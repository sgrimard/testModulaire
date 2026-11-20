package dev.testia.mono.extone

import dev.testia.mono.server.Extension
import dev.testia.mono.server.ExtensionEvent
import dev.testia.mono.server.ExtensionEvents
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap

/** Magasin clé/valeur ; publie « updated » à chaque écriture. */
@ApplicationScoped
class NotesExtension @Inject constructor(private val events: ExtensionEvents) : Extension {
    private val notes = ConcurrentHashMap<String, String>()

    override val name = "notes"

    override fun get(key: String): String? = notes[key]

    override fun put(key: String, value: String) {
        notes[key] = value
        events.publish(ExtensionEvent(name, "updated", key, value))
    }
}
