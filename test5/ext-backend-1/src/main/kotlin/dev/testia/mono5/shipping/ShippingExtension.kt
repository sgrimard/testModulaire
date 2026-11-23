package dev.testia.mono5.shipping

import dev.testia.mono5.core.api.FieldDescriptor
import dev.testia.mono5.core.api.FieldType
import dev.testia.mono5.core.spi.ProductExtension
import dev.testia.mono5.core.spi.SchemaContribution
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Table

@ApplicationScoped
class ShippingSchema : SchemaContribution {
    override val module = SHIPPING
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

/** Extension « shipping » : poids et matière dangereuse, dans sa propre table. */
@ApplicationScoped
class ShippingExtension(private val em: EntityManager) : ProductExtension {
    override val namespace = SHIPPING

    override fun fields() = listOf(
        FieldDescriptor(SHIPPING, "weightKg", "Poids (kg)", FieldType.NUMBER, min = 0.01, max = 1_000.0),
        FieldDescriptor(SHIPPING, "hazardous", "Marchandise dangereuse", FieldType.BOOLEAN),
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
