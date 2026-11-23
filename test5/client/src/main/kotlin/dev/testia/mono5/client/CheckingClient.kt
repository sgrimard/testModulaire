package dev.testia.mono5.client

import dev.testia.mono5.checking.api.CheckReport
import dev.testia.mono5.checking.api.CheckingOperations
import dev.testia.mono5.checking.rest.CheckingApi
import dev.testia.mono5.checking.service.CheckingService
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

/**
 * Client tierce partie : ne dépend que de checking. Le même code fonctionne en appel direct
 * et en REST, quel que soit l'ensemble des modules ext-checking-* présents dans l'assemblage.
 */
@ApplicationScoped
class CheckingClient(
    direct: CheckingService,
    @RestClient rest: CheckingApi,
) {
    enum class Mode { DIRECT, REST }

    private val operations: Map<Mode, CheckingOperations> = mapOf(Mode.DIRECT to direct, Mode.REST to rest)

    fun operations(mode: Mode): CheckingOperations = operations.getValue(mode)

    fun check(mode: Mode, productId: Long): CheckReport = operations(mode).check(productId)

    /** Rendu générique d'un rapport : piloté par les données, sans rien savoir des contributions. */
    fun render(report: CheckReport): String = buildString {
        appendLine("${report.product} : ${report.status} (score ${report.score}/100)")
        report.metrics.forEach { appendLine("  ${it.label} : ${it.value} ${it.unit}") }
        report.findings.forEach { appendLine("  [${it.severity}] ${it.message}" + if (it.penalty > 0) " (-${it.penalty})" else "") }
    }
}
