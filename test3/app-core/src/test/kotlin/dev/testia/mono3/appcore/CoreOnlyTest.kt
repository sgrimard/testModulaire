package dev.testia.mono3.appcore

import dev.testia.mono3.client.ThirdPartyClient
import dev.testia.mono3.client.ThirdPartyClient.Mode
import dev.testia.mono3.core.api.ProductDto
import dev.testia.mono3.pg.EmbeddedPostgresResource
import io.quarkus.test.common.WithTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** Sans plug-in : les champs du noyau gardent leurs valeurs valides d'origine. */
@QuarkusTest
@WithTestResource(EmbeddedPostgresResource::class)
class CoreOnlyTest {
    @Inject
    lateinit var client: ThirdPartyClient

    private fun product(price: String = "10", category: String? = "office") =
        ProductDto(name = "p", price = BigDecimal(price), category = category)

    @Test
    fun `core constraints are the original ones`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        val schema = ops.schema().associateBy { it.key }
        assertEquals(listOf("name", "description", "price", "category"), schema.keys.toList())
        assertEquals(listOf("office", "tools", "garden"), schema.getValue("category").options!!.map { it.value })
        assertEquals(0.0 to 100_000.0, schema.getValue("price").let { it.min to it.max }, "$mode")
        // le contexte ne change rien sans plug-in
        assertEquals(ops.schema(), ops.schemaFor(product()))

        // ce que le plug-in interdirait (prix 0 ou 6000) est accepté, ce qu'il ajouterait est refusé
        assertEquals(emptyList<Any>(), ops.validate(product(price = "0")))
        assertEquals(emptyList<Any>(), ops.validate(product(price = "6000")))
        assertEquals(listOf("category"), ops.validate(product(category = "chemicals")).map { it.field })
        assertEquals(listOf("price"), ops.validate(product(price = "-1")).map { it.field })

        val created = ops.create(product(price = "6000", category = "garden"))
        assertEquals("garden", ops.get(created.id!!).category)
        assertEquals("p (6000.00 €)", ops.label(created.id!!))
    }

    @Test
    fun `no ui plugin`() {
        given().get("/ui/plugins").then().statusCode(200).body("", empty<Any>())
    }
}
