package dev.testia.mono.client

import dev.testia.mono.server.ExtensionApi
import dev.testia.mono.server.ExtensionEvent
import dev.testia.mono.server.ExtensionGateway
import dev.testia.mono.server.ExtensionHandle
import dev.testia.mono.server.UnknownExtensionException
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.ws.rs.WebApplicationException
import org.eclipse.microprofile.rest.client.inject.RestClient

/** Implémentation REST du même contrat [ExtensionGateway], bâtie sur [ExtensionApi]. */
@ApplicationScoped
@Named("rest")
class RemoteExtensionGateway @Inject constructor(
    @RestClient private val api: ExtensionApi,
) : ExtensionGateway {
    override fun names(): List<String> = api.names()

    override fun extension(name: String): ExtensionHandle {
        if (name !in api.names()) throw UnknownExtensionException(name)
        return object : ExtensionHandle {
            override val name = name
            override fun get(key: String): String? = try {
                api.get(name, key)
            } catch (e: WebApplicationException) {
                if (e.response.status == 404) null else throw e
            }
            override fun put(key: String, value: String) = api.put(name, key, value)
            override fun events(): Multi<ExtensionEvent> = api.events(name)
        }
    }
}
