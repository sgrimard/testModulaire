/** Client tiers : ne voit que le contrat de checking. */
@org.springframework.modulith.ApplicationModule(displayName = "Client tiers", allowedDependencies = {"checking::api", "checking::rest"})
package dev.testia.mono6.client;
