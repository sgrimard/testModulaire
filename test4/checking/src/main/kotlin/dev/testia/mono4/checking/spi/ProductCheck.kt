package dev.testia.mono4.checking.spi

import dev.testia.mono4.checking.api.Finding
import dev.testia.mono4.checking.api.Metric
import dev.testia.mono4.core.api.ProductDto

/**
 * Point d'extension de checking : une contribution au calcul. checking les découvre via
 * Instance<ProductCheck> et ne connaît aucun module ext-checking-*.
 *
 * Chaque contribution reçoit le produit tel que core-backend le renvoie, champs des extensions
 * compris ; elle lit les données d'une extension par l'API typée de ce module (dépendance Gradle
 * explicite), ce qui garantit que l'extension de données est présente dès que la contribution l'est.
 */
interface ProductCheck {
    /** Nom de la contribution ; préfixe de ses métriques et de ses règles. */
    val name: String

    /** Ordre d'affichage dans le rapport (le noyau en premier). */
    val order: Int get() = 100

    fun evaluate(product: ProductDto): CheckResult
}

data class CheckResult(val metrics: List<Metric> = emptyList(), val findings: List<Finding> = emptyList())
