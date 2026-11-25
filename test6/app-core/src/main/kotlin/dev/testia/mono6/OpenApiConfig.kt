package dev.testia.mono6

import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Le JSON des DTO contient toujours chaque propriété (null compris) : le contrat les déclare toutes
 * obligatoires, les valeurs absentes restant typées « nullable ». Sans cela, springdoc rend optionnelles
 * les propriétés nullables (`id?:` en TypeScript), ce que l'IHM ne doit pas avoir à gérer.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun everyPropertyIsPresent() = OpenApiCustomizer { api ->
        api.components?.schemas?.values?.forEach { schema ->
            schema.properties?.let { schema.required = it.keys.sorted() }
        }
    }
}
