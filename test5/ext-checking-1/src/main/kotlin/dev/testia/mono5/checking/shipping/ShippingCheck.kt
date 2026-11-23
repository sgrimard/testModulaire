package dev.testia.mono5.checking.shipping

import dev.testia.mono5.checking.api.Finding
import dev.testia.mono5.checking.api.Metric
import dev.testia.mono5.checking.api.Severity
import dev.testia.mono5.checking.spi.CheckResult
import dev.testia.mono5.checking.spi.ProductCheck
import dev.testia.mono5.core.api.ProductDto
import dev.testia.mono5.shipping.shipping
import jakarta.enterprise.context.ApplicationScoped
import java.math.BigDecimal
import java.math.RoundingMode

/** Complète le calcul avec les données d'ext-backend-1 (transport). */
@ApplicationScoped
class ShippingCheck : ProductCheck {
    override val name = "shipping"

    override fun evaluate(product: ProductDto): CheckResult {
        val shipping = product.shipping()
        val findings = mutableListOf<Finding>()
        if (shipping.hazardous && product.category !in HAZARDOUS_CATEGORIES) {
            findings += Finding(
                "shipping.hazardous-category", Severity.BLOCKING,
                "Marchandise dangereuse dans la catégorie « ${product.category} »", 40,
            )
        }
        val cost = shipping.costPerUnit()
            ?: return CheckResult(findings = findings + Finding("shipping.unknown-weight", Severity.WARNING, "Poids inconnu : transport non calculable", 10))
        val share = percent(cost, product.price)
        if (share != null && share > MAX_SHARE) {
            findings += Finding("shipping.expensive", Severity.WARNING, "Le transport représente $share % du prix", 15)
        }
        return CheckResult(
            metrics = listOfNotNull(
                Metric("shipping.costPerUnit", "Transport par unité", cost, "€"),
                share?.let { Metric("shipping.shareOfPrice", "Part du transport dans le prix", it, "%") },
            ),
            findings = findings,
        )
    }

    private companion object {
        val HAZARDOUS_CATEGORIES = setOf("tools", "chemicals")
        val MAX_SHARE = BigDecimal(30)

        fun percent(part: BigDecimal, whole: BigDecimal): BigDecimal? =
            if (whole.signum() == 0) null else (part * BigDecimal(100)).divide(whole, 1, RoundingMode.HALF_UP)
    }
}
