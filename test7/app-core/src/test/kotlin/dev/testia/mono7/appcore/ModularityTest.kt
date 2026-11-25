package dev.testia.mono7.appcore

import dev.testia.mono7.CoreApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Règles d'architecture vérifiées par Spring Modulith, sans démarrer l'application :
 * pas de cycle, pas d'accès aux paquets internes d'un autre module, et seules les dépendances
 * déclarées dans les package-info.java (`allowedDependencies`) sont permises.
 */
class ModularityTest {
    private val modules = ApplicationModules.of(CoreApplication::class.java)

    @Test
    fun `modules respect their declared dependencies`() {
        modules.verify()
    }

    @Test
    fun `assembly contains the expected modules`() {
        assertEquals(listOf("client", "core", "ui"), modules.map { it.identifier.toString() }.sorted())
    }
}
