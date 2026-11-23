package dev.testia.mono4.core.rest

import dev.testia.mono4.core.api.ApiError
import dev.testia.mono4.core.api.FieldDescriptor
import dev.testia.mono4.core.api.ProductDto
import dev.testia.mono4.core.api.ProductEvent
import dev.testia.mono4.core.api.ProductNotFoundException
import dev.testia.mono4.core.api.ProductOperations
import dev.testia.mono4.core.api.ProductValidationException
import dev.testia.mono4.core.api.UnknownExtensionException
import io.smallrye.mutiny.Multi
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.jboss.resteasy.reactive.RestStreamElementType

/**
 * Contrat REST de core-backend. Il étend [ProductOperations] : le client REST généré par Quarkus
 * s'utilise exactement comme le service en appel direct (mêmes méthodes, mêmes exceptions).
 */
@RegisterRestClient(configKey = "product-api")
@RegisterProvider(ProductErrorMapper::class)
@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ProductApi : ProductOperations {
    @GET
    @Path("/schema")
    override fun schema(): List<FieldDescriptor>

    @GET
    override fun list(): List<ProductDto>

    @GET
    @Path("/{id}")
    override fun get(@PathParam("id") id: Long): ProductDto

    @POST
    override fun create(product: ProductDto): ProductDto

    @PUT
    @Path("/{id}")
    override fun update(@PathParam("id") id: Long, product: ProductDto): ProductDto

    @DELETE
    @Path("/{id}")
    override fun delete(@PathParam("id") id: Long)

    @GET
    @Path("/extensions")
    override fun extensions(): List<String>

    @GET
    @Path("/{id}/extensions/{name}")
    override fun getExtension(@PathParam("id") id: Long, @PathParam("name") name: String): Map<String, Any?>

    @PUT
    @Path("/{id}/extensions/{name}")
    override fun putExtension(
        @PathParam("id") id: Long,
        @PathParam("name") name: String,
        values: Map<String, Any?>,
    ): Map<String, Any?>

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    override fun events(): Multi<ProductEvent>
}

/** Côté client REST : retraduit les erreurs HTTP en exceptions du contrat. */
class ProductErrorMapper : ResponseExceptionMapper<RuntimeException> {
    override fun handles(status: Int, headers: MultivaluedMap<String, Any>?) = status == 400 || status == 404

    override fun toThrowable(response: Response): RuntimeException? {
        val error = runCatching { response.readEntity(ApiError::class.java) }.getOrNull() ?: return null
        return when (error.code) {
            ApiError.NOT_FOUND -> ProductNotFoundException(error.subject?.toLongOrNull())
            ApiError.UNKNOWN_EXTENSION -> UnknownExtensionException(error.subject.orEmpty())
            ApiError.INVALID -> ProductValidationException(error.violations)
            else -> null
        }
    }
}
