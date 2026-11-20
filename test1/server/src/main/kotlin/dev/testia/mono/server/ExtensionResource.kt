package dev.testia.mono.server

import io.smallrye.mutiny.Multi
import jakarta.inject.Inject
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path

/** Exposition REST de la passerelle locale (les annotations viennent de [ExtensionApi]). */
@Path("/extensions")
class ExtensionResource @Inject constructor(private val gateway: LocalExtensionGateway) : ExtensionApi {
    override fun names(): List<String> = gateway.names()

    override fun get(name: String, key: String): String =
        handle(name).get(key) ?: throw NotFoundException("No value for $name/$key")

    override fun put(name: String, key: String, value: String) = handle(name).put(key, value)

    override fun events(name: String): Multi<ExtensionEvent> = handle(name).events()

    private fun handle(name: String): ExtensionHandle = try {
        gateway.extension(name)
    } catch (e: UnknownExtensionException) {
        throw NotFoundException(e.message)
    }
}
