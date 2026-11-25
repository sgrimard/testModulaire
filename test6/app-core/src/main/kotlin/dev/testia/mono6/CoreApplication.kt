package dev.testia.mono6

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Assemblage minimal : noyau et checking, sans extension. Publie le contrat OpenAPI.
 * Placée dans le paquet racine : chaque sous-paquet direct (core, shipping, checking…) est un
 * module Spring Modulith. Les modules présents sont ceux que build.gradle.kts met sur le classpath.
 */
@SpringBootApplication
class CoreApplication

fun main(args: Array<String>) {
    runApplication<CoreApplication>(*args)
}
