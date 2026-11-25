/**
 * Extension de données « supplier » (ext-backend-2). Le paquet de base est son API (vue typée) ;
 * {@code internal} (table, entité, implémentation du SPI) est invisible des autres modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Extension supplier (ext-backend-2)", allowedDependencies = {"core::api", "core::spi"})
package dev.testia.mono6.supplier;
