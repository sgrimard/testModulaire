package dev.testia.mono4.app

import dev.testia.mono4.checking.api.CheckEvent
import dev.testia.mono4.checking.api.CheckReport
import dev.testia.mono4.checking.api.CheckStatus
import dev.testia.mono4.client.CheckingClient
import dev.testia.mono4.client.CheckingClient.Mode
import dev.testia.mono4.core.api.ProductDto
import dev.testia.mono4.core.api.ProductEvent
import dev.testia.mono4.core.api.ProductNotFoundException
import dev.testia.mono4.core.api.ProductOperations
import dev.testia.mono4.core.api.ProductValidationException
import dev.testia.mono4.core.api.UnknownExtensionException
import dev.testia.mono4.core.api.extension
import dev.testia.mono4.core.rest.ProductApi
import dev.testia.mono4.core.service.ProductService
import dev.testia.mono4.pg.EmbeddedPostgresResource
import io.quarkus.test.common.WithTestResource
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.Multi
import jakarta.inject.Inject
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Assemblage complet : deux extensions de données, trois contributions au calcul. Direct ET REST. */
@QuarkusTest
@WithTestResource(EmbeddedPostgresResource::class)
class FullAssemblyTest {
    @Inject
    lateinit var client: CheckingClient

    @Inject
    lateinit var directProducts: ProductService

    @Inject
    @RestClient
    lateinit var restProducts: ProductApi

    private fun products(mode: Mode): ProductOperations = if (mode == Mode.DIRECT) directProducts else restProducts

    private fun product(
        name: String,
        price: String,
        stock: Int = 10,
        category: String = "tools",
        weightKg: Double? = 2.0,
        hazardous: Boolean = false,
        unitCost: Double? = null,
        leadTimeDays: Int? = null,
    ) = ProductDto(
        name = name, price = BigDecimal(price), category = category, stock = stock,
        extensions = mapOf(
            "shipping" to mapOf("weightKg" to weightKg, "hazardous" to hazardous),
            "supplier" to mapOf("supplier" to "ACME", "unitCost" to unitCost, "leadTimeDays" to leadTimeDays),
        ),
    )

    private fun CheckReport.value(code: String) = metric(code)?.toDouble()
    private fun CheckReport.rules() = findings.map { it.rule }

    @Test
    fun `one report combines core and every contribution, without the client knowing them`() = Mode.entries.forEach { mode ->
        val id = products(mode).create(product("Perceuse-$mode", "100.00", unitCost = 50.0)).id!!
        val report = client.check(mode, id)

        assertEquals(listOf("core", "shipping", "supplier", "landed"), report.contributors, "$mode")
        assertEquals(1000.0, report.value("core.stockValue"))        // checking : core-backend seul
        assertEquals(3.0, report.value("shipping.costPerUnit"))      // ext-checking-1 : 2 kg × 1,50
        assertEquals(50.0, report.value("supplier.grossMargin"))     // ext-checking-2 : 100 − 50
        assertEquals(53.0, report.value("landed.cost"))              // ext-checking-3 : 50 + 3
        assertEquals(47.0, report.value("landed.netMarginRate"))
        assertEquals(CheckStatus.OK to 100, report.status to report.score)
        assertEquals(report, client.check(Mode.entries.first { it != mode }, id), "même rapport dans l'autre mode")
    }

    @Test
    fun `cross-extension contribution finds what single-extension ones cannot`() = Mode.entries.forEach { mode ->
        // marge brute positive (2 €), mais le transport (3 €) la fait passer sous zéro
        val id = products(mode).create(product("Cisaille-$mode", "20.00", unitCost = 18.0)).id!!
        val report = client.check(mode, id)

        assertEquals(listOf("supplier.low-margin", "landed.margin-absorbed"), report.rules(), "$mode")
        assertEquals(-1.0, report.value("landed.netMargin"))
        assertEquals(CheckStatus.BLOCKING to 45, report.status to report.score)
    }

    @Test
    fun `findings from every module add up`() = Mode.entries.forEach { mode ->
        val id = products(mode).create(
            product("Solvant-$mode", "10.00", stock = 0, category = "office", weightKg = null, hazardous = true, leadTimeDays = 45),
        ).id!!
        val report = client.check(mode, id)
        assertEquals(
            listOf(
                "core.out-of-stock", "shipping.hazardous-category", "shipping.unknown-weight",
                "supplier.restock-risk", "supplier.unknown-cost",
            ),
            report.rules(), "$mode",
        )
        assertEquals(CheckStatus.BLOCKING to 10, report.status to report.score) // 100 − (20 + 40 + 10 + 10 + 10)
    }

