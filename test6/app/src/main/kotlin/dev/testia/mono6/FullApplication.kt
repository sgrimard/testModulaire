package dev.testia.mono6

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Assemblage complet : noyau, deux extensions de données, checking et ses trois contributions.
 * Placée dans le paquet racine : chaque sous-paquet direct (core, shipping, checking…) est un
 * module Spring Modulith. Les modules présents sont ceux que build.gradle.kts met sur le classpath.
 */
@SpringBootApplication
class FullApplication

fun main(args: Array<String>) {
    runApplication<FullApplication>(*args)
}
