package dev.testia.mono.server

import io.smallrye.mutiny.Multi
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.jboss.resteasy.reactive.RestStreamElementType

/** Contrat REST publié par le serveur (utilisé comme client MicroProfile). */
@RegisterRestClient(configKey = "extension-api")
@Path("/extensions")
interface ExtensionApi {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun names(): List<String>

    @GET
    @Path("/{name}/values/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    fun get(@PathParam("name") name: String, @PathParam("key") key: String): String

    @PUT
    @Path("/{name}/values/{key}")
    @Consumes(MediaType.TEXT_PLAIN)
    fun put(@PathParam("name") name: String, @PathParam("key") key: String, value: String)

    @GET
    @Path("/{name}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    fun events(@PathParam("name") name: String): Multi<ExtensionEvent>
}
