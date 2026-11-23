package dev.testia.mono5.checking.rest

import dev.testia.mono5.checking.api.CheckEvent
import dev.testia.mono5.checking.api.CheckReport
import dev.testia.mono5.checking.api.CheckingOperations
import dev.testia.mono5.checking.service.CheckingService
import dev.testia.mono5.core.rest.ProductErrorMapper
import io.smallrye.mutiny.Multi
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.jboss.resteasy.reactive.RestStreamElementType

/** Contrat REST de checking ; le client REST généré s'utilise comme le service en appel direct. */
@RegisterRestClient(configKey = "checking-api")
@RegisterProvider(ProductErrorMapper::class) // 404 -> ProductNotFoundException
@Path("/api/checking")
@Produces(MediaType.APPLICATION_JSON)
interface CheckingApi : CheckingOperations {
    @GET
    @Path("/reports/{productId}")
    override fun check(@PathParam("productId") productId: Long): CheckReport

    @GET
    @Path("/reports")
    override fun checkAll(): List<CheckReport>

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    override fun events(): Multi<CheckEvent>
}

@Path("/api/checking")
class CheckingResource(private val service: CheckingService) : CheckingApi {
    override fun check(productId: Long) = service.check(productId)
    override fun checkAll() = service.checkAll()
    override fun events() = service.events()
}
