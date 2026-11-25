package dev.testia.mono7.app

import dev.testia.mono7.client.ThirdPartyClient
import dev.testia.mono7.client.ThirdPartyClient.Mode
import dev.testia.mono7.core.api.FieldDescriptor
import dev.testia.mono7.core.api.ProductDto
import dev.testia.mono7.core.api.ProductNotFoundException
import dev.testia.mono7.core.api.ProductOperations
import dev.testia.mono7.core.api.ProductValidationException
import dev.testia.devdb.EmbeddedPostgresInitializer
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ContextConfiguration
import java.math.BigDecimal
import javax.sql.DataSource

/** Assemblage complet : le plug-in modifie les valeurs valides de champs du noyau. Direct ET REST. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [EmbeddedPostgresInitializer::class])
class WithPluginTest {
    @Autowired
    lateinit var client: ThirdPartyClient

    @LocalServerPort
    var port = 0

    @BeforeEach
    fun restAssured() {
        RestAssured.port = port
    }

    @Autowired
    lateinit var dataSource: DataSource

    private fun product(
        name: String,
        price: String = "12.50",
        category: String? = "tools",
        weight: Double? = 2.5,
        hazardous: Boolean? = false,
    ) = ProductDto(
        name = name, description = "desc", price = BigDecimal(price), category = category,
        extensions = mapOf("shipping" to mapOf("weightKg" to weight, "hazardous" to hazardous)),
    )

    private fun List<FieldDescriptor>.field(key: String) = single { it.key == key }
    private fun ProductOperations.invalidFields(p: ProductDto) = validate(p).map { it.field }.toSet()

    @Test
    fun `plugin changes the valid values of core fields in the schema`() = Mode.entries.forEach { mode ->
        val schema = client.operations(mode).schema()
        assertEquals(
            listOf("name", "description", "price", "category", "shipping.weightKg", "shipping.hazardous"),
            schema.map { it.key },
        )
        // liste déroulante du noyau : une valeur ajoutée par le plug-in
        assertEquals(listOf("office", "tools", "garden", "chemicals"), schema.field("category").options!!.map { it.value })
        // bornes du noyau [0 ; 100 000] resserrées par le plug-in
        assertEquals(0.01 to 5_000.0, schema.field("price").let { it.min to it.max }, "$mode")
        assertEquals(false, schema.field("shipping.weightKg").required)
    }

    @Test
    fun `constraints can depend on the draft being edited`() = Mode.entries.forEach { mode ->
        val schema = client.operations(mode).schemaFor(product("d", hazardous = true))
        assertEquals(listOf("tools", "chemicals"), schema.field("category").options!!.map { it.value }, "$mode")
        assertEquals(true, schema.field("shipping.weightKg").required)
    }

    @Test
    fun `validation follows the modified constraints`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        assertEquals(emptySet<String>(), ops.invalidFields(product("ok", category = "chemicals")), "valeur ajoutée")
        assertEquals(setOf("category"), ops.invalidFields(product("x", category = "toys")), "valeur inconnue")
        assertEquals(setOf("category"), ops.invalidFields(product("x", category = null)), "obligatoire")
        assertEquals(setOf("price"), ops.invalidFields(product("x", price = "0")), "borne min du plug-in")
        assertEquals(setOf("price"), ops.invalidFields(product("x", price = "6000")), "borne max du plug-in")
        assertEquals(setOf("shipping.weightKg"), ops.invalidFields(product("x", weight = -1.0)), "borne du champ du plug-in")
        // contraintes contextuelles
        assertEquals(setOf("category"), ops.invalidFields(product("x", category = "office", hazardous = true)))
        assertEquals(setOf("shipping.weightKg"), ops.invalidFields(product("x", weight = null, hazardous = true)))
        // plusieurs violations dans la même réponse
        assertEquals(setOf("name", "price", "category"), ops.invalidFields(product(" ", price = "6000", category = "toys")))
        // contrôles de forme toujours actifs
        val generic = ops.invalidFields(ProductDto(name = "g", category = "tools", extensions = mapOf(
            "shipping" to mapOf("weightKg" to "lourd", "colour" to "red"),
            "unknown" to mapOf("x" to 1),
        )))
        assertEquals(setOf("shipping.weightKg", "shipping.colour", "unknown"), generic, "$mode")

        val ex = assertThrows<ProductValidationException> { ops.create(product("bad", price = "6000")) }
        assertEquals("Doit être ≤ 5000", ex.violations.single().message)
    }

    @Test
    fun `crud keeps core and plugin fields`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        val created = ops.create(product("crud-$mode", category = "chemicals"))
        val id = created.id!!
        assertEquals("chemicals", created.category)
        assertEquals(mapOf("shipping" to mapOf("weightKg" to 2.5, "hazardous" to false)), created.extensions)
        assertEquals(created, ops.get(id))
        assertEquals(2.5, shippingRow(id)?.first)

        val updated = ops.update(id, product("crud-$mode-bis", category = "tools", weight = 4.0, hazardous = true))
        assertEquals("tools", updated.category)
        assertEquals(4.0 to true, shippingRow(id))
        assertTrue(ops.list().any { it.id == id && it == updated })

        ops.delete(id)
        assertThrows<ProductNotFoundException>("$mode") { ops.get(id) }
        assertEquals(null, shippingRow(id))
    }

    @Test
    fun `generic third-party export sees all fields without knowing the plugin`() = Mode.entries.forEach { mode ->
        val id = client.operations(mode).create(product("export-$mode", weight = 1.0)).id!!
        val export = client.export(mode, id)
        assertEquals(1.0, export["shipping.weightKg"])
        assertEquals("tools", export["category"])
    }

    @Test
    fun `quote and label stay specialised by the plugin`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        val id = ops.create(product("Bidon", category = "chemicals", weight = 2.0, hazardous = true)).id!!
        assertEquals(listOf("base", "shipping", "hazmat"), ops.quote(id, 3).lines.map { it.code })
        assertEquals(BigDecimal("66.50"), ops.quote(id, 3).total)
        assertEquals("Bidon (12.50 €) – 2.0 kg ⚠", ops.label(id))
    }

    @Test
    fun `ui gets the contextual schema over http`() {
        given().contentType("application/json")
            .body("""{"name":"x","extensions":{"shipping":{"hazardous":true}}}""")
            .post("/api/products/schema").then().statusCode(200)
            .body("find { it.key == 'category' }.options.value", equalTo(listOf("tools", "chemicals")))
            .body("find { it.key == 'shipping.weightKg' }.required", equalTo(true))
        given().get("/ui/plugins").then().statusCode(200).body("id", equalTo(listOf("shipping")))
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
