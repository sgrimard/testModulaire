package dev.testia.mono6.core.spi

import dev.testia.mono6.core.api.FieldDescriptor

/*
 * Points d'extension de core-backend. Une extension les implémente avec des beans Spring
 * (@Component) ; core-backend les découvre via ObjectProvider<…> et ne connaît aucune extension.
 */

/** Champs supplémentaires : description + persistance (dans la transaction du noyau). */
interface ProductExtension {
    /** Nom de l'extension, utilisé pour la découverte et comme namespace de ses champs. */
    val namespace: String
    fun fields(): List<FieldDescriptor>
    fun load(productIds: Collection<Long>): Map<Long, Map<String, Any?>>
    fun save(productId: Long, values: Map<String, Any?>)
    fun delete(productId: Long)
}

/** Chaque module possède ses scripts SQL ; ils sont appliqués par ordre de module puis de script. */
interface SchemaContribution {
    val module: String
    val order: Int
    val scripts: List<String>
}
