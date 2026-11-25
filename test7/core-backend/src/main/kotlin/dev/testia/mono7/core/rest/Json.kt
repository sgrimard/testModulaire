package dev.testia.mono7.core.rest

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

/**
 * Réglage JSON commun au serveur et aux clients REST. Le module Kotlin de Jackson 3 active
 * `StrictNullChecks`, qui refuse à tort les valeurs null des champs d'extension
 * (`Map<String, Map<String, Any?>>`) : on le désactive.
 */
object Json {
    val kotlinModule: KotlinModule = KotlinModule.Builder().disable(KotlinFeature.StrictNullChecks).build()

    val mapper: JsonMapper = JsonMapper.builder().findAndAddModules().addModule(kotlinModule).build()
}

@Configuration
class JsonConfig {
    /** Passe après la configuration de Spring Boot : remplace le module Kotlin trouvé sur le classpath. */
    @Bean
    fun kotlinJsonCustomizer() = JsonMapperBuilderCustomizer { it.addModule(Json.kotlinModule) }
}
