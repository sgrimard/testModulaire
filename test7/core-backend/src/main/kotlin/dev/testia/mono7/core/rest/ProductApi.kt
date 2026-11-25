package dev.testia.mono7.core.rest

import dev.testia.mono7.core.api.FieldDescriptor
import dev.testia.mono7.core.api.ProductDto
import dev.testia.mono7.core.api.ProductOperations
import dev.testia.mono7.core.api.Quote
import dev.testia.mono7.core.api.Violation
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import org.springframework.web.service.annotation.PutExchange

/**
 * Contrat REST de core-backend. Il étend [ProductOperations] et sert des deux côtés :
 *  - serveur : [ProductResource] l'implémente, Spring MVC lit les annotations `@HttpExchange` ;
 *  - client : [productApiClient] en fait un client HTTP qui s'utilise exactement comme le service
 *    en appel direct (mêmes méthodes, mêmes exceptions).
 */
@HttpExchange("/api/products", accept = [MediaType.APPLICATION_JSON_VALUE])
interface ProductApi : ProductOperations {
    @GetExchange("/schema")
    override fun schema(): List<FieldDescriptor>

    @PostExchange("/schema")
    override fun schemaFor(@RequestBody draft: ProductDto): List<FieldDescriptor>

    @GetExchange
    override fun list(): List<ProductDto>

    @GetExchange("/{id}")
    override fun get(@PathVariable("id") id: Long): ProductDto

    @PostExchange
    override fun create(@RequestBody product: ProductDto): ProductDto

    @PutExchange("/{id}")
    override fun update(@PathVariable("id") id: Long, @RequestBody product: ProductDto): ProductDto

    @DeleteExchange("/{id}")
    override fun delete(@PathVariable("id") id: Long)

    @PostExchange("/validate")
    override fun validate(@RequestBody product: ProductDto): List<Violation>

    @GetExchange("/{id}/quote")
    override fun quote(@PathVariable("id") id: Long, @RequestParam("quantity") quantity: Int): Quote

    @GetExchange("/{id}/label", accept = [MediaType.TEXT_PLAIN_VALUE])
    override fun label(@PathVariable("id") id: Long): String
}

/** Client REST de core-backend (base : `http://hôte:port`). */
fun productApiClient(baseUrl: String): ProductApi = RestClients.create(baseUrl, ProductApi::class.java)
