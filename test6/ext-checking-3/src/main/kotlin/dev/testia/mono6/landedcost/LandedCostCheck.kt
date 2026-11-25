package dev.testia.mono6.landedcost

import dev.testia.mono6.checking.api.Finding
import dev.testia.mono6.checking.api.Metric
import dev.testia.mono6.checking.api.Severity
import dev.testia.mono6.checking.spi.CheckResult
import dev.testia.mono6.checking.spi.ProductCheck
import dev.testia.mono6.core.api.ProductDto
import dev.testia.mono6.shipping.shipping
import dev.testia.mono6.supplier.supplier
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Complète le calcul avec les données des DEUX extensions : coût de revient rendu
 * (achat d'ext-backend-2 + transport d'ext-backend-1) et marge nette.
 * Ne dépend ni d'ext-checking-1 ni d'ext-checking-2 : il recalcule à partir des données.
 */
@Component
class LandedCostCheck : ProductCheck {
    override val name = "landed"
    override val order = 200 // après les contributions qui ne lisent qu'une extension

    override fun evaluate(product: ProductDto): CheckResult {
        // Les données manquantes sont déjà signalées par les contrôles de chaque extension.
        val unitCost = product.supplier().unitCost ?: return CheckResult()
        val shipping = product.shipping().costPerUnit() ?: return CheckResult()
        val landed = unitCost + shipping
        val net = product.price - landed
        val rate = if (product.price.signum() == 0) null else (net * BigDecimal(100)).divide(product.price, 1, RoundingMode.HALF_UP)
        val findings = buildList {
            when {
                // vendu à perte dès l'achat : déjà signalé par la contribution « supplier »
                net.signum() < 0 && unitCost <= product.price ->
                    add(Finding("landed.margin-absorbed", Severity.BLOCKING, "Marge absorbée par le transport (${net} € par unité)", 40))
                net.signum() >= 0 && rate != null && rate < MIN_NET_RATE ->
                    add(Finding("landed.thin-margin", Severity.WARNING, "Marge nette faible ($rate %)", 10))
            }
        }
        return CheckResult(
            metrics = listOfNotNull(
                Metric("landed.cost", "Coût de revient rendu", landed, "€"),
                Metric("landed.netMargin", "Marge nette par unité", net, "€"),
                rate?.let { Metric("landed.netMarginRate", "Taux de marge nette", it, "%") },
            ),
            findings = findings,
        )
    }

    private companion object {
        val MIN_NET_RATE = BigDecimal(10)
    }
}
