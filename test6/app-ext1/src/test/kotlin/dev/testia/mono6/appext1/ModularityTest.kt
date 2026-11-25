package dev.testia.mono6.appext1

import dev.testia.mono6.PartialApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Règles d'architecture vérifiées par Spring Modulith, sans démarrer l'application :
 * pas de cycle, pas d'accès aux paquets internes d'un autre module, et seules les dépendances
 * déclarées dans les package-info.java (`allowedDependencies`) sont permises.
 */
class ModularityTest {
    private val modules = ApplicationModules.of(PartialApplication::class.java)

    @Test
    fun `modules respect their declared dependencies`() {
        modules.verify()
    }

    @Test
    fun `assembly contains the expected modules`() {
        assertEquals(EXPECTED, modules.map { it.identifier.toString() }.sorted())
    }

    private companion object {
        val EXPECTED = listOf("checking", "client", "core", "shipping", "shippingcheck")
    }
}
