package dev.testia.mono3.core.rest

import dev.testia.mono3.core.api.ProductDto
import dev.testia.mono3.core.api.ProductNotFoundException
import dev.testia.mono3.core.api.ProductValidationException
import dev.testia.mono3.core.api.ValidationErrors
import dev.testia.mono3.core.service.ProductService
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

/** Exposition REST : délégation pure au service (les annotations JAX-RS viennent de [ProductApi]). */
@Path("/api/products")
class ProductResource(private val service: ProductService) : ProductApi {
    override fun schema() = service.schema()
    override fun schemaFor(draft: ProductDto) = service.schemaFor(draft)
    override fun list() = service.list()
    override fun get(id: Long) = service.get(id)
    override fun create(product: ProductDto) = service.create(product)
    override fun update(id: Long, product: ProductDto) = service.update(id, product)
    override fun delete(id: Long) = service.delete(id)
    override fun validate(product: ProductDto) = service.validate(product)
    override fun quote(id: Long, quantity: Int) = service.quote(id, quantity)
    override fun label(id: Long) = service.label(id)

    @ServerExceptionMapper
    fun notFound(e: ProductNotFoundException): Response =
        Response.status(404).type(MediaType.TEXT_PLAIN).entity(e.message).build()

    @ServerExceptionMapper
    fun invalid(e: ProductValidationException): Response =
        Response.status(400).type(MediaType.APPLICATION_JSON).entity(ValidationErrors(e.violations)).build()
}
