package dev.testia.mono3.core.service

import dev.testia.mono3.core.api.FieldDescriptor
import dev.testia.mono3.core.api.FieldDescriptor.Companion.CORE
import dev.testia.mono3.core.api.FieldOption
import dev.testia.mono3.core.api.FieldType
import dev.testia.mono3.core.api.ProductDto
import dev.testia.mono3.core.api.ProductNotFoundException
import dev.testia.mono3.core.api.ProductOperations
import dev.testia.mono3.core.api.ProductValidationException
import dev.testia.mono3.core.api.Quote
import dev.testia.mono3.core.api.QuoteLine
import dev.testia.mono3.core.api.Violation
import dev.testia.mono3.core.persistence.ProductEntity
import dev.testia.mono3.core.spi.FieldPolicy
import dev.testia.mono3.core.spi.LabelFormatter
import dev.testia.mono3.core.spi.ProductExtension
import dev.testia.mono3.core.spi.ProductValidator
import dev.testia.mono3.core.spi.QuoteStep
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
    policies: Instance<FieldPolicy>,
    private val labelFormatter: LabelFormatter,
) : ProductOperations {
    private val extensions: Map<String, ProductExtension> = extensions.associateBy { it.namespace }.toSortedMap()
    private val validators: List<ProductValidator> = validators.toList()
    private val quoteSteps: List<QuoteStep> = quoteSteps.sortedBy { it.order }
    private val policies: List<FieldPolicy> = policies.sortedBy { it.order }

    // --- schéma ------------------------------------------------------------------------------

    override fun schema(): List<FieldDescriptor> = effectiveSchema(null)

    override fun schemaFor(draft: ProductDto): List<FieldDescriptor> = effectiveSchema(coerce(draft).first)

    private fun baseSchema(): List<FieldDescriptor> = CORE_FIELDS + extensions.values.flatMap { it.fields() }

    private fun effectiveSchema(draft: ProductDto?): List<FieldDescriptor> = baseSchema().map { base ->
        policies.fold(base) { field, policy ->
            policy.adjust(field, draft).also {
                // Une politique peut changer les valeurs valides, pas l'identité ni le type du champ.
                check(it.namespace == base.namespace && it.name == base.name && it.type == base.type) {
                    "${policy::class.simpleName} changed identity or type of ${base.key}"
                }
            }
        }
    }

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

    // --- traitements -------------------------------------------------------------------------

    override fun validate(product: ProductDto): List<Violation> = normalize(product).second

    @Transactional
    override fun quote(id: Long, quantity: Int): Quote {
        if (quantity <= 0) throw ProductValidationException(listOf(Violation("quantity", "La quantité doit être positive")))
        val product = get(id)
        val lines = quoteSteps.fold(emptyList<QuoteLine>()) { acc, step -> step.apply(product, quantity, acc) }
        return Quote(lines, lines.fold(BigDecimal.ZERO) { sum, l -> sum + l.amount }.setScale(2, RoundingMode.HALF_UP))
    }

    @Transactional
    override fun label(id: Long): String = labelFormatter.label(get(id))

    // --- validation --------------------------------------------------------------------------

    private fun find(id: Long): ProductEntity = em.find(ProductEntity::class.java, id) ?: throw ProductNotFoundException(id)

    private fun checked(product: ProductDto): ProductDto {
        val (normalized, violations) = normalize(product)
        if (violations.isNotEmpty()) throw ProductValidationException(violations)
        return normalized
    }

    private fun saveExtensions(id: Long, product: ProductDto) =
        extensions.forEach { (ns, ext) -> ext.save(id, product.extensions[ns].orEmpty()) }

    /**
     * Mise en forme : namespaces/champs connus et types. Le résultat contient tous les champs
     * de tous les plug-ins présents (null si absent).
     */
    private fun coerce(product: ProductDto): Pair<ProductDto, List<Violation>> {
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
        return normalized to violations
    }

    /** Mise en forme, puis contraintes du schéma effectif *pour ce produit*, puis règles métier. */
    private fun normalize(product: ProductDto): Pair<ProductDto, List<Violation>> {
        val (normalized, violations) = coerce(product).let { it.first to it.second.toMutableList() }
        if (violations.isNotEmpty()) return normalized to violations
        effectiveSchema(normalized).forEach { f -> violations += checkConstraints(f, valueOf(normalized, f)) }
        if (violations.isEmpty()) violations += validators.flatMap { it.validate(normalized) }
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
                "description" -> p.description
                "price" -> p.price
                "category" -> p.category
                else -> null
            }
        }

    private fun ProductEntity.copyFrom(p: ProductDto) {
        name = p.name
        description = p.description
        price = p.price
        category = p.category!!
    }

    private fun ProductEntity.toDto(extValues: Map<String, Map<Long, Map<String, Any?>>>) = ProductDto(
        id = id,
        name = name,
        description = description,
        price = price.setScale(2, RoundingMode.HALF_UP),
        category = category,
        extensions = extensions.mapValues { (ns, ext) ->
            val stored = extValues[ns]?.get(id).orEmpty()
            ext.fields().associate { it.name to stored[it.name] }
        },
    )

    companion object {
        val CORE_FIELDS = listOf(
            FieldDescriptor(CORE, "name", "Nom", FieldType.TEXT, required = true),
            FieldDescriptor(CORE, "description", "Description", FieldType.TEXT),
            FieldDescriptor(CORE, "price", "Prix unitaire", FieldType.NUMBER, required = true, min = 0.0, max = 100_000.0),
            FieldDescriptor(
                CORE, "category", "Catégorie", FieldType.CHOICE, required = true,
                options = listOf(
                    FieldOption("office", "Bureautique"),
                    FieldOption("tools", "Outillage"),
                    FieldOption("garden", "Jardin"),
                ),
            ),
        )
    }
}
