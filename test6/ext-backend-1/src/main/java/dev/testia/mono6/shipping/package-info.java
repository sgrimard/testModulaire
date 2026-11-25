/**
 * Extension de données « shipping » (ext-backend-1). Le paquet de base est son API (vue typée) ;
 * {@code internal} (table, entité, implémentation du SPI) est invisible des autres modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Extension shipping (ext-backend-1)", allowedDependencies = {"core::api", "core::spi"})
package dev.testia.mono6.shipping;
