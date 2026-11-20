package dev.testia.mono2.pg

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

/**
 * Base de développement persistante : ./gradlew :pg-embedded:run
 * (port 5432, données dans build/devdb, utilisateur/mot de passe postgres/postgres).
 */
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toInt() ?: 5432
    val dataDir = Path.of(System.getProperty("devdb.dir", "build/devdb")).toAbsolutePath()
    val pg = EmbeddedPostgres.builder()
        .setPort(port)
        .setDataDirectory(dataDir)
        .setCleanDataDirectory(false)
        .start()
    println("PostgreSQL prêt : ${pg.getJdbcUrl("postgres", "postgres")} (données : $dataDir)")
    Runtime.getRuntime().addShutdownHook(Thread { pg.close() })
    CountDownLatch(1).await()
}
