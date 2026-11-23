package dev.testia.mono5.shipping

import dev.testia.mono5.core.api.ProductDto
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * API publique d'ext-backend-1 pour les autres modules (ext-checking-1, ext-checking-3) :
 * une vue typée de ses données et son tarif. Le format de stockage reste interne au module.
 */

const val SHIPPING = "shipping"

data class ShippingInfo(val weightKg: Double?, val hazardous: Boolean) {
    /** Frais de transport d'une unité, ou null si le poids est inconnu. */
    fun costPerUnit(): BigDecimal? = weightKg?.let { w ->
        (BigDecimal.valueOf(w) * RATE_PER_KG + if (hazardous) HAZMAT_FEE else BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)
    }

    companion object {
        val RATE_PER_KG = BigDecimal("1.50")
        val HAZMAT_FEE = BigDecimal("8.00")
    }
}

/** Données d'ext-backend-1 portées par un produit lu via core-backend. */
fun ProductDto.shipping(): ShippingInfo {
    val values = extensions[SHIPPING].orEmpty()
    return ShippingInfo((values["weightKg"] as Number?)?.toDouble(), values["hazardous"] == true)
}
