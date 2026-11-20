package dev.testia.mono2.ui

import jakarta.enterprise.inject.Instance
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

/**
 * Point d'extension de core-frontend : un plug-in UI déclare un module ES servi depuis son jar
 * (META-INF/resources/...). Ce module exporte `register(registry)`.
 */
interface UiPlugin {
    val id: String
    val module: String
}

data class UiPluginDescriptor(val id: String, val module: String)

/** L'IHM du noyau découvre les plug-ins présents à l'exécution ; elle n'en référence aucun. */
@Path("/ui/plugins")
class UiPluginResource(private val plugins: Instance<UiPlugin>) {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun list(): List<UiPluginDescriptor> = plugins.map { UiPluginDescriptor(it.id, it.module) }.sortedBy { it.id }
}
