package dev.testia.mono4.checking.service

import dev.testia.mono4.checking.api.CheckEvent
import dev.testia.mono4.checking.api.CheckReport
import dev.testia.mono4.checking.api.CheckStatus
import dev.testia.mono4.checking.api.CheckingOperations
import dev.testia.mono4.checking.api.Finding
import dev.testia.mono4.checking.api.Severity
import dev.testia.mono4.checking.spi.CheckResult
import dev.testia.mono4.checking.spi.ProductCheck
import dev.testia.mono4.core.api.ProductDto
import dev.testia.mono4.core.api.ProductEvent
import dev.testia.mono4.core.api.ProductNotFoundException
import dev.testia.mono4.core.service.ProductService
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.infrastructure.Infrastructure
import io.smallrye.mutiny.subscription.Cancellable
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Instance
import org.jboss.logging.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Implémentation « appel direct » du contrat ; c'est aussi elle que le REST délègue.
 * Lit les produits via core-backend en appel direct et agrège les contributions [ProductCheck].
 */
@ApplicationScoped
class CheckingService(
    private val products: ProductService,
    checks: Instance<ProductCheck>,
) : CheckingOperations {
    private val log = Logger.getLogger(CheckingService::class.java)
    private val checks: List<ProductCheck> = checks.sortedWith(compareBy({ it.order }, { it.name }))
    private val subscribers = CopyOnWriteArrayList<MultiEmitter<in CheckEvent>>()
    private var productChanges: Cancellable? = null

    override fun check(productId: Long): CheckReport = evaluate(products.get(productId))

    override fun checkAll(): List<CheckReport> = products.list().map(::evaluate)

    override fun events(): Multi<CheckEvent> = Multi.createFrom().emitter { emitter ->
        subscribers += emitter
        emitter.onTermination { subscribers -= emitter }
        emitter.emit(CheckEvent(CheckEvent.SUBSCRIBED))
    }

    /** Le calcul est un pur assemblage : chaque contribution ajoute ses métriques et ses constats. */
    fun evaluate(product: ProductDto): CheckReport {
        val results = checks.map { check ->
            try {
                check.evaluate(product)
            } catch (e: RuntimeException) {
                // Une contribution défaillante n'empêche pas le calcul des autres.
                log.warnf(e, "Check %s failed for product %d", check.name, product.id)
                CheckResult(findings = listOf(Finding("${check.name}.error", Severity.WARNING, "Contrôle indisponible", 0)))
            }
        }
        val findings = results.flatMap { it.findings }
        return CheckReport(
            productId = product.id!!,
            product = product.name,
            score = (100 - findings.sumOf { it.penalty }).coerceAtLeast(0),
            status = when {
                findings.any { it.severity == Severity.BLOCKING } -> CheckStatus.BLOCKING
                findings.any { it.severity == Severity.WARNING } -> CheckStatus.WARNING
                else -> CheckStatus.OK
            },
            metrics = results.flatMap { it.metrics },
            findings = findings,
            contributors = checks.map { it.name },
        )
    }

    // --- recalcul sur changement : checking s'abonne à core-backend comme n'importe quel client ---

    fun onStart(@Observes event: StartupEvent) {
        productChanges = products.events()
            .emitOn(Infrastructure.getDefaultWorkerPool()) // hors du thread qui a validé la transaction
            .subscribe().with(
                { event: ProductEvent -> onProductChange(event) },
                { failure: Throwable -> log.error("Product event stream failed", failure) },
            )
    }

    fun onStop(@Observes event: ShutdownEvent) {
        productChanges?.cancel()
    }

    private fun onProductChange(event: ProductEvent) {
        val id = event.productId ?: return
        val out = when (event.type) {
            ProductEvent.DELETED -> CheckEvent(CheckEvent.REMOVED, id)
            else -> try {
                CheckEvent(CheckEvent.CHECKED, id, check(id))
            } catch (_: ProductNotFoundException) {
                return // supprimé entre-temps : l'événement « deleted » suit
            }
        }
        subscribers.forEach { it.emit(out) }
    }
}
