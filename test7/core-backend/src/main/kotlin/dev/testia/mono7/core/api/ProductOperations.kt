package dev.testia.mono7.core.api

import java.math.BigDecimal

/**
 * Contrat public de core-backend, identique en appel direct ([dev.testia.mono7.core.service.ProductService])
 * et en REST ([dev.testia.mono7.core.rest.ProductApi], qui étend cette interface).
 */
interface ProductOperations {
    /** Schéma effectif (champs + contraintes), plug-ins compris, sans contexte. */
    fun schema(): List<FieldDescriptor>

    /** Schéma effectif pour un brouillon : certaines contraintes dépendent des autres valeurs. */
    fun schemaFor(draft: ProductDto): List<FieldDescriptor>

    fun list(): List<ProductDto>

    /** @throws ProductNotFoundException */
    fun get(id: Long): ProductDto

    /** @throws ProductValidationException */
    fun create(product: ProductDto): ProductDto

    /** Remplacement complet. @throws ProductNotFoundException, ProductValidationException */
    fun update(id: Long, product: ProductDto): ProductDto

    /** @throws ProductNotFoundException */
    fun delete(id: Long)

    /** Traitement : validation (contraintes du schéma effectif + règles métier), sans écriture. */
    fun validate(product: ProductDto): List<Violation>

    /** Traitement : devis, construit par un pipeline d'étapes que les plug-ins peuvent enrichir. */
    fun quote(id: Long, quantity: Int): Quote

    /** Traitement : libellé, implémentation par défaut remplaçable par un plug-in. */
    fun label(id: Long): String
}

/** Champs du noyau typés ; ceux des plug-ins rangés par namespace dans [extensions]. */
data class ProductDto(
    val id: Long? = null,
    val name: String = "",
    val description: String? = null,
    val price: BigDecimal = BigDecimal.ZERO,
    val category: String? = null,
    val extensions: Map<String, Map<String, Any?>> = emptyMap(),
)

enum class FieldType {
    TEXT, NUMBER, BOOLEAN, CHOICE;

    /** Normalise une valeur (JSON ou Kotlin) vers le type Kotlin canonique, ou lève IllegalArgumentException. */
    fun coerce(value: Any?): Any? = when {
        value == null -> null
        (this == TEXT || this == CHOICE) && value is String -> value
        this == NUMBER && value is Number -> value.toDouble()
        this == BOOLEAN && value is Boolean -> value
        else -> throw IllegalArgumentException("$value is not $this")
    }
}

data class FieldOption(val value: String, val label: String)

/**
 * Description d'un champ et de ses valeurs valides. Ces contraintes sont la seule source de vérité :
 * le backend valide avec, l'IHM s'affiche avec (liste déroulante, min/max, obligatoire).
 * Un plug-in peut les modifier, y compris sur les champs du noyau, via une
 * [dev.testia.mono7.core.spi.FieldPolicy].
 */
data class FieldDescriptor(
    val namespace: String,
    val name: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    /** Valeurs autorisées (type CHOICE). */
    val options: List<FieldOption>? = null,
    /** Bornes incluses (type NUMBER). */
    val min: Double? = null,
    val max: Double? = null,
) {
    /** Clé utilisée dans les violations et par l'IHM : "name" pour le noyau, "<namespace>.<champ>" sinon. */
    val key: String get() = if (namespace == CORE) name else "$namespace.$name"

    companion object {
        const val CORE = "core"
    }
}

data class Violation(val field: String, val message: String)

data class ValidationErrors(val violations: List<Violation> = emptyList())

data class QuoteLine(val code: String, val label: String, val amount: BigDecimal)

data class Quote(val lines: List<QuoteLine> = emptyList(), val total: BigDecimal = BigDecimal.ZERO)

class ProductNotFoundException(id: Long?) : RuntimeException("Product not found: ${id ?: "?"}")

class ProductValidationException(val violations: List<Violation>) :
    RuntimeException("Invalid product: $violations")
