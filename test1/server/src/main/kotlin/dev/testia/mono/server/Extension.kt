package dev.testia.mono.server

/**
 * SPI implémentée par les modules d'extension (bean CDI @ApplicationScoped).
 * Les événements sont publiés via le bus [ExtensionEvents] fourni par le serveur.
 */
interface Extension {
    val name: String
    fun get(key: String): String?
    fun put(key: String, value: String)
}
