package dev.testia.mono.app

import dev.testia.mono.client.ClientFacade
import dev.testia.mono.client.ClientFacade.Mode
import dev.testia.mono.server.ExtensionEvent
import dev.testia.mono.server.ExtensionHandle
import dev.testia.mono.server.UnknownExtensionException
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@QuarkusTest
class AssemblyTest {
    @Inject
    lateinit var client: ClientFacade

    @Test
    fun `discovers extensions by name in both modes`() {
        Mode.entries.forEach { mode ->
            assertEquals(listOf("counter", "notes"), client.gateway(mode).names(), "mode $mode")
        }
    }

    @Test
    fun `unknown extension is rejected in both modes`() {
        Mode.entries.forEach { mode ->
            assertThrows<UnknownExtensionException> { client.gateway(mode).extension("nope") }
        }
    }

    @Test
    fun `get and put on every discovered extension`() {
        Mode.entries.forEach { mode ->
            val gateway = client.gateway(mode)
            gateway.names().forEach { name ->
                val ext = gateway.extension(name)
                val key = "k-$mode"
                assertNull(ext.get(key), "$mode/$name")
                ext.put(key, "3")
                assertNotNull(ext.get(key), "$mode/$name")
            }
        }
        // Les deux modes voient le même état : écrit en REST, lu en direct.
        client.gateway(Mode.REST).extension("notes").put("shared", "hello")
        assertEquals("hello", client.gateway(Mode.DIRECT).extension("notes").get("shared"))
    }

    @Test
    fun `subscribe to extension messages in both modes`() {
        Mode.entries.forEach { mode ->
            val notes = client.gateway(mode).extension("notes")
            val events = notes.collect {
                notes.put("e-$mode", "v")
            }
            assertEquals(ExtensionEvent("notes", "updated", "e-$mode", "v"), events.single())

            val counter = client.gateway(mode).extension("counter")
            val counterEvents = counter.collect(expected = 3) {
                counter.put("c-$mode", "6")
                counter.put("c-$mode", "6")
            }
            assertEquals(listOf("incremented", "incremented", "threshold"), counterEvents.map { it.type })
            assertEquals("12", counterEvents.last().value)
        }
    }

    /** S'abonne, attend SUBSCRIBED, exécute [action], puis renvoie les [expected] événements suivants. */
    private fun ExtensionHandle.collect(expected: Int = 1, action: () -> Unit): List<ExtensionEvent> {
        val queue = LinkedBlockingQueue<ExtensionEvent>()
        val subscription = events().subscribe().with { queue.add(it) }
        try {
            assertEquals(ExtensionEvent.SUBSCRIBED, queue.poll(10, TimeUnit.SECONDS)?.type)
            action()
            return List(expected) { checkNotNull(queue.poll(10, TimeUnit.SECONDS)) { "event #$it not received" } }
        } finally {
            subscription.cancel()
        }
    }
}
