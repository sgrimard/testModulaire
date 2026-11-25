/**
 * Plug-in « shipping » (ext-backend) : champs, table, règles des champs, étape de devis et libellé.
 * Il ne publie rien ; tout son code est dans {@code internal}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Plug-in shipping (ext-backend)", allowedDependencies = {"core::api", "core::spi"})
package dev.testia.mono7.shipping;
