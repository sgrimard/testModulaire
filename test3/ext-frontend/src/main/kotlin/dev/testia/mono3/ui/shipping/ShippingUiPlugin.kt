package dev.testia.mono3.ui.shipping

import dev.testia.mono3.ui.UiPlugin
import jakarta.enterprise.context.ApplicationScoped

/** Déclare le module JS servi depuis ce jar (META-INF/resources/plugins/shipping/plugin.js). */
@ApplicationScoped
class ShippingUiPlugin : UiPlugin {
    override val id = "shipping"
    override val module = "/plugins/shipping/plugin.js"
}
