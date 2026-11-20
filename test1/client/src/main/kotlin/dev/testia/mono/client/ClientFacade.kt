package dev.testia.mono.client

import dev.testia.mono.server.ExtensionGateway
import dev.testia.mono.server.LocalExtensionGateway
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/** Le client ne connaît que le contrat du serveur ; le mode d'accès est un détail. */
@ApplicationScoped
class ClientFacade @Inject constructor(
    local: LocalExtensionGateway,
    remote: RemoteExtensionGateway,
) {
    enum class Mode { DIRECT, REST }

    private val gateways: Map<Mode, ExtensionGateway> = mapOf(Mode.DIRECT to local, Mode.REST to remote)

    fun gateway(mode: Mode): ExtensionGateway = gateways.getValue(mode)
}
