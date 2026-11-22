package dev.testia.mono3.client

import dev.testia.mono3.core.api.FieldDescriptor.Companion.CORE
import dev.testia.mono3.core.api.ProductOperations
import dev.testia.mono3.core.rest.ProductApi
import dev.testia.mono3.core.service.ProductService
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

/**
 * Client tierce partie : ne dépend que de core-backend. Le même code fonctionne en appel direct
 * et en REST, avec ou sans plug-in, car les champs supplémentaires sont découverts via le schéma.
 */
@ApplicationScoped
class ThirdPartyClient(
    direct: ProductService,
    @RestClient rest: ProductApi,
) {
    enum class Mode { DIRECT, REST }

    private val operations: Map<Mode, ProductOperations> = mapOf(Mode.DIRECT to direct, Mode.REST to rest)

    fun operations(mode: Mode): ProductOperations = operations.getValue(mode)

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
