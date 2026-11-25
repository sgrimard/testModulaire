package dev.testia.mono7.core.service

import dev.testia.mono7.core.api.ProductDto
import dev.testia.mono7.core.api.QuoteLine
import dev.testia.mono7.core.spi.LabelFormatter
import dev.testia.mono7.core.spi.QuoteStep
import dev.testia.mono7.core.spi.coreLabel
import org.springframework.stereotype.Component
import java.math.BigDecimal

// Les règles de valeurs du noyau (nom obligatoire, prix ≥ 0, catégories) sont désormais
// des contraintes déclarées dans ProductService.CORE_FIELDS, et non plus du code de validation.

/** Première étape du devis : prix unitaire × quantité. */
@Component
class BaseQuoteStep : QuoteStep {
    override val order = 0
    override fun apply(product: ProductDto, quantity: Int, lines: List<QuoteLine>) =
        lines + QuoteLine("base", "${product.name} × $quantity", product.price * BigDecimal(quantity))
}

/**
 * Implémentation par défaut. Un plug-in qui fournit un autre LabelFormatter le marque `@Primary` :
 * c'est alors le sien qui est injecté (équivalent du `@DefaultBean` de Quarkus).
 */
@Component
class CoreLabelFormatter : LabelFormatter {
    override fun label(product: ProductDto) = coreLabel(product)
}
