package dev.testia.mono2.core.api

import java.math.BigDecimal

/**
 * Contrat public de core-backend, identique en appel direct ([dev.testia.mono2.core.service.ProductService])
 * et en REST ([ProductApi], qui étend cette interface : le client REST *est* un ProductOperations).
 */
interface ProductOperations {
    /** Description de tous les champs : noyau (namespace "core") + ceux apportés par les plug-ins. */
    fun schema(): List<FieldDescriptor>

    fun list(): List<ProductDto>

    /** @throws ProductNotFoundException */
    fun get(id: Long): ProductDto

    /** @throws ProductValidationException */
    fun create(product: ProductDto): ProductDto

    /** Remplacement complet. @throws ProductNotFoundException, ProductValidationException */
    fun update(id: Long, product: ProductDto): ProductDto

    /** @throws ProductNotFoundException */
    fun delete(id: Long)

    /** Traitement : validation (noyau + règles des plug-ins), sans écriture. */
    fun validate(product: ProductDto): List<Violation>

    /** Traitement : devis, construit par un pipeline d'étapes que les plug-ins peuvent enrichir. */
    fun quote(id: Long, quantity: Int): Quote

    /** Traitement : libellé, implémentation par défaut remplaçable par un plug-in. */
    fun label(id: Long): String
}

/**
 * Objet métier. Les champs du noyau sont typés ; ceux des plug-ins sont rangés par namespace
 * dans [extensions] et décrits par [ProductOperations.schema] (le client n'a pas à les connaître).
 */
data class ProductDto(
    val id: Long? = null,
    val name: String = "",
    val description: String? = null,
    val price: BigDecimal = BigDecimal.ZERO,
    val extensions: Map<String, Map<String, Any?>> = emptyMap(),
)

enum class FieldType {
    TEXT, NUMBER, BOOLEAN;

    /** Normalise une valeur (JSON ou Kotlin) vers le type Kotlin canonique, ou lève IllegalArgumentException. */
    fun coerce(value: Any?): Any? = when {
        value == null -> null
        this == TEXT && value is String -> value
        this == NUMBER && value is Number -> value.toDouble()
        this == BOOLEAN && value is Boolean -> value
        else -> throw IllegalArgumentException("$value is not $this")
    }
}

data class FieldDescriptor(
    val namespace: String,
    val name: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
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
