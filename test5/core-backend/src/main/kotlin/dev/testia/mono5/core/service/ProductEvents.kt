package dev.testia.mono5.core.service

import dev.testia.mono5.core.api.ProductEvent
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.event.TransactionPhase
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bus des changements du noyau. Le service émet un événement CDI dans sa transaction ; il n'est
 * diffusé aux abonnés qu'après commit (une modification annulée ne produit rien).
 * Les abonnés sont appelés sur le thread qui a validé : ils ne doivent pas y faire de travail lourd.
 */
@ApplicationScoped
class ProductEvents {
    private val subscribers = CopyOnWriteArrayList<MultiEmitter<in ProductEvent>>()

    fun onCommitted(@Observes(during = TransactionPhase.AFTER_SUCCESS) event: ProductEvent) {
        subscribers.forEach { it.emit(event) }
    }

    fun stream(): Multi<ProductEvent> = Multi.createFrom().emitter { emitter ->
        subscribers += emitter
        emitter.onTermination { subscribers -= emitter }
        // Émis après l'inscription : tout événement validé ensuite sera reçu.
        emitter.emit(ProductEvent(ProductEvent.SUBSCRIBED))
    }
}
