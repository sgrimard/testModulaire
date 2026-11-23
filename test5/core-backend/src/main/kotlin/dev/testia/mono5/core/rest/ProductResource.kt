package dev.testia.mono5.core.rest

import dev.testia.mono5.core.api.ApiError
import dev.testia.mono5.core.api.ProductDto
import dev.testia.mono5.core.api.ProductNotFoundException
import dev.testia.mono5.core.api.ProductValidationException
import dev.testia.mono5.core.api.UnknownExtensionException
import dev.testia.mono5.core.service.ProductService
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

/** Exposition REST : délégation pure au service (les annotations JAX-RS viennent de [ProductApi]). */
@Path("/api/products")
class ProductResource(private val service: ProductService) : ProductApi {
    override fun schema() = service.schema()
    override fun list() = service.list()
    override fun get(id: Long) = service.get(id)
    override fun create(product: ProductDto) = service.create(product)
    override fun update(id: Long, product: ProductDto) = service.update(id, product)
    override fun delete(id: Long) = service.delete(id)
    override fun extensions() = service.extensions()
    override fun getExtension(id: Long, name: String) = service.getExtension(id, name)
    override fun putExtension(id: Long, name: String, values: Map<String, Any?>) = service.putExtension(id, name, values)
    override fun events() = service.events()
}

/** Erreurs du contrat -> réponses HTTP (valable pour toute ressource, checking compris). */
class ProductExceptionMappers {
    @ServerExceptionMapper
    fun notFound(e: ProductNotFoundException): Response =
        error(404, ApiError(ApiError.NOT_FOUND, e.message!!, e.id?.toString()))

    @ServerExceptionMapper
    fun unknownExtension(e: UnknownExtensionException): Response =
        error(404, ApiError(ApiError.UNKNOWN_EXTENSION, e.message!!, e.extension))

    @ServerExceptionMapper
    fun invalid(e: ProductValidationException): Response =
        error(400, ApiError(ApiError.INVALID, e.message!!, violations = e.violations))

    private fun error(status: Int, body: ApiError): Response =
        Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build()
}
