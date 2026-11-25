package dev.testia.mono6.core.rest

import dev.testia.mono6.core.api.ApiError
import dev.testia.mono6.core.api.FieldDescriptor
import dev.testia.mono6.core.api.ProductDto
import dev.testia.mono6.core.api.ProductEvent
import dev.testia.mono6.core.api.ProductOperations
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import org.springframework.web.service.annotation.PutExchange
import reactor.core.publisher.Flux

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

    @GetExchange
    override fun list(): List<ProductDto>

    @GetExchange("/{id}")
    override fun get(@PathVariable("id") id: Long): ProductDto

    @PostExchange
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Produit invalide", content = [Content(schema = Schema(implementation = ApiError::class))])
    override fun create(@RequestBody product: ProductDto): ProductDto

    @PutExchange("/{id}")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Produit invalide", content = [Content(schema = Schema(implementation = ApiError::class))])
    override fun update(@PathVariable("id") id: Long, @RequestBody product: ProductDto): ProductDto

    @DeleteExchange("/{id}")
    override fun delete(@PathVariable("id") id: Long)

    @GetExchange("/extensions")
    override fun extensions(): List<String>

    @GetExchange("/{id}/extensions/{name}")
    override fun getExtension(@PathVariable("id") id: Long, @PathVariable("name") name: String): Map<String, Any?>

    @PutExchange("/{id}/extensions/{name}")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Produit invalide", content = [Content(schema = Schema(implementation = ApiError::class))])
    override fun putExtension(
        @PathVariable("id") id: Long,
        @PathVariable("name") name: String,
        @RequestBody values: Map<String, Any?>,
    ): Map<String, Any?>

    @GetExchange("/events", accept = [MediaType.TEXT_EVENT_STREAM_VALUE])
    override fun events(): Flux<ProductEvent>
}

/** Client REST de core-backend (base : `http://hôte:port`). */
fun productApiClient(baseUrl: String): ProductApi = RestClients.create(baseUrl, ProductApi::class.java)
