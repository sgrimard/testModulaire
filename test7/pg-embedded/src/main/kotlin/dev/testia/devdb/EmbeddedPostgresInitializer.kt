package dev.testia.devdb

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource

/**
 * Vrai PostgreSQL (binaire embarqué, sans Docker) pour les tests Spring :
 * `@ContextConfiguration(initializers = [EmbeddedPostgresInitializer::class])`.
 * Une base par contexte Spring, arrêtée en dernier, après le pool de connexions.
 */
class EmbeddedPostgresInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        val pg = EmbeddedPostgres.builder().start()
        // Enregistré avant tout autre bean : détruit après eux.
        (context.beanFactory as DefaultListableBeanFactory).registerDisposableBean("embeddedPostgres", DisposableBean { pg.close() })
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "embedded-postgres",
                mapOf(
                    "spring.datasource.url" to pg.getJdbcUrl("postgres", "postgres"),
                    "spring.datasource.username" to "postgres",
                    "spring.datasource.password" to "postgres",
                ),
            ),
        )
    }
}
