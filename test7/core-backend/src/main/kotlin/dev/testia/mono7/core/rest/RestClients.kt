package dev.testia.mono7.core.rest

import dev.testia.mono7.core.api.ProductNotFoundException
import dev.testia.mono7.core.api.ProductValidationException
import dev.testia.mono7.core.api.ValidationErrors
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import reactor.core.publisher.Mono

/**
 * Fabrique des clients REST (interfaces `@HttpExchange`). Les appels sont bloquants, comme en appel
 * direct ; les statuts HTTP du contrat redeviennent ses exceptions (404 -> [ProductNotFoundException],
 * 400 + [ValidationErrors] -> [ProductValidationException]).
 */
object RestClients {
    fun <T : Any> create(baseUrl: String, type: Class<T>): T {
        val web = WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(JdkClientHttpConnector())
            .codecs {
                it.defaultCodecs().jacksonJsonDecoder(JacksonJsonDecoder(Json.mapper))
                it.defaultCodecs().jacksonJsonEncoder(JacksonJsonEncoder(Json.mapper))
            }
            .defaultStatusHandler({ it.value() == 400 || it.value() == 404 }, ::toException)
            .build()
        return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(web)).build().createClient(type)
    }

    private fun toException(response: ClientResponse): Mono<Throwable> =
        if (response.statusCode().value() == 404) {
            response.releaseBody().then(Mono.just(ProductNotFoundException(null)))
        } else {
            response.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { body ->
                runCatching { Json.mapper.readValue(body, ValidationErrors::class.java) }.getOrNull()
                    ?.let { Mono.just<Throwable>(ProductValidationException(it.violations)) }
                    ?: response.createException().map { it as Throwable }
            }
        }
}
