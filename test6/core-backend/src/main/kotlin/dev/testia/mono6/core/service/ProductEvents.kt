package dev.testia.mono6.core.service

import dev.testia.mono6.core.api.ProductEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bus des changements du noyau vers les abonnés du flux (SSE, clients). Le service publie un
 * événement Spring dans sa transaction ; il n'est diffusé qu'après commit (une modification
 * annulée ne produit rien). Les abonnés sont appelés sur le thread qui a validé : pas de travail lourd.
 */
@Component
class ProductEvents {
    private val subscribers = CopyOnWriteArrayList<FluxSink<ProductEvent>>()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onCommitted(event: ProductEvent) {
        subscribers.forEach { it.next(event) }
    }

    fun stream(): Flux<ProductEvent> = Flux.create { sink ->
        subscribers += sink
        sink.onDispose { subscribers -= sink }
        // Émis après l'inscription : tout événement validé ensuite sera reçu.
        sink.next(ProductEvent(ProductEvent.SUBSCRIBED))
    }
}
