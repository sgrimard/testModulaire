package dev.testia.mono5.pg

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

/** Vrai PostgreSQL (binaire embarqué, sans Docker) démarré pour la durée des tests Quarkus. */
class EmbeddedPostgresResource : QuarkusTestResourceLifecycleManager {
    private var postgres: EmbeddedPostgres? = null

    override fun start(): Map<String, String> {
        val pg = EmbeddedPostgres.builder().start().also { postgres = it }
        return mapOf(
            "quarkus.datasource.jdbc.url" to pg.getJdbcUrl("postgres", "postgres"),
            "quarkus.datasource.username" to "postgres",
            "quarkus.datasource.password" to "postgres",
        )
    }

    override fun stop() {
        postgres?.close()
    }
}
