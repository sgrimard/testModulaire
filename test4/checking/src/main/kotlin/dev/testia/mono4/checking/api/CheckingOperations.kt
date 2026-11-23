package dev.testia.mono4.checking.api

import io.smallrye.mutiny.Multi
import java.math.BigDecimal

/**
 * Contrat public de checking, identique en appel direct ([dev.testia.mono4.checking.service.CheckingService])
 * et en REST ([dev.testia.mono4.checking.rest.CheckingApi]).
 *
 * Le calcul part des données de core-backend ; les modules ext-checking-* le complètent sans que
 * l'appelant ait à les connaître : il reçoit un seul rapport, quel que soit l'assemblage.
 */
interface CheckingOperations {
    /** @throws dev.testia.mono4.core.api.ProductNotFoundException */
    fun check(productId: Long): CheckReport

    fun checkAll(): List<CheckReport>

    /**
     * Rapports recalculés à chaque changement validé dans core-backend (flux chaud).
     * Le premier élément est toujours [CheckEvent.SUBSCRIBED].
     */
    fun events(): Multi<CheckEvent>
}

data class CheckReport(
    val productId: Long = 0,
    val product: String = "",
    /** 100 moins la somme des pénalités, borné à 0. */
    val score: Int = 100,
    val status: CheckStatus = CheckStatus.OK,
    val metrics: List<Metric> = emptyList(),
    val findings: List<Finding> = emptyList(),
    /** Contributions qui ont participé au calcul (information, l'appelant n'a pas à s'en servir). */
    val contributors: List<String> = emptyList(),
) {
    fun metric(code: String): BigDecimal? = metrics.firstOrNull { it.code == code }?.value
}

enum class CheckStatus { OK, WARNING, BLOCKING }

enum class Severity { INFO, WARNING, BLOCKING }

data class Metric(val code: String = "", val label: String = "", val value: BigDecimal = BigDecimal.ZERO, val unit: String = "")

data class Finding(
    val rule: String = "",
    val severity: Severity = Severity.INFO,
    val message: String = "",
    val penalty: Int = 0,
)

data class CheckEvent(
    val type: String = "",
    val productId: Long? = null,
    /** Nouveau rapport ([CHECKED]) ; null pour [SUBSCRIBED] et [REMOVED]. */
    val report: CheckReport? = null,
) {
    companion object {
        const val SUBSCRIBED = "subscribed"
        const val CHECKED = "checked"
        const val REMOVED = "removed"
    }
}
