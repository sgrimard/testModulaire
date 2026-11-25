package dev.testia.mono6.checking.rest

import dev.testia.mono6.checking.api.CheckEvent
import dev.testia.mono6.checking.api.CheckReport
import dev.testia.mono6.checking.api.CheckingOperations
import dev.testia.mono6.checking.service.CheckingService
import dev.testia.mono6.core.rest.RestClients
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import reactor.core.publisher.Flux

/** Contrat REST de checking ; le client REST s'utilise comme le service en appel direct. */
@HttpExchange("/api/checking", accept = [MediaType.APPLICATION_JSON_VALUE])
interface CheckingApi : CheckingOperations {
    @GetExchange("/reports/{productId}")
    override fun check(@PathVariable("productId") productId: Long): CheckReport

    @GetExchange("/reports")
    override fun checkAll(): List<CheckReport>

    @GetExchange("/events", accept = [MediaType.TEXT_EVENT_STREAM_VALUE])
    override fun events(): Flux<CheckEvent>
}

/** Client REST de checking (base : `http://hôte:port`) ; un 404 redevient ProductNotFoundException. */
fun checkingApiClient(baseUrl: String): CheckingApi = RestClients.create(baseUrl, CheckingApi::class.java)

@RestController
class CheckingResource(private val service: CheckingService) : CheckingApi {
    override fun check(productId: Long) = service.check(productId)
    override fun checkAll() = service.checkAll()
    override fun events() = service.events()
}
