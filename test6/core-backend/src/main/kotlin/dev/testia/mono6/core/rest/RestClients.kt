package dev.testia.mono6.core.rest

import dev.testia.mono6.core.api.ApiError
import dev.testia.mono6.core.api.ProductNotFoundException
import dev.testia.mono6.core.api.ProductValidationException
import dev.testia.mono6.core.api.UnknownExtensionException
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import reactor.core.publisher.Mono

/**
 * Fabrique des clients REST des briques (interfaces `@HttpExchange`). WebClient permet les flux SSE
 * (`Flux`) ; les autres appels sont bloquants, comme en appel direct. Les erreurs HTTP du contrat
 * (corps [ApiError]) redeviennent les exceptions du contrat.
 */
object RestClients {
    private val json = Json.mapper

    fun <T : Any> create(baseUrl: String, type: Class<T>): T {
        val web = WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(JdkClientHttpConnector())
            .codecs {
                it.defaultCodecs().jacksonJsonDecoder(JacksonJsonDecoder(json))
                it.defaultCodecs().jacksonJsonEncoder(JacksonJsonEncoder(json))
            }
            .defaultStatusHandler({ it.value() == 400 || it.value() == 404 }, ::toException)
            .build()
        return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(web)).build().createClient(type)
    }

    private fun toException(response: ClientResponse): Mono<Throwable> =
        response.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { body ->
            val error = runCatching { json.readValue(body, ApiError::class.java) }.getOrNull()
            val mapped: Throwable? = when (error?.code) {
                ApiError.NOT_FOUND -> ProductNotFoundException(error.subject?.toLongOrNull())
                ApiError.UNKNOWN_EXTENSION -> UnknownExtensionException(error.subject.orEmpty())
                ApiError.INVALID -> ProductValidationException(error.violations)
                else -> null
            }
            mapped?.let { Mono.just(it) } ?: response.createException().map { it as Throwable }
        }
}
