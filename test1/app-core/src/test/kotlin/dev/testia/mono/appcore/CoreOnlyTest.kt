package dev.testia.mono.appcore

import dev.testia.mono.client.ClientFacade
import dev.testia.mono.client.ClientFacade.Mode
import dev.testia.mono.server.UnknownExtensionException
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@QuarkusTest
class CoreOnlyTest {
    @Inject
    lateinit var client: ClientFacade

    @Test
    fun `same client works without any extension`() {
        Mode.entries.forEach { mode ->
            assertEquals(emptyList<String>(), client.gateway(mode).names())
            assertThrows<UnknownExtensionException> { client.gateway(mode).extension("notes") }
        }
    }
}
