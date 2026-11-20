package dev.testia.mono.server

import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/** Implémentation « appel direct » : agrège les beans [Extension] présents sur le classpath. */
@ApplicationScoped
class LocalExtensionGateway @Inject constructor(
    extensions: Instance<Extension>,
    private val events: ExtensionEvents,
) : ExtensionGateway {
    private val byName: Map<String, Extension> = extensions.associateBy { it.name }.toSortedMap()

    override fun names(): List<String> = byName.keys.toList()

    override fun extension(name: String): ExtensionHandle {
        val ext = byName[name] ?: throw UnknownExtensionException(name)
        return object : ExtensionHandle {
            override val name = name
            override fun get(key: String) = ext.get(key)
            override fun put(key: String, value: String) = ext.put(key, value)
            override fun events(): Multi<ExtensionEvent> = events.stream(name)
        }
    }
}
