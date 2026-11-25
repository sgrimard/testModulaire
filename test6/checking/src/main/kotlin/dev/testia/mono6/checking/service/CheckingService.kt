package dev.testia.mono6.checking.service

import dev.testia.mono6.checking.api.CheckEvent
import dev.testia.mono6.checking.api.CheckReport
import dev.testia.mono6.checking.api.CheckStatus
import dev.testia.mono6.checking.api.CheckingOperations
import dev.testia.mono6.checking.api.Finding
import dev.testia.mono6.checking.api.Severity
import dev.testia.mono6.checking.spi.CheckResult
import dev.testia.mono6.checking.spi.ProductCheck
import dev.testia.mono6.core.api.ProductDto
import dev.testia.mono6.core.api.ProductEvent
import dev.testia.mono6.core.api.ProductNotFoundException
import dev.testia.mono6.core.api.ProductOperations
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Implémentation « appel direct » du contrat ; c'est aussi elle que le REST délègue.
 * Lit les produits par le contrat de core-backend (appel direct) et agrège les contributions [ProductCheck].
 */
@Service
@Primary
class CheckingService(
    private val products: ProductOperations,
    checks: ObjectProvider<ProductCheck>,
) : CheckingOperations {
    private val log = LoggerFactory.getLogger(CheckingService::class.java)
    private val checks: List<ProductCheck> = checks.orderedStream().toList().sortedWith(compareBy({ it.order }, { it.name }))
    private val subscribers = CopyOnWriteArrayList<FluxSink<CheckEvent>>()

    override fun check(productId: Long): CheckReport = evaluate(products.get(productId))

    override fun checkAll(): List<CheckReport> = products.list().map(::evaluate)

    override fun events(): Flux<CheckEvent> = Flux.create { sink ->
        subscribers += sink
        sink.onDispose { subscribers -= sink }
        sink.next(CheckEvent(CheckEvent.SUBSCRIBED))
    }

    /** Le calcul est un pur assemblage : chaque contribution ajoute ses métriques et ses constats. */
    fun evaluate(product: ProductDto): CheckReport {
        val results = checks.map { check ->
            try {
                check.evaluate(product)
            } catch (e: RuntimeException) {
                // Une contribution défaillante n'empêche pas le calcul des autres.
                log.warn("Check {} failed for product {}", check.name, product.id, e)
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

    /**
     * Recalcul sur changement : checking écoute l'événement publié par core-backend (événement Spring
     * entre modules, déclaré dans le contrat `core::api`).
     *
     * Le rapport est calculé juste avant le commit, dans la transaction qui écrit : il reflète
     * exactement l'état validé par ce commit, même si d'autres modifications suivent aussitôt.
     * Il n'est diffusé qu'après un commit réussi, dans l'ordre des commits.
     * Contrepartie : le calcul allonge la transaction d'écriture.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onProductChange(event: ProductEvent) {
        val out = report(event) ?: return
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = subscribers.forEach { it.next(out) }
            },
        )
    }

    private fun report(event: ProductEvent): CheckEvent? {
        val id = event.productId ?: return null
        return when (event.type) {
            ProductEvent.DELETED -> CheckEvent(CheckEvent.REMOVED, id)
            else -> try {
                CheckEvent(CheckEvent.CHECKED, id, check(id))
            } catch (e: RuntimeException) {
                // Le recalcul ne doit jamais faire échouer l'écriture du noyau.
                log.error("Recompute failed for product {}", id, e)
                null
            }
        }
    }
}
