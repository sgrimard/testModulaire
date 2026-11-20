package dev.testia.mono.server

import io.smallrye.mutiny.Multi

/**
 * Contrat vu par les clients : découverte des extensions par nom,
 * puis accès générique (get / put / abonnement) via un [ExtensionHandle].
 * Deux implémentations : locale (appel direct, ici) et REST (module client).
 */
interface ExtensionGateway {
    fun names(): List<String>

    /** @throws UnknownExtensionException si aucune extension ne porte ce nom. */
    fun extension(name: String): ExtensionHandle
}

interface ExtensionHandle {
    val name: String
    fun get(key: String): String?
    fun put(key: String, value: String)

    /** Flux chaud ; le premier élément est toujours [ExtensionEvent.SUBSCRIBED]. */
    fun events(): Multi<ExtensionEvent>
}

data class ExtensionEvent(
    val extension: String,
    val type: String,
    val key: String? = null,
    val value: String? = null,
) {
    companion object {
        const val SUBSCRIBED = "subscribed"
    }
}

class UnknownExtensionException(name: String) : NoSuchElementException("Unknown extension: $name")
