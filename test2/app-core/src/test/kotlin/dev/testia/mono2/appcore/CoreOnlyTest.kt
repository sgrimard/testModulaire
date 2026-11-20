package dev.testia.mono2.appcore

import dev.testia.mono2.client.ThirdPartyClient
import dev.testia.mono2.client.ThirdPartyClient.Mode
import dev.testia.mono2.core.api.ProductDto
import dev.testia.mono2.core.api.ProductValidationException
import dev.testia.mono2.pg.EmbeddedPostgresResource
import io.quarkus.test.common.WithTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import javax.sql.DataSource

/** Même client, même noyau, aucun plug-in : tout fonctionne, les traitements gardent leur forme par défaut. */
@QuarkusTest
@WithTestResource(EmbeddedPostgresResource::class)
class CoreOnlyTest {
    @Inject
    lateinit var client: ThirdPartyClient

    @Inject
    lateinit var dataSource: DataSource


    @Test
    fun `core works alone in both modes`() = Mode.entries.forEach { mode ->
        val ops = client.operations(mode)
        assertEquals(listOf("name", "description", "price"), ops.schema().map { it.key })

        val created = ops.create(ProductDto(name = "core-$mode", price = BigDecimal("10")))
        assertEquals(emptyMap<String, Any>(), created.extensions)
        assertEquals(listOf("base"), ops.quote(created.id!!, 2).lines.map { it.code })
        assertEquals(BigDecimal("20.00"), ops.quote(created.id!!, 2).total)
        assertEquals("core-$mode (10.00 €)", ops.label(created.id!!))

        // Données d'un plug-in absent : refusées explicitement plutôt qu'ignorées.
        val ex = assertThrows<ProductValidationException> {
            ops.create(ProductDto(name = "x", extensions = mapOf("shipping" to mapOf("weightKg" to 1.0))))
        }
        assertEquals("shipping", ex.violations.single().field)
    }

    @Test
    fun `no plugin table and no ui plugin`() {
        val tables = dataSource.connection.use { c ->
            c.createStatement().use { s ->
                s.executeQuery("select table_name from information_schema.tables where table_schema = 'public' order by 1")
                    .use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
        }
        assertEquals(listOf("product", "schema_history"), tables)
        given().get("/ui/plugins").then().statusCode(200).body("", empty<Any>())
    }
}
