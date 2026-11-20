package dev.testia.mono2.core.rest

import dev.testia.mono2.core.api.FieldDescriptor
import dev.testia.mono2.core.api.ProductDto
import dev.testia.mono2.core.api.ProductNotFoundException
import dev.testia.mono2.core.api.ProductOperations
import dev.testia.mono2.core.api.ProductValidationException
import dev.testia.mono2.core.api.Quote
import dev.testia.mono2.core.api.ValidationErrors
import dev.testia.mono2.core.api.Violation
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

/**
 * Contrat REST de core-backend. Il étend [ProductOperations] : le client REST généré par Quarkus
 * s'utilise donc exactement comme le service en appel direct (mêmes méthodes, mêmes exceptions).
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

    @POST
    @Path("/validate")
    override fun validate(product: ProductDto): List<Violation>

    @GET
    @Path("/{id}/quote")
    override fun quote(@PathParam("id") id: Long, @QueryParam("quantity") quantity: Int): Quote

    @GET
    @Path("/{id}/label")
    @Produces(MediaType.TEXT_PLAIN)
    override fun label(@PathParam("id") id: Long): String
}

/** Côté client REST : retraduit les statuts HTTP en exceptions du contrat. */
class ProductErrorMapper : ResponseExceptionMapper<RuntimeException> {
    override fun handles(status: Int, headers: MultivaluedMap<String, Any>?) = status == 400 || status == 404

    override fun toThrowable(response: Response): RuntimeException? = when (response.status) {
        404 -> ProductNotFoundException(null)
        400 -> ProductValidationException(response.readEntity(ValidationErrors::class.java).violations)
        else -> null
    }
}
