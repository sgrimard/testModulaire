package dev.testia.mono6.appext1

import dev.testia.mono6.checking.api.CheckStatus
import dev.testia.mono6.client.CheckingClient
import dev.testia.mono6.client.CheckingClient.Mode
import dev.testia.mono6.core.api.ProductDto
import dev.testia.mono6.core.api.ProductOperations
import dev.testia.mono6.core.api.ProductValidationException
import dev.testia.mono6.core.api.UnknownExtensionException
import dev.testia.mono6.core.api.extension
import dev.testia.devdb.EmbeddedPostgresInitializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.test.context.ContextConfiguration
import java.math.BigDecimal

/**
 * Assemblage partiel : ext-backend-1 + ext-checking-1 seulement. ext-checking-3 ne peut pas y figurer
 * (il lui faut ext-backend-2) ; le client, inchangé, reçoit un rapport réduit.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [EmbeddedPostgresInitializer::class])
class PartialAssemblyTest {
    @Autowired
    lateinit var client: CheckingClient

    @Autowired
    lateinit var products: ProductOperations

    @Test
    fun `report only contains the contributions present`() {
        val id = products.create(
            ProductDto(
                name = "Bêche", price = BigDecimal("10.00"), category = "garden", stock = 3,
                extensions = mapOf("shipping" to mapOf("weightKg" to 4.0, "hazardous" to false)),
            ),
        ).id!!
        Mode.entries.forEach { mode ->
            val report = client.check(mode, id)
            assertEquals(listOf("core", "shipping"), report.contributors, "$mode")
            assertEquals(listOf("core.stockValue", "shipping.costPerUnit", "shipping.shareOfPrice"), report.metrics.map { it.code })
            // stock faible (−5) + transport = 60 % du prix (−15)
            assertEquals(listOf("core.low-stock", "shipping.expensive"), report.findings.map { it.rule })
            assertEquals(CheckStatus.WARNING to 80, report.status to report.score)
            assertEquals("Bêche : WARNING (score 80/100)", client.render(report).lines().first())
        }
    }

    @Test
    fun `absent extension is unknown to core-backend`() {
        assertEquals(listOf("shipping"), products.extensions())
        assertThrows<UnknownExtensionException> { products.extension("supplier") }
        val ex = assertThrows<ProductValidationException> {
            products.create(
                ProductDto(
                    name = "x", price = BigDecimal.ONE, category = "tools",
                    extensions = mapOf("supplier" to mapOf("unitCost" to 1.0)),
                ),
            )
        }
        assertEquals("supplier" to "Extension inconnue", ex.violations.single().let { it.field to it.message })
    }
}