    @Test
    fun `core-backend extensions are discovered and used by name`() = Mode.entries.forEach { mode ->
        val ops = products(mode)
        assertEquals(listOf("shipping", "supplier"), ops.extensions(), "$mode")
        assertThrows<UnknownExtensionException> { ops.extension("nope") }

        val id = ops.create(product("Pelle-$mode", "30.00")).id!!
        val supplier = ops.extension("supplier")
        assertEquals(listOf("supplier", "unitCost", "leadTimeDays"), supplier.fields().map { it.name })
        assertEquals(mapOf("supplier" to "Bricomax", "unitCost" to 12.5, "leadTimeDays" to 7.0),
            supplier.put(id, mapOf("supplier" to "Bricomax", "unitCost" to 12.5, "leadTimeDays" to 7)))
        assertEquals(12.5, ops.get(id).extensions["supplier"]?.get("unitCost"))
        assertEquals(2.0, ops.extension("shipping").get(id)["weightKg"], "le put n'a touché qu'une extension")

        val invalid = assertThrows<ProductValidationException> { supplier.put(id, mapOf("unitCost" to -1)) }
        assertEquals(listOf("supplier.unitCost"), invalid.violations.map { it.field })
        assertThrows<ProductNotFoundException> { supplier.get(999_999) }
        assertThrows<ProductNotFoundException> { client.check(mode, 999_999) }
    }

    @Test
    fun `subscribers to an extension only see its changes`() = Mode.entries.forEach { mode ->
        val ops = products(mode)
        val id = ops.create(product("Râteau-$mode", "15.00")).id!!
        val events = ops.extension("shipping").events().collect(ProductEvent.SUBSCRIBED, keep = { it.productId == id }) {
            ops.extension("supplier").put(id, mapOf("unitCost" to 5.0))
            ops.extension("shipping").put(id, mapOf("weightKg" to 1.0))
        }
        assertEquals(ProductEvent(ProductEvent.UPDATED, id, "shipping"), events.single(), "$mode")
    }

    @Test
    fun `reports are recomputed and pushed when data changes`() = Mode.entries.forEach { mode ->
        val ops = products(mode)
        val name = "Tondeuse-$mode"
        var id = 0L
        val events = client.operations(mode).events().collect(
            CheckEvent.SUBSCRIBED,
            expected = 4,
            keep = { it.report?.product == name || (it.type == CheckEvent.REMOVED && it.productId == id) },
        ) {
            id = ops.create(product(name, "200.00", unitCost = 100.0)).id!!
            ops.extension("supplier").put(id, mapOf("unitCost" to 250.0))   // ext-backend-2 seul
            ops.extension("shipping").put(id, mapOf("weightKg" to 4.0))     // ext-backend-1 seul
            ops.delete(id)
        }

        assertEquals(listOf("checked", "checked", "checked", "removed"), events.map { it.type }, "$mode")
        assertEquals(100.0, events[0].report!!.value("supplier.grossMargin"))
        assertEquals(-50.0, events[1].report!!.value("supplier.grossMargin"))
        assertEquals(CheckStatus.BLOCKING, events[1].report!!.status)
        assertEquals(6.0, events[2].report!!.value("shipping.costPerUnit"))
        assertEquals(256.0, events[2].report!!.value("landed.cost"))
    }

    /** S'abonne, attend l'accusé [subscribed], exécute [action], puis renvoie les [expected] éléments suivants. */
    private fun <T : Any> Multi<T>.collect(
        subscribed: String,
        expected: Int = 1,
        keep: (T) -> Boolean = { true },
        action: () -> Unit,
    ): List<T> {
        val queue = LinkedBlockingQueue<T>()
        val subscription = subscribe().with { queue.add(it) }
        try {
            assertEquals(subscribed, queue.poll(10, TimeUnit.SECONDS)?.let(::typeOf))
            action()
            return buildList {
                while (size < expected) {
                    val next = checkNotNull(queue.poll(10, TimeUnit.SECONDS)) { "event #$size not received (got $this)" }
                    if (keep(next)) add(next)
                }
            }
        } finally {
            subscription.cancel()
        }
    }

    private fun typeOf(event: Any) = when (event) {
        is ProductEvent -> event.type
        is CheckEvent -> event.type
        else -> error("unexpected $event")
    }
}
