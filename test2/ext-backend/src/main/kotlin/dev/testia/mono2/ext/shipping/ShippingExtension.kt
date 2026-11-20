package dev.testia.mono2.ext.shipping

import dev.testia.mono2.core.api.FieldDescriptor
import dev.testia.mono2.core.api.FieldType
import dev.testia.mono2.core.api.ProductDto
import dev.testia.mono2.core.api.QuoteLine
import dev.testia.mono2.core.api.Violation
import dev.testia.mono2.core.service.coreLabel
import dev.testia.mono2.core.spi.LabelFormatter
import dev.testia.mono2.core.spi.ProductExtension
import dev.testia.mono2.core.spi.ProductValidator
import dev.testia.mono2.core.spi.QuoteStep
import dev.testia.mono2.core.spi.SchemaContribution
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.math.RoundingMode

const val NS = "shipping"

private fun ProductDto.weightKg() = extensions[NS]?.get("weightKg") as Double?
private fun ProductDto.hazardous() = extensions[NS]?.get("hazardous") == true

// --- schéma et persistance (table propre au plug-in) -----------------------------------------

@ApplicationScoped
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

@ApplicationScoped
class ShippingExtension(private val em: EntityManager) : ProductExtension {
    override val namespace = NS

    override fun fields() = listOf(
        FieldDescriptor(NS, "weightKg", "Poids (kg)", FieldType.NUMBER),
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

/** Additive : s'ajoute aux règles du noyau. */
@ApplicationScoped
class ShippingValidator : ProductValidator {
    override fun validate(product: ProductDto): List<Violation> = buildList {
        val weight = product.weightKg()
        if (weight != null && weight <= 0) add(Violation("$NS.weightKg", "Le poids doit être positif"))
        if (product.hazardous() && weight == null) {
            add(Violation("$NS.weightKg", "Poids obligatoire pour une marchandise dangereuse"))
        }
    }
}

/** Pipeline : ajoute les frais de port (1,50 €/kg) et une surtaxe matière dangereuse. */
@ApplicationScoped
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

/** Remplacement : supplante le CoreLabelFormatter (@DefaultBean) du noyau. */
@ApplicationScoped
class ShippingLabelFormatter : LabelFormatter {
    override fun label(product: ProductDto): String = buildString {
        append(coreLabel(product))
        product.weightKg()?.let { append(" – $it kg") }
        if (product.hazardous()) append(" ⚠")
    }
}
