package dev.testia.mono.server

import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.CopyOnWriteArrayList

/** Bus d'événements du serveur : les extensions publient, les clients s'abonnent par nom d'extension. */
@ApplicationScoped
class ExtensionEvents {
    private class Subscriber(val extension: String, val emitter: MultiEmitter<in ExtensionEvent>)

    private val subscribers = CopyOnWriteArrayList<Subscriber>()

    fun publish(event: ExtensionEvent) {
        subscribers.filter { it.extension == event.extension }.forEach { it.emitter.emit(event) }
    }

    fun stream(extension: String): Multi<ExtensionEvent> = Multi.createFrom().emitter { emitter ->
        val subscriber = Subscriber(extension, emitter)
        subscribers += subscriber
        emitter.onTermination { subscribers -= subscriber }
        // Émis après l'inscription : tout événement publié ensuite sera reçu.
        emitter.emit(ExtensionEvent(extension, ExtensionEvent.SUBSCRIBED))
    }
}
