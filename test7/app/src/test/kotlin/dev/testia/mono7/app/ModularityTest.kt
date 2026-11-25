package dev.testia.mono7.app

import dev.testia.mono7.WithPluginApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * Règles d'architecture vérifiées par Spring Modulith, sans démarrer l'application :
 * pas de cycle, pas d'accès aux paquets internes d'un autre module, et seules les dépendances
 * déclarées dans les package-info.java (`allowedDependencies`) sont permises.
 */
class ModularityTest {
    private val modules = ApplicationModules.of(WithPluginApplication::class.java)

    @Test
    fun `modules respect their declared dependencies`() {
        modules.verify()
    }

    @Test
    fun `assembly contains the expected modules`() {
        assertEquals(listOf("client", "core", "shipping", "ui"), modules.map { it.identifier.toString() }.sorted())
    }

    /** Diagrammes C4 / PlantUML et fiches des modules : build/spring-modulith-docs. */
    @Test
    fun `module documentation`() {
        Documenter(modules).writeDocumentation()
    }
}
