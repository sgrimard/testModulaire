package dev.testia.mono5.core.service

import dev.testia.mono5.core.api.FieldDescriptor
import dev.testia.mono5.core.api.FieldDescriptor.Companion.CORE
import dev.testia.mono5.core.api.FieldOption
import dev.testia.mono5.core.api.FieldType
import dev.testia.mono5.core.api.ProductDto
import dev.testia.mono5.core.api.ProductEvent
import dev.testia.mono5.core.api.ProductNotFoundException
import dev.testia.mono5.core.api.ProductOperations
import dev.testia.mono5.core.api.ProductValidationException
import dev.testia.mono5.core.api.UnknownExtensionException
import dev.testia.mono5.core.api.Violation
import dev.testia.mono5.core.persistence.ProductEntity
import dev.testia.mono5.core.spi.ProductExtension
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Event
import jakarta.enterprise.inject.Instance
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/** Implémentation « appel direct » du contrat ; c'est aussi elle que le REST délègue. */
@ApplicationScoped
class ProductService(
    private val em: EntityManager,
    extensions: Instance<ProductExtension>,
    private val changes: Event<ProductEvent>,
    private val bus: ProductEvents,
) : ProductOperations {
    private val extensions: Map<String, ProductExtension> = extensions.associateBy { it.namespace }.toSortedMap()

    // --- schéma et découverte ----------------------------------------------------------------

    override fun schema(): List<FieldDescriptor> = CORE_FIELDS + extensions.values.flatMap { it.fields() }

    override fun extensions(): List<String> = extensions.keys.toList()

    // --- CRUD --------------------------------------------------------------------------------

    @Transactional
    override fun list(): List<ProductDto> {
        val entities = em.createQuery("from ProductEntity p order by p.id", ProductEntity::class.java).resultList
        val ids = entities.map { it.id!! }
        val extValues = extensions.mapValues { (_, ext) -> if (ids.isEmpty()) emptyMap() else ext.load(ids) }
        return entities.map { it.toDto(extValues) }
    }

    @Transactional
    override fun get(id: Long): ProductDto = find(id).toDto(extensions.mapValues { (_, ext) -> ext.load(listOf(id)) })

    @Transactional
    override fun create(product: ProductDto): ProductDto {
        val normalized = checked(product)
        val entity = ProductEntity().apply { copyFrom(normalized) }
        em.persist(entity)
        em.flush() // la ligne du noyau doit exister avant celles des extensions (clé étrangère)
        saveExtensions(entity.id!!, normalized)
        changes.fire(ProductEvent(ProductEvent.CREATED, entity.id))
        return get(entity.id!!)
    }

    @Transactional
    override fun update(id: Long, product: ProductDto): ProductDto {
        val entity = find(id)
        val normalized = checked(product)
        entity.copyFrom(normalized)
        saveExtensions(id, normalized)
        changes.fire(ProductEvent(ProductEvent.UPDATED, id))
        return get(id)
    }

    @Transactional
    override fun delete(id: Long) {
        val entity = find(id)
        extensions.values.forEach { it.delete(id) }
        em.remove(entity)
        changes.fire(ProductEvent(ProductEvent.DELETED, id))
    }

    // --- accès « par nom » à une extension (test1) ---------------------------------------------

    @Transactional
    override fun getExtension(id: Long, name: String): Map<String, Any?> {
        val ext = extension(name)
        find(id)
        return withAllFields(ext, ext.load(listOf(id))[id].orEmpty())
    }

    /** Valide le produit complet tel qu'il serait après le put : les contraintes restent celles du schéma. */
    @Transactional
    override fun putExtension(id: Long, name: String, values: Map<String, Any?>): Map<String, Any?> {
        val ext = extension(name)
        val current = get(id)
        val normalized = checked(current.copy(extensions = current.extensions + (name to values)))
        ext.save(id, normalized.extensions.getValue(name))
        changes.fire(ProductEvent(ProductEvent.UPDATED, id, extension = name))
        return getExtension(id, name)
    }

    override fun events(): Multi<ProductEvent> = bus.stream()

    // --- validation --------------------------------------------------------------------------

    private fun extension(name: String) = extensions[name] ?: throw UnknownExtensionException(name)

    private fun find(id: Long): ProductEntity = em.find(ProductEntity::class.java, id) ?: throw ProductNotFoundException(id)

    private fun checked(product: ProductDto): ProductDto {
        val (normalized, violations) = normalize(product)
        if (violations.isNotEmpty()) throw ProductValidationException(violations)
        return normalized
    }

    private fun saveExtensions(id: Long, product: ProductDto) =
        extensions.forEach { (ns, ext) -> ext.save(id, product.extensions[ns].orEmpty()) }

    /** Forme (extensions/champs connus, types), puis contraintes du schéma. */
    private fun normalize(product: ProductDto): Pair<ProductDto, List<Violation>> {
        val violations = mutableListOf<Violation>()
        product.extensions.keys.filterNot { it in extensions }.forEach { violations += Violation(it, "Extension inconnue") }
        val ext = extensions.mapValues { (ns, extension) ->
            val fields = extension.fields().associateBy { it.name }
            val given = product.extensions[ns].orEmpty()
            given.keys.filterNot { it in fields }.forEach { violations += Violation("$ns.$it", "Champ inconnu") }
            fields.mapValues { (_, f) ->
                try {
                    f.type.coerce(given[f.name])
                } catch (_: IllegalArgumentException) {
                    violations += Violation(f.key, "Type attendu : ${f.type}")
                    null
                }
            }
        }
        val normalized = product.copy(
            name = product.name.trim(),
            price = product.price.setScale(2, RoundingMode.HALF_UP),
            extensions = ext,
        )
        if (violations.isEmpty()) schema().forEach { f -> violations += checkConstraints(f, valueOf(normalized, f)) }
        return normalized to violations
    }

    private fun checkConstraints(f: FieldDescriptor, value: Any?): List<Violation> = buildList {
        if (value == null || value == "") {
            if (f.required) add(Violation(f.key, "Champ obligatoire"))
            return@buildList
        }
        f.options?.let { options ->
            if (options.none { it.value == value }) add(Violation(f.key, "Valeur non autorisée : $value"))
        }
        if (value is Number) {
            val v = value.toDouble()
            f.min?.let { if (v < it) add(Violation(f.key, "Doit être ≥ ${fmt(it)}")) }
            f.max?.let { if (v > it) add(Violation(f.key, "Doit être ≤ ${fmt(it)}")) }
        }
    }

    private fun fmt(d: Double) = BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()

    private fun valueOf(p: ProductDto, f: FieldDescriptor): Any? =
        if (f.namespace != CORE) {
            p.extensions[f.namespace]?.get(f.name)
        } else {
            when (f.name) {
                "name" -> p.name
                "price" -> p.price
                "category" -> p.category
                "stock" -> p.stock
                else -> null
            }
        }

    private fun withAllFields(ext: ProductExtension, stored: Map<String, Any?>) =
        ext.fields().associate { it.name to stored[it.name] }

    private fun ProductEntity.copyFrom(p: ProductDto) {
        name = p.name
        price = p.price
        category = p.category!!
        stock = p.stock
    }

    private fun ProductEntity.toDto(extValues: Map<String, Map<Long, Map<String, Any?>>>) = ProductDto(
        id = id,
        name = name,
        price = price.setScale(2, RoundingMode.HALF_UP),
        category = category,
        stock = stock,
        extensions = extensions.mapValues { (ns, ext) -> withAllFields(ext, extValues[ns]?.get(id).orEmpty()) },
    )

    companion object {
        val CORE_FIELDS = listOf(
            FieldDescriptor(CORE, "name", "Nom", FieldType.TEXT, required = true),
            FieldDescriptor(CORE, "price", "Prix de vente", FieldType.NUMBER, required = true, min = 0.0, max = 100_000.0),
            FieldDescriptor(
                CORE, "category", "Catégorie", FieldType.CHOICE, required = true,
                options = listOf(
                    FieldOption("office", "Bureautique"),
                    FieldOption("tools", "Outillage"),
                    FieldOption("garden", "Jardin"),
                    FieldOption("chemicals", "Produits chimiques"),
                ),
            ),
            FieldDescriptor(CORE, "stock", "Stock", FieldType.NUMBER, required = true, min = 0.0),
        )
    }
}
