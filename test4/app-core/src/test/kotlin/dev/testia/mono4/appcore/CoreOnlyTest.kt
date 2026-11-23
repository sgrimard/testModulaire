package dev.testia.mono4.appcore

import dev.testia.mono4.checking.api.CheckEvent
import dev.testia.mono4.checking.api.CheckStatus
import dev.testia.mono4.client.CheckingClient
import dev.testia.mono4.client.CheckingClient.Mode
import dev.testia.mono4.core.api.ProductDto
import dev.testia.mono4.core.service.ProductService
import dev.testia.mono4.pg.EmbeddedPostgresResource
import io.quarkus.test.common.WithTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Noyau seul : aucune extension ; le même client fonctionne, avec le calcul de checking uniquement. */
@QuarkusTest
@WithTestResource(EmbeddedPostgresResource::class)
class CoreOnlyTest {
    @Inject
    lateinit var client: CheckingClient

    @Inject
    lateinit var products: ProductService

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
        val subscription = client.operations(mode).events().subscribe().with { queue.add(it) }
        try {
            assertEquals(CheckEvent.SUBSCRIBED, queue.poll(10, TimeUnit.SECONDS)?.type)
            val id = products.create(ProductDto(name = "Classeur-$mode", price = BigDecimal("3.00"), category = "office", stock = 50)).id!!
            val event = generateSequence { queue.poll(10, TimeUnit.SECONDS) }.first { it.productId == id }
            assertEquals(CheckStatus.OK to BigDecimal("150.00"), event.report!!.status to event.report!!.metric("core.stockValue"), "$mode")
        } finally {
            subscription.cancel()
        }
    }

    @Test
    fun `rest endpoints`() {
        given().get("/api/products/extensions").then().statusCode(200).body("size()", equalTo(0))
        given().get("/api/checking/reports/424242").then().statusCode(404).body("code", equalTo("product-not-found"))
    }

    @Test
    fun `ui of each brick is assembled at runtime`() {
        given().get("/").then().statusCode(200).body(containsString("/core/app.js"))
        given().get("/ui/plugins").then().statusCode(200).body("id", equalTo(listOf("checking")))
        given().get("/plugins/checking/plugin.js").then().statusCode(200).body(containsString("export function register"))
    }
}
