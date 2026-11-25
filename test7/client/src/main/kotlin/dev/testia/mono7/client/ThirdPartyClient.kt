package dev.testia.mono7.client

import dev.testia.mono7.core.api.FieldDescriptor.Companion.CORE
import dev.testia.mono7.core.api.ProductOperations
import dev.testia.mono7.core.rest.productApiClient
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Client tierce partie : ne dépend que de core-backend. Le même code fonctionne en appel direct
 * et en REST, avec ou sans plug-in, car les champs supplémentaires sont découverts via le schéma.
 */
@Component
class ThirdPartyClient(
    private val direct: ProductOperations,
    private val env: Environment,
) {
    enum class Mode { DIRECT, REST }

    /**
     * Serveur REST : `product-api.url`, sinon l'application elle-même. Résolu au premier appel,
     * quand le port réel (`local.server.port`) est connu.
     */
    private val rest: ProductOperations by lazy {
        productApiClient(env.getProperty("product-api.url") ?: "http://localhost:${env.getRequiredProperty("local.server.port")}")
    }

    fun operations(mode: Mode): ProductOperations = if (mode == Mode.DIRECT) direct else rest

    /** Export « à plat » piloté par le schéma : clé -> valeur, sans connaître les plug-ins. */
    fun export(mode: Mode, id: Long): Map<String, Any?> {
        val ops = operations(mode)
        val product = ops.get(id)
        return ops.schema().associate { f ->
            f.key to when {
                f.namespace != CORE -> product.extensions[f.namespace]?.get(f.name)
                f.name == "name" -> product.name
                f.name == "description" -> product.description
                f.name == "price" -> product.price
                f.name == "category" -> product.category
                else -> null
            }
        }
    }
}
