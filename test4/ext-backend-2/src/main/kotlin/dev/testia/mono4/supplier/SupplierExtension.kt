package dev.testia.mono4.supplier

import dev.testia.mono4.core.api.FieldDescriptor
import dev.testia.mono4.core.api.FieldType
import dev.testia.mono4.core.spi.ProductExtension
import dev.testia.mono4.core.spi.SchemaContribution
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.math.RoundingMode

@ApplicationScoped
class SupplierSchema : SchemaContribution {
    override val module = SUPPLIER
    override val order = 100
    override val scripts = listOf("db/supplier/001_supplier_product.sql")
}

@Entity
@Table(name = "supplier_product")
class SupplierEntity {
    @Id
    @Column(name = "product_id")
    var productId: Long? = null

    @Column(name = "supplier")
    var supplier: String? = null

    @Column(name = "unit_cost", precision = 12, scale = 2)
    var unitCost: BigDecimal? = null

    @Column(name = "lead_time_days")
    var leadTimeDays: Int? = null
}

/** Extension « supplier » : fournisseur, coût d'achat, délai de réapprovisionnement. */
@ApplicationScoped
class SupplierExtension(private val em: EntityManager) : ProductExtension {
    override val namespace = SUPPLIER

    override fun fields() = listOf(
        FieldDescriptor(SUPPLIER, "supplier", "Fournisseur", FieldType.TEXT),
        FieldDescriptor(SUPPLIER, "unitCost", "Coût d'achat unitaire", FieldType.NUMBER, min = 0.0, max = 100_000.0),
        FieldDescriptor(SUPPLIER, "leadTimeDays", "Délai de réapprovisionnement (jours)", FieldType.NUMBER, min = 0.0, max = 365.0),
    )

    override fun load(productIds: Collection<Long>): Map<Long, Map<String, Any?>> =
        em.createQuery("from SupplierEntity s where s.productId in :ids", SupplierEntity::class.java)
            .setParameter("ids", productIds)
            .resultList
            .associate {
                it.productId!! to mapOf(
                    "supplier" to it.supplier,
                    "unitCost" to it.unitCost?.toDouble(),
                    "leadTimeDays" to it.leadTimeDays?.toDouble(),
                )
            }

    override fun save(productId: Long, values: Map<String, Any?>) {
        val entity = em.find(SupplierEntity::class.java, productId)
            ?: SupplierEntity().also { it.productId = productId; em.persist(it) }
        entity.supplier = values["supplier"] as String?
        entity.unitCost = (values["unitCost"] as Double?)?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP) }
        entity.leadTimeDays = (values["leadTimeDays"] as Double?)?.toInt()
    }

    override fun delete(productId: Long) {
        em.find(SupplierEntity::class.java, productId)?.let(em::remove)
    }
}
