package dev.testia.mono7.core.rest

import dev.testia.mono7.core.api.ProductDto
import dev.testia.mono7.core.api.ProductNotFoundException
import dev.testia.mono7.core.api.ProductValidationException
import dev.testia.mono7.core.api.ValidationErrors
import dev.testia.mono7.core.service.ProductService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Exposition REST : délégation pure au service (le mapping HTTP vient de [ProductApi]). */
@RestController
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
}

/** Erreurs du contrat -> réponses HTTP. */
@RestControllerAdvice
class ProductErrorHandler {
    @ExceptionHandler
    fun notFound(e: ProductNotFoundException): ResponseEntity<String> =
        ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body(e.message)

    @ExceptionHandler
    fun invalid(e: ProductValidationException): ResponseEntity<ValidationErrors> =
        ResponseEntity.status(400).contentType(MediaType.APPLICATION_JSON).body(ValidationErrors(e.violations))
}
