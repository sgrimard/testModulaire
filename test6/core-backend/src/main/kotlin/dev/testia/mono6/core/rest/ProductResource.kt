package dev.testia.mono6.core.rest

import dev.testia.mono6.core.api.ApiError
import dev.testia.mono6.core.api.ProductDto
import dev.testia.mono6.core.api.ProductNotFoundException
import dev.testia.mono6.core.api.ProductValidationException
import dev.testia.mono6.core.api.UnknownExtensionException
import dev.testia.mono6.core.service.ProductService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Exposition REST : délégation pure au service (le mapping HTTP vient de [ProductApi]). */
@RestController
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

/** Erreurs du contrat -> réponses HTTP (valable pour tout contrôleur, checking compris). */
@RestControllerAdvice
class ProductErrorHandler {
    @ExceptionHandler
    fun notFound(e: ProductNotFoundException): ResponseEntity<ApiError> =
        ResponseEntity.status(404).body(ApiError(ApiError.NOT_FOUND, e.message!!, e.id?.toString()))

    @ExceptionHandler
    fun unknownExtension(e: UnknownExtensionException): ResponseEntity<ApiError> =
        ResponseEntity.status(404).body(ApiError(ApiError.UNKNOWN_EXTENSION, e.message!!, e.extension))

    @ExceptionHandler
    fun invalid(e: ProductValidationException): ResponseEntity<ApiError> =
        ResponseEntity.status(400).body(ApiError(ApiError.INVALID, e.message!!, violations = e.violations))
}

/**
 * CORS de l'API : l'IHM de bureau (Electron, origine `app://desktop`) et le serveur Vite appellent
 * depuis une autre origine. Chaque assemblage choisit les origines (`catalogue.cors.origins`).
 */
@Configuration
class CorsConfig(@Value("\${catalogue.cors.origins:}") private val origins: List<String>) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        if (origins.isEmpty()) return
        registry.addMapping("/api/**").allowedOriginPatterns(*origins.toTypedArray()).allowedMethods("*")
    }
}
