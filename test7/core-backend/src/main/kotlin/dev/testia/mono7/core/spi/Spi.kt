package dev.testia.mono7.core.spi

import dev.testia.mono7.core.api.FieldDescriptor
import dev.testia.mono7.core.api.ProductDto
import dev.testia.mono7.core.api.QuoteLine
import dev.testia.mono7.core.api.Violation

/*
 * Points d'extension de core-backend. Un plug-in les implémente avec des beans Spring
 * (@Component) ; core-backend les découvre via ObjectProvider<…> et ne connaît aucun plug-in.
 */

/** Champs supplémentaires : description + persistance (dans la transaction du noyau). */
interface ProductExtension {
    val namespace: String
    fun fields(): List<FieldDescriptor>
    fun load(productIds: Collection<Long>): Map<Long, Map<String, Any?>>
    fun save(productId: Long, values: Map<String, Any?>)
    fun delete(productId: Long)
}

/**
 * Modifie les valeurs valides d'un champ (du noyau ou d'un plug-in) : options d'une liste, bornes,
 * caractère obligatoire. Appliquée dans l'ordre de [order] ; chaque politique reçoit le résultat
 * de la précédente. [draft] est le produit en cours de saisie (null pour le schéma sans contexte),
 * ce qui permet des contraintes qui dépendent d'autres champs.
 */
interface FieldPolicy {
    val order: Int get() = 0
    fun adjust(field: FieldDescriptor, draft: ProductDto?): FieldDescriptor
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

/**
 * Spécialisation par remplacement : le noyau fournit une implémentation par défaut ; un plug-in la
 * supplante en déclarant la sienne `@Primary`. Deux plug-ins `@Primary` font échouer le démarrage.
 */
interface LabelFormatter {
    fun label(product: ProductDto): String
}

/**
 * Libellé par défaut du noyau, réutilisable par un plug-in qui remplace [LabelFormatter].
 * Il est dans le SPI (publié) et non dans le service (interne au noyau).
 */
fun coreLabel(product: ProductDto): String = "${product.name} (${product.price} €)"

/** Chaque module possède ses scripts SQL ; ils sont appliqués par ordre de module puis de script. */
interface SchemaContribution {
    val module: String
    val order: Int
    val scripts: List<String>
}
