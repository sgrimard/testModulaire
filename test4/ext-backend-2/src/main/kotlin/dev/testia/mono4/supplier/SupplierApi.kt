package dev.testia.mono4.supplier

import dev.testia.mono4.core.api.ProductDto
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * API publique d'ext-backend-2 pour les autres modules (ext-checking-2, ext-checking-3) :
 * une vue typée de ses données. Le format de stockage reste interne au module.
 */

const val SUPPLIER = "supplier"

data class SupplierInfo(val supplier: String?, val unitCost: BigDecimal?, val leadTimeDays: Int?)

/** Données d'ext-backend-2 portées par un produit lu via core-backend. */
fun ProductDto.supplier(): SupplierInfo {
    val values = extensions[SUPPLIER].orEmpty()
    return SupplierInfo(
        values["supplier"] as String?,
        (values["unitCost"] as Number?)?.let { BigDecimal.valueOf(it.toDouble()).setScale(2, RoundingMode.HALF_UP) },
        (values["leadTimeDays"] as Number?)?.toInt(),
    )
}
