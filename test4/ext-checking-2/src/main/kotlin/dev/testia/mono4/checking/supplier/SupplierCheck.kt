package dev.testia.mono4.checking.supplier

import dev.testia.mono4.checking.api.Finding
import dev.testia.mono4.checking.api.Metric
import dev.testia.mono4.checking.api.Severity
import dev.testia.mono4.checking.spi.CheckResult
import dev.testia.mono4.checking.spi.ProductCheck
import dev.testia.mono4.core.api.ProductDto
import dev.testia.mono4.supplier.supplier
import jakarta.enterprise.context.ApplicationScoped
import java.math.BigDecimal
import java.math.RoundingMode

/** Complète le calcul avec les données d'ext-backend-2 (fournisseur). */
@ApplicationScoped
class SupplierCheck : ProductCheck {
    override val name = "supplier"

    override fun evaluate(product: ProductDto): CheckResult {
        val supplier = product.supplier()
        val findings = mutableListOf<Finding>()
        val lead = supplier.leadTimeDays
        if (lead != null && lead > LONG_LEAD_TIME && product.stock < LOW_STOCK) {
            findings += Finding("supplier.restock-risk", Severity.WARNING, "Stock faible et réapprovisionnement en $lead jours", 10)
        }
        val cost = supplier.unitCost
            ?: return CheckResult(findings = findings + Finding("supplier.unknown-cost", Severity.WARNING, "Coût d'achat inconnu : marge non calculable", 10))
        val margin = product.price - cost
        val rate = if (product.price.signum() == 0) null else (margin * BigDecimal(100)).divide(product.price, 1, RoundingMode.HALF_UP)
        when {
            margin.signum() < 0 -> findings += Finding("supplier.sold-at-loss", Severity.BLOCKING, "Vendu à perte (${margin} € par unité)", 50)
            rate != null && rate < MIN_RATE -> findings += Finding("supplier.low-margin", Severity.WARNING, "Marge brute faible ($rate %)", 15)
        }
        return CheckResult(
            metrics = listOfNotNull(
                Metric("supplier.grossMargin", "Marge brute par unité", margin, "€"),
                rate?.let { Metric("supplier.grossMarginRate", "Taux de marge brute", it, "%") },
            ),
            findings = findings,
        )
    }

    private companion object {
        const val LONG_LEAD_TIME = 30
        const val LOW_STOCK = 5
        val MIN_RATE = BigDecimal(15)
    }
}
