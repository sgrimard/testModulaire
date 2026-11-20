package dev.testia.mono2.core.spi

import dev.testia.mono2.core.api.FieldDescriptor
import dev.testia.mono2.core.api.ProductDto
import dev.testia.mono2.core.api.QuoteLine
import dev.testia.mono2.core.api.Violation

/*
 * Points d'extension de core-backend. Un plug-in les implémente avec des beans CDI ;
 * core-backend les découvre via Instance<…> et ne connaît aucun plug-in.
 */

/** Champs supplémentaires : description + persistance (dans la transaction du noyau). */
interface ProductExtension {
    val namespace: String
    fun fields(): List<FieldDescriptor>
    fun load(productIds: Collection<Long>): Map<Long, Map<String, Any?>>
    fun save(productId: Long, values: Map<String, Any?>)
    fun delete(productId: Long)
}

/** Spécialisation additive : toutes les règles s'appliquent. */
interface ProductValidator {
    fun validate(product: ProductDto): List<Violation>
}

/** Spécialisation en pipeline : chaque étape reçoit les lignes précédentes et peut ajouter/modifier/retirer. */
interface QuoteStep {
    val order: Int
    fun apply(product: ProductDto, quantity: Int, lines: List<QuoteLine>): List<QuoteLine>
}

/** Spécialisation par remplacement : le noyau fournit un bean @DefaultBean, un plug-in peut le supplanter. */
interface LabelFormatter {
    fun label(product: ProductDto): String
}

/** Chaque module possède ses scripts SQL ; ils sont appliqués par ordre de module puis de script. */
interface SchemaContribution {
    val module: String
    val order: Int
    val scripts: List<String>
}
