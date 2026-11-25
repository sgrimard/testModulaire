package dev.testia.mono6.client

import dev.testia.mono6.checking.api.CheckReport
import dev.testia.mono6.checking.api.CheckingOperations
import dev.testia.mono6.checking.rest.checkingApiClient
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Client tierce partie : ne dépend que de checking. Le même code fonctionne en appel direct
 * et en REST, quel que soit l'ensemble des modules ext-checking-* présents dans l'assemblage.
 */
@Component
class CheckingClient(
    private val direct: CheckingOperations,
    private val env: Environment,
) {
    enum class Mode { DIRECT, REST }

    /**
     * Serveur REST : `checking-api.url`, sinon l'application elle-même. Résolu au premier appel,
     * quand le port réel (`local.server.port`) est connu.
     */
    private val rest: CheckingOperations by lazy {
        checkingApiClient(env.getProperty("checking-api.url") ?: "http://localhost:${env.getRequiredProperty("local.server.port")}")
    }

    fun operations(mode: Mode): CheckingOperations = if (mode == Mode.DIRECT) direct else rest

    fun check(mode: Mode, productId: Long): CheckReport = operations(mode).check(productId)

    /** Rendu générique d'un rapport : piloté par les données, sans rien savoir des contributions. */
    fun render(report: CheckReport): String = buildString {
        appendLine("${report.product} : ${report.status} (score ${report.score}/100)")
        report.metrics.forEach { appendLine("  ${it.label} : ${it.value} ${it.unit}") }
        report.findings.forEach { appendLine("  [${it.severity}] ${it.message}" + if (it.penalty > 0) " (-${it.penalty})" else "") }
    }
}
