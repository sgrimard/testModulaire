package dev.testia.mono7

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Assemblage sans plug-in : noyau et son IHM.
 * Placée dans le paquet racine : chaque sous-paquet direct (core, ui, shipping, client) est un
 * module Spring Modulith. Les modules présents sont ceux que build.gradle.kts met sur le classpath.
 */
@SpringBootApplication
class CoreApplication

fun main(args: Array<String>) {
    runApplication<CoreApplication>(*args)
}
