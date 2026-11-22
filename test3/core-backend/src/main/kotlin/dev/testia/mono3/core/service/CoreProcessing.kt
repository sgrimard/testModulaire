package dev.testia.mono3.core.service

import dev.testia.mono3.core.api.ProductDto
import dev.testia.mono3.core.api.QuoteLine
import dev.testia.mono3.core.spi.LabelFormatter
import dev.testia.mono3.core.spi.QuoteStep
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import java.math.BigDecimal

// Les règles de valeurs du noyau (nom obligatoire, prix ≥ 0, catégories) sont désormais
// des contraintes déclarées dans ProductService.CORE_FIELDS, et non plus du code de validation.

/** Première étape du devis : prix unitaire × quantité. */
@ApplicationScoped
class BaseQuoteStep : QuoteStep {
    override val order = 0
    override fun apply(product: ProductDto, quantity: Int, lines: List<QuoteLine>) =
        lines + QuoteLine("base", "${product.name} × $quantity", product.price * BigDecimal(quantity))
}

/** Libellé par défaut, réutilisable par un plug-in qui le remplace. */
fun coreLabel(product: ProductDto): String = "${product.name} (${product.price} €)"

/** Implémentation par défaut : désactivée automatiquement si un plug-in fournit un autre LabelFormatter. */
@DefaultBean
@ApplicationScoped
class CoreLabelFormatter : LabelFormatter {
    override fun label(product: ProductDto) = coreLabel(product)
}
