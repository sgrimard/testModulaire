package dev.testia.mono6.appcore

import dev.testia.mono6.checking.api.CheckEvent
import dev.testia.mono6.checking.api.CheckStatus
import dev.testia.mono6.client.CheckingClient
import dev.testia.mono6.client.CheckingClient.Mode
import dev.testia.mono6.core.api.ProductDto
import dev.testia.mono6.core.api.ProductOperations
import dev.testia.devdb.EmbeddedPostgresInitializer
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ContextConfiguration
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Noyau seul : aucune extension ; le même client fonctionne, avec le calcul de checking uniquement. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [EmbeddedPostgresInitializer::class])
class CoreOnlyTest {
    @Autowired
    lateinit var client: CheckingClient

    @Autowired
    lateinit var products: ProductOperations

    @LocalServerPort
    var port = 0

    @BeforeEach
    fun restAssured() {
        RestAssured.port = port
    }

    @Test
    fun `checking works with core data only`() {
        assertEquals(emptyList<String>(), products.extensions())
        val id = products.create(ProductDto(name = "Agrafeuse", price = BigDecimal("8.50"), category = "office", stock = 0)).id!!
        Mode.entries.forEach { mode ->
            val report = client.check(mode, id)
            assertEquals(listOf("core"), report.contributors, "$mode")
            assertEquals(listOf("core.stockValue"), report.metrics.map { it.code })
            assertEquals(CheckStatus.WARNING to 80, report.status to report.score)
            assertEquals(report, client.operations(mode).checkAll().single { it.productId == id })
        }
    }

    @Test
    fun `reports are pushed in both modes`() = Mode.entries.forEach { mode ->
        val queue = LinkedBlockingQueue<CheckEvent>()
        val subscription = client.operations(mode).events().subscribe { queue.add(it) }
        try {
            assertEquals(CheckEvent.SUBSCRIBED, queue.poll(10, TimeUnit.SECONDS)?.type)
            val id = products.create(ProductDto(name = "Classeur-$mode", price = BigDecimal("3.00"), category = "office", stock = 50)).id!!
            val event = generateSequence { queue.poll(10, TimeUnit.SECONDS) }.first { it.productId == id }
            assertEquals(CheckStatus.OK to BigDecimal("150.00"), event.report!!.status to event.report!!.metric("core.stockValue"), "$mode")
        } finally {
            subscription.dispose()
        }
    }

    @Test
    fun `rest endpoints`() {
        given().get("/api/products/extensions").then().statusCode(200).body("size()", equalTo(0))
        given().get("/api/checking/reports/424242").then().statusCode(404).body("code", equalTo("product-not-found"))
    }

    @Test
    fun `desktop ui may call the api from another origin`() {
        given().header("Origin", "app://desktop").get("/api/checking/reports").then().statusCode(200)
            .header("Access-Control-Allow-Origin", "app://desktop")
    }

    /** Écrit aussi le contrat dans build/openapi/openapi.json : source des types TypeScript (npm run api). */
    @Test
    fun `openapi contract of both bricks is published`() {
        val json = given().get("/v3/api-docs").then().statusCode(200)
            .body("paths.keySet()", hasItems("/api/products", "/api/checking/reports"))
            .extract().asString()
        Files.createDirectories(OPENAPI.parent)
        Files.writeString(OPENAPI, json)
    }

    private companion object {
        val OPENAPI: Path = Path.of("build/openapi/openapi.json")
    }
}
