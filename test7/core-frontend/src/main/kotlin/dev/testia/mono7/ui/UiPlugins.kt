package dev.testia.mono7.ui

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Properties

/**
 * Point d'extension de core-frontend, sans dépendance de code : un plug-in d'IHM est un jar qui
 * contient META-INF/ui-plugin.properties (id, module) et le module ES qu'il désigne
 * (servi depuis META-INF/resources, comme les fichiers de l'IHM du noyau). Ce module exporte `register(registry)`.
 * Chaque jar de plug-in ne dépend ainsi que de son propre backend.
 */
data class UiPluginDescriptor(val id: String, val module: String)

/** L'IHM du noyau découvre les plug-ins présents à l'exécution ; elle n'en référence aucun. */
@RestController
class UiPluginResource {
    private val plugins: List<UiPluginDescriptor> by lazy {
        UiPluginResource::class.java.classLoader.getResources(DESCRIPTOR).toList()
            .map { url -> Properties().apply { url.openStream().use(::load) } }
            .map { UiPluginDescriptor(it.getProperty("id"), it.getProperty("module")) }
            .distinctBy { it.id }
            .sortedBy { it.id }
    }

    @GetMapping("/ui/plugins")
    fun list(): List<UiPluginDescriptor> = plugins

    private companion object {
        const val DESCRIPTOR = "META-INF/ui-plugin.properties"
    }
}
