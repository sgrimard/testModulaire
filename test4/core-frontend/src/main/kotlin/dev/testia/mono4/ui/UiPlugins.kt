package dev.testia.mono4.ui

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.util.Properties

/**
 * Point d'extension de core-frontend, sans dépendance de code : un plug-in d'IHM est un jar qui
 * contient META-INF/ui-plugin.properties (id, module) et le module ES qu'il désigne
 * (servi depuis META-INF/resources). Ce module exporte `register(registry)`.
 * Chaque jar de plug-in ne dépend ainsi que de son propre backend.
 */
data class UiPluginDescriptor(val id: String, val module: String)

/** L'IHM du noyau découvre les plug-ins présents à l'exécution ; elle n'en référence aucun. */
@Path("/ui/plugins")
class UiPluginResource {
    private val plugins: List<UiPluginDescriptor> by lazy {
        Thread.currentThread().contextClassLoader.getResources(DESCRIPTOR).toList()
            .map { url -> Properties().apply { url.openStream().use(::load) } }
            .map { UiPluginDescriptor(it.getProperty("id"), it.getProperty("module")) }
            .distinctBy { it.id }
            .sortedBy { it.id }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun list(): List<UiPluginDescriptor> = plugins

    private companion object {
        const val DESCRIPTOR = "META-INF/ui-plugin.properties"
    }
}
