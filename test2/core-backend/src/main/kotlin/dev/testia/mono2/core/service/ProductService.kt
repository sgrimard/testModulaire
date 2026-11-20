package dev.testia.mono2.core.service

import dev.testia.mono2.core.api.FieldDescriptor
import dev.testia.mono2.core.api.FieldDescriptor.Companion.CORE
import dev.testia.mono2.core.api.FieldType
import dev.testia.mono2.core.api.ProductDto
import dev.testia.mono2.core.api.ProductNotFoundException
import dev.testia.mono2.core.api.ProductOperations
import dev.testia.mono2.core.api.ProductValidationException
import dev.testia.mono2.core.api.Quote
import dev.testia.mono2.core.api.QuoteLine
import dev.testia.mono2.core.api.Violation
import dev.testia.mono2.core.persistence.ProductEntity
import dev.testia.mono2.core.spi.LabelFormatter
import dev.testia.mono2.core.spi.ProductExtension
import dev.testia.mono2.core.spi.ProductValidator
import dev.testia.mono2.core.spi.QuoteStep
import jakarta.enterprise.context.ApplicationScoped
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
    validators: Instance<ProductValidator>,
    quoteSteps: Instance<QuoteStep>,
    private val labelFormatter: LabelFormatter,
) : ProductOperations {
    private val extensions: Map<String, ProductExtension> = extensions.associateBy { it.namespace }.toSortedMap()
    private val validators: List<ProductValidator> = validators.toList()
    private val quoteSteps: List<QuoteStep> = quoteSteps.sortedBy { it.order }

    override fun schema(): List<FieldDescriptor> = CORE_FIELDS + extensions.values.flatMap { it.fields() }

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
        em.flush() // la ligne du noyau doit exister avant celles des plug-ins (clé étrangère)
        saveExtensions(entity.id!!, normalized)
        return get(entity.id!!)
    }

    @Transactional
    override fun update(id: Long, product: ProductDto): ProductDto {
        val entity = find(id)
        val normalized = checked(product)
        entity.copyFrom(normalized)
        saveExtensions(id, normalized)
        return get(id)
    }

    @Transactional
    override fun delete(id: Long) {
        val entity = find(id)
        extensions.values.forEach { it.delete(id) }
        em.remove(entity)
    }

    override fun validate(product: ProductDto): List<Violation> = normalize(product).second

    @Transactional
    override fun quote(id: Long, quantity: Int): Quote {
        if (quantity <= 0) throw ProductValidationException(listOf(Violation("quantity", "La quantité doit être positive")))
        val product = get(id)
        val lines = quoteSteps.fold(emptyList<QuoteLine>()) { acc, step ->
            step.apply(product, quantity, acc)
        }
        return Quote(lines, lines.fold(BigDecimal.ZERO) { sum, l -> sum + l.amount }.setScale(2, RoundingMode.HALF_UP))
    }

    @Transactional
    override fun label(id: Long): String = labelFormatter.label(get(id))

    // --- interne -----------------------------------------------------------------------------

    private fun find(id: Long): ProductEntity = em.find(ProductEntity::class.java, id) ?: throw ProductNotFoundException(id)

    private fun checked(product: ProductDto): ProductDto {
        val (normalized, violations) = normalize(product)
        if (violations.isNotEmpty()) throw ProductValidationException(violations)
        return normalized
    }

    private fun saveExtensions(id: Long, product: ProductDto) =
        extensions.forEach { (ns, ext) -> ext.save(id, product.extensions[ns].orEmpty()) }

    /**
     * Contrôle générique piloté par les descripteurs (namespace et champ connus, type, obligatoire),
     * puis, si tout est bien formé, règles métier du noyau et des plug-ins.
     * Le résultat contient *tous* les champs de *tous* les plug-ins présents (null si absent).
     */
    private fun normalize(product: ProductDto): Pair<ProductDto, List<Violation>> {
        val violations = mutableListOf<Violation>()
        product.extensions.keys.filterNot { it in extensions }.forEach { violations += Violation(it, "Extension inconnue") }

        val normalizedExt = extensions.mapValues { (ns, ext) ->
            val fields = ext.fields().associateBy { it.name }
            val given = product.extensions[ns].orEmpty()
            given.keys.filterNot { it in fields }.forEach { violations += Violation("$ns.$it", "Champ inconnu") }
            fields.mapValues { (_, f) ->
                val value = try {
                    f.type.coerce(given[f.name])
                } catch (_: IllegalArgumentException) {
                    violations += Violation(f.key, "Type attendu : ${f.type}")
                    null
                }
                if (f.required && value == null && violations.none { it.field == f.key }) {
                    violations += Violation(f.key, "Champ obligatoire")
                }
                value
            }
        }
        val normalized = product.copy(
            name = product.name.trim(),
            price = product.price.setScale(2, RoundingMode.HALF_UP),
            extensions = normalizedExt,
        )
        if (violations.isEmpty()) violations += validators.flatMap { it.validate(normalized) }
        return normalized to violations
    }

    private fun ProductEntity.copyFrom(p: ProductDto) {
        name = p.name
        description = p.description
        price = p.price
    }

    private fun ProductEntity.toDto(extValues: Map<String, Map<Long, Map<String, Any?>>>) = ProductDto(
        id = id,
        name = name,
        description = description,
        price = price.setScale(2, RoundingMode.HALF_UP),
        extensions = extensions.mapValues { (ns, ext) ->
            val stored = extValues[ns]?.get(id).orEmpty()
            ext.fields().associate { it.name to stored[it.name] }
        },
    )

    companion object {
        val CORE_FIELDS = listOf(
            FieldDescriptor(CORE, "name", "Nom", FieldType.TEXT, required = true),
            FieldDescriptor(CORE, "description", "Description", FieldType.TEXT),
            FieldDescriptor(CORE, "price", "Prix unitaire", FieldType.NUMBER, required = true),
        )
    }
}
