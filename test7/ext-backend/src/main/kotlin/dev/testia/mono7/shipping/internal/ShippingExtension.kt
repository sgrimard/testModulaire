package dev.testia.mono7.shipping.internal

import dev.testia.mono7.core.api.FieldDescriptor
import dev.testia.mono7.core.api.FieldOption
import dev.testia.mono7.core.api.FieldType
import dev.testia.mono7.core.api.ProductDto
import dev.testia.mono7.core.api.QuoteLine
import dev.testia.mono7.core.spi.FieldPolicy
import dev.testia.mono7.core.spi.LabelFormatter
import dev.testia.mono7.core.spi.ProductExtension
import dev.testia.mono7.core.spi.QuoteStep
import dev.testia.mono7.core.spi.SchemaContribution
import dev.testia.mono7.core.spi.coreLabel
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

const val NS = "shipping"

private fun ProductDto.weightKg() = extensions[NS]?.get("weightKg") as Double?
private fun ProductDto.hazardous() = extensions[NS]?.get("hazardous") == true

// --- schéma et persistance (table propre au plug-in) -----------------------------------------

@Component
class ShippingSchema : SchemaContribution {
    override val module = NS
    override val order = 100
    override val scripts = listOf("db/shipping/001_shipping_product.sql")
}

@Entity
@Table(name = "shipping_product")
class ShippingEntity {
    @Id
    @Column(name = "product_id")
    var productId: Long? = null

    @Column(name = "weight_kg")
    var weightKg: Double? = null

    @Column(name = "hazardous")
    var hazardous: Boolean? = null
}

@Component
class ShippingExtension(private val em: EntityManager) : ProductExtension {
    override val namespace = NS

    override fun fields() = listOf(
        FieldDescriptor(NS, "weightKg", "Poids (kg)", FieldType.NUMBER, min = 0.01, max = 1_000.0),
        FieldDescriptor(NS, "hazardous", "Marchandise dangereuse", FieldType.BOOLEAN),
    )

    override fun load(productIds: Collection<Long>): Map<Long, Map<String, Any?>> =
        em.createQuery("from ShippingEntity s where s.productId in :ids", ShippingEntity::class.java)
            .setParameter("ids", productIds)
            .resultList
            .associate { it.productId!! to mapOf("weightKg" to it.weightKg, "hazardous" to it.hazardous) }

    override fun save(productId: Long, values: Map<String, Any?>) {
        val entity = em.find(ShippingEntity::class.java, productId)
            ?: ShippingEntity().also { it.productId = productId; em.persist(it) }
        entity.weightKg = values["weightKg"] as Double?
        entity.hazardous = values["hazardous"] as Boolean?
    }

    override fun delete(productId: Long) {
        em.find(ShippingEntity::class.java, productId)?.let(em::remove)
    }
}

// --- spécialisation des traitements du noyau --------------------------------------------------

/**
 * Modifie les valeurs valides de champs du noyau et du plug-in :
 *  - catégorie (liste du noyau) : ajoute « chemicals » ; si dangereux, seules chemicals/tools restent ;
 *  - prix (borne du noyau) : resserré à [0,01 ; 5 000] ;
 *  - poids (champ du plug-in) : obligatoire si dangereux.
 */
@Component
class ShippingFieldPolicy : FieldPolicy {
    override val order = 100

    override fun adjust(field: FieldDescriptor, draft: ProductDto?): FieldDescriptor {
        val hazardous = draft?.hazardous() == true
        return when (field.key) {
            "category" -> {
                val options = field.options.orEmpty() + FieldOption("chemicals", "Produits chimiques")
                field.copy(options = if (hazardous) options.filter { it.value in HAZARDOUS_CATEGORIES } else options)
            }
            "price" -> field.copy(min = maxOf(field.min ?: MIN_PRICE, MIN_PRICE), max = minOf(field.max ?: MAX_PRICE, MAX_PRICE))
            "$NS.weightKg" -> field.copy(required = field.required || hazardous)
            else -> field
        }
    }

    private companion object {
        const val MIN_PRICE = 0.01
        const val MAX_PRICE = 5_000.0
        val HAZARDOUS_CATEGORIES = setOf("chemicals", "tools")
    }
}

/** Pipeline : ajoute les frais de port (1,50 €/kg) et une surtaxe matière dangereuse. */
@Component
class ShippingQuoteStep : QuoteStep {
    override val order = 100

    override fun apply(product: ProductDto, quantity: Int, lines: List<QuoteLine>): List<QuoteLine> {
        val weight = product.weightKg() ?: return lines
        val shipping = BigDecimal(weight * quantity * RATE_PER_KG).setScale(2, RoundingMode.HALF_UP)
        return lines +
            QuoteLine("shipping", "Transport ${weight * quantity} kg", shipping) +
            listOfNotNull(QuoteLine("hazmat", "Surtaxe matière dangereuse", HAZMAT_FEE).takeIf { product.hazardous() })
    }

    private companion object {
        const val RATE_PER_KG = 1.5
        val HAZMAT_FEE = BigDecimal("20.00")
    }
}

/** Remplacement : `@Primary` supplante le CoreLabelFormatter (implémentation par défaut) du noyau. */
@Component
@Primary
class ShippingLabelFormatter : LabelFormatter {
    override fun label(product: ProductDto): String = buildString {
        append(coreLabel(product))
        product.weightKg()?.let { append(" – $it kg") }
        if (product.hazardous()) append(" ⚠")
    }
}
