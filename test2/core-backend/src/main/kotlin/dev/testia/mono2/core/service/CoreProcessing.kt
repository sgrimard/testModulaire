package dev.testia.mono2.core.service

import dev.testia.mono2.core.api.ProductDto
import dev.testia.mono2.core.api.QuoteLine
import dev.testia.mono2.core.api.Violation
import dev.testia.mono2.core.spi.LabelFormatter
import dev.testia.mono2.core.spi.ProductValidator
import dev.testia.mono2.core.spi.QuoteStep
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import java.math.BigDecimal

/** Règles du noyau : elles restent actives quels que soient les plug-ins. */
@ApplicationScoped
class CoreValidator : ProductValidator {
    override fun validate(product: ProductDto): List<Violation> = buildList {
        if (product.name.isBlank()) add(Violation("name", "Le nom est obligatoire"))
        if (product.price < BigDecimal.ZERO) add(Violation("price", "Le prix doit être positif"))
    }
}

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
