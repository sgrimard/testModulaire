package dev.testia.mono6.core.api

import reactor.core.publisher.Flux
import java.math.BigDecimal

/**
 * Contrat public de core-backend, identique en appel direct ([dev.testia.mono6.core.service.ProductService])
 * et en REST ([dev.testia.mono6.core.rest.ProductApi], qui étend cette interface).
 *
 * Il réunit les deux approches précédentes :
 *  - test2 : l'objet du noyau, persistant, porte aussi les champs des extensions (rangés par namespace) ;
 *  - test1 : les extensions se découvrent par leur nom, s'utilisent par get / put génériques, et l'on
 *    peut s'abonner aux changements (voir [extension] pour la vue « par nom »).
 */
interface ProductOperations {
    /** Champs du noyau et de toutes les extensions présentes, avec leurs contraintes. */
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

    /** Noms des extensions présentes dans l'assemblage (découverte, sans les connaître à l'avance). */
    fun extensions(): List<String>

    /** Valeurs d'une extension pour un produit. @throws UnknownExtensionException, ProductNotFoundException */
    fun getExtension(id: Long, name: String): Map<String, Any?>

    /**
     * Remplace les valeurs d'une extension pour un produit, sans toucher au reste.
     * @throws UnknownExtensionException, ProductNotFoundException, ProductValidationException
     */
    fun putExtension(id: Long, name: String, values: Map<String, Any?>): Map<String, Any?>

    /** Flux chaud des changements validés ; le premier élément est toujours [ProductEvent.SUBSCRIBED]. */
    fun events(): Flux<ProductEvent>
}

/** Vue « par nom » d'une extension (comme l'ExtensionHandle du test1), valable en direct comme en REST. */
class ExtensionHandle internal constructor(private val ops: ProductOperations, val name: String) {
    fun fields(): List<FieldDescriptor> = ops.schema().filter { it.namespace == name }
    fun get(productId: Long): Map<String, Any?> = ops.getExtension(productId, name)
    fun put(productId: Long, values: Map<String, Any?>): Map<String, Any?> = ops.putExtension(productId, name, values)

    /** Changements qui touchent cette extension (le premier élément reste [ProductEvent.SUBSCRIBED]). */
    fun events(): Flux<ProductEvent> =
        ops.events().filter { it.type == ProductEvent.SUBSCRIBED || it.extension == name }
}

/** @throws UnknownExtensionException si aucune extension ne porte ce nom. */
fun ProductOperations.extension(name: String): ExtensionHandle {
    if (name !in extensions()) throw UnknownExtensionException(name)
    return ExtensionHandle(this, name)
}

/** Champs du noyau typés ; ceux des extensions rangés par namespace dans [extensions]. */
data class ProductDto(
    val id: Long? = null,
    val name: String = "",
    val price: BigDecimal = BigDecimal.ZERO,
    val category: String? = null,
    val stock: Int = 0,
    val extensions: Map<String, Map<String, Any?>> = emptyMap(),
)

/**
 * Changement validé (émis après commit). C'est aussi l'événement Spring publié par core-backend :
 * les autres modules s'y abonnent par `@TransactionalEventListener` (voir checking). [extension] est renseigné quand seul un put sur cette
 * extension a eu lieu ; null pour une création / modification / suppression complète.
 */
data class ProductEvent(
    val type: String,
    val productId: Long? = null,
    val extension: String? = null,
) {
    companion object {
        const val SUBSCRIBED = "subscribed"
        const val CREATED = "created"
        const val UPDATED = "updated"
        const val DELETED = "deleted"
    }
}

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

/** Description d'un champ et de ses valeurs valides (seule source de vérité pour la validation). */
data class FieldDescriptor(
    val namespace: String,
    val name: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    val options: List<FieldOption>? = null,
    val min: Double? = null,
    val max: Double? = null,
) {
    /** "name" pour le noyau, "<namespace>.<champ>" pour une extension. */
    val key: String get() = if (namespace == CORE) name else "$namespace.$name"

    companion object {
        const val CORE = "core"
    }
}

data class Violation(val field: String, val message: String)

/** Corps des réponses d'erreur REST, retraduit en exception du contrat côté client. */
data class ApiError(
    val code: String,
    val message: String,
    /** Identifiant du produit ou nom de l'extension concernés. */
    val subject: String? = null,
    val violations: List<Violation> = emptyList(),
) {
    companion object {
        const val NOT_FOUND = "product-not-found"
        const val UNKNOWN_EXTENSION = "unknown-extension"
        const val INVALID = "invalid"
    }
}

class ProductNotFoundException(val id: Long?) : NoSuchElementException("Product not found: ${id ?: "?"}")

class UnknownExtensionException(val extension: String) : NoSuchElementException("Unknown extension: $extension")

class ProductValidationException(val violations: List<Violation>) : RuntimeException("Invalid product: $violations")
