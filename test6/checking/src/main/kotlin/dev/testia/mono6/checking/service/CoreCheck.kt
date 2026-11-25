package dev.testia.mono6.checking.service

import dev.testia.mono6.checking.api.Finding
import dev.testia.mono6.checking.api.Metric
import dev.testia.mono6.checking.api.Severity
import dev.testia.mono6.checking.spi.CheckResult
import dev.testia.mono6.checking.spi.ProductCheck
import dev.testia.mono6.core.api.ProductDto
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/** Partie du calcul fournie par checking lui-même : uniquement les données de core-backend. */
@Component
class CoreCheck : ProductCheck {
    override val name = "core"
    override val order = 0

    override fun evaluate(product: ProductDto): CheckResult {
        val stockValue = (product.price * BigDecimal(product.stock)).setScale(2, RoundingMode.HALF_UP)
        val findings = buildList {
            if (product.price.signum() == 0) add(Finding("core.free", Severity.BLOCKING, "Prix de vente nul", 50))
            when {
                product.stock == 0 -> add(Finding("core.out-of-stock", Severity.WARNING, "Rupture de stock", 20))
                product.stock < LOW_STOCK -> add(Finding("core.low-stock", Severity.WARNING, "Stock faible (${product.stock})", 5))
            }
        }
        return CheckResult(listOf(Metric("core.stockValue", "Valeur du stock", stockValue, "€")), findings)
    }

    companion object {
        const val LOW_STOCK = 5
    }
}
