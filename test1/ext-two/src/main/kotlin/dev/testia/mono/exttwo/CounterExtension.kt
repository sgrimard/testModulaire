package dev.testia.mono.exttwo

import dev.testia.mono.server.Extension
import dev.testia.mono.server.ExtensionEvent
import dev.testia.mono.server.ExtensionEvents
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap

/** Compteurs : put ajoute la valeur, get renvoie le total ; publie « incremented » et « threshold » (>= 10). */
@ApplicationScoped
class CounterExtension @Inject constructor(private val events: ExtensionEvents) : Extension {
    private val totals = ConcurrentHashMap<String, Long>()

    override val name = "counter"

    override fun get(key: String): String? = totals[key]?.toString()

    override fun put(key: String, value: String) {
        val delta = value.toLong()
        val before = totals[key] ?: 0
        val after = totals.merge(key, delta, Long::plus)!!
        events.publish(ExtensionEvent(name, "incremented", key, after.toString()))
        if (before < THRESHOLD && after >= THRESHOLD) {
            events.publish(ExtensionEvent(name, "threshold", key, after.toString()))
        }
    }

    private companion object {
        const val THRESHOLD = 10L
    }
}
