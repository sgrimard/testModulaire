package dev.testia.mono2.app

import dev.testia.mono2.client.ThirdPartyClient
import dev.testia.mono2.client.ThirdPartyClient.Mode
import dev.testia.mono2.core.api.ProductDto
import dev.testia.mono2.core.api.ProductNotFoundException
import dev.testia.mono2.core.api.ProductValidationException
import dev.testia.mono2.pg.EmbeddedPostgresResource
import io.quarkus.test.common.WithTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import javax.sql.DataSource

/** Assemblage complet : core + ext (backend et frontend). Chaque scénario est joué en direct ET en REST. */
@QuarkusTest
@WithTestResource(EmbeddedPostgresResource::class)
class WithPluginTest {
    @Inject
    lateinit var client: ThirdPartyClient

    @Inject
    lateinit var dataSource: DataSource


    private fun product(name: String, weight: Double? = 2.5, hazardous: Boolean? = false) = ProductDto(
        name = name, description = "desc", price = BigDecimal("12.50"),
        extensions = mapOf("shipping" to mapOf("weightKg" to weight, "hazardous" to hazardous)),
    )

    @Test
    fun `schema exposes core and plugin fields`() = Mode.entries.forEach { mode ->
        val keys = client.operations(mode).schema().map { it.key }
        assertEquals(listOf("name", "description", "price", "shipping.weightKg", "shipping.hazardous"), keys, "$mode")
    }

    @Test
    fun `crud keeps plugin fields, stored in the plugin table`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        val created = ops.create(product("crud-$mode"))
        val id = created.id!!
        assertEquals(mapOf("shipping" to mapOf("weightKg" to 2.5, "hazardous" to false)), created.extensions)
        assertEquals(BigDecimal("12.50"), created.price)
        assertEquals(created, ops.get(id))
        assertEquals(2.5, shippingRow(id)?.first)

        val updated = ops.update(id, product("crud-$mode-bis", weight = 4.0, hazardous = true))
        assertEquals("crud-$mode-bis", updated.name)
        assertEquals(4.0 to true, shippingRow(id))
        assertTrue(ops.list().any { it.id == id && it.extensions == updated.extensions })

        ops.delete(id)
        assertThrows<ProductNotFoundException>("$mode") { ops.get(id) }
        assertEquals(null, shippingRow(id))
    }

    @Test
    fun `generic third-party export sees plugin fields without knowing them`() = Mode.entries.forEach { mode ->
        val id = client.operations(mode).create(product("export-$mode", weight = 1.0)).id!!
        val export = client.export(mode, id)
        assertEquals(1.0, export["shipping.weightKg"])
        assertEquals("export-$mode", export["name"])
    }

    @Test
    fun `validation combines generic, core and plugin rules`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        // règle du plug-in : dangereux => poids obligatoire
        assertEquals(listOf("shipping.weightKg"), ops.validate(product("v", weight = null, hazardous = true)).map { it.field })
        // règle du noyau + règle du plug-in dans la même réponse
        assertEquals(
            setOf("name", "shipping.weightKg"),
            ops.validate(product(" ", weight = -1.0)).map { it.field }.toSet(),
        )
        // contrôles génériques pilotés par le schéma
        val generic = ops.validate(ProductDto(name = "g", extensions = mapOf(
            "shipping" to mapOf("weightKg" to "lourd", "colour" to "red"),
            "unknown" to mapOf("x" to 1),
        ))).map { it.field }.toSet()
        assertEquals(setOf("shipping.weightKg", "shipping.colour", "unknown"), generic, "$mode")

        val ex = assertThrows<ProductValidationException> { ops.create(product("bad", weight = null, hazardous = true)) }
        assertEquals("shipping.weightKg", ex.violations.single().field)
    }

    @Test
    fun `quote pipeline is specialised by the plugin`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        val id = ops.create(product("quote-$mode", weight = 2.0, hazardous = true)).id!!
        val quote = ops.quote(id, 3)
        assertEquals(listOf("base", "shipping", "hazmat"), quote.lines.map { it.code })
        // 12,50 × 3 + 2 kg × 3 × 1,50 + 20
        assertEquals(BigDecimal("66.50"), quote.total)
        assertThrows<ProductValidationException> { ops.quote(id, 0) }
    }

    @Test
    fun `label implementation is replaced by the plugin`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        val id = ops.create(product("Bidon", weight = 5.0, hazardous = true)).id!!
        assertEquals("Bidon (12.50 €) – 5.0 kg ⚠", ops.label(id))
    }

    @Test
    fun `ui discovers the plugin module and serves it`() {
        given().get("/ui/plugins").then().statusCode(200)
            .body("id", equalTo(listOf("shipping")))
            .body("module", equalTo(listOf("/plugins/shipping/plugin.js")))
        given().get("/plugins/shipping/plugin.js").then().statusCode(200).body(containsString("export function register"))
        given().get("/").then().statusCode(200).body(containsString("/core/app.js"))
    }

    private fun shippingRow(id: Long): Pair<Double?, Boolean?>? = dataSource.connection.use { c ->
        c.prepareStatement("select weight_kg, hazardous from shipping_product where product_id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getObject(1) as Double? to rs.getObject(2) as Boolean? else null
            }
        }
    }
}
