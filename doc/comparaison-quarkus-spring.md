# Quarkus ou Spring Modulith : le code à écrire

Le même monolithe modulaire Kotlin (noyau, extensions, calcul, client, assemblages), écrit deux fois. Chaque écart montre le code Quarkus des tests 3 et 5, puis le code Spring Boot 4.2 + Spring Modulith 2.2 des tests 6 et 7. Les extraits sont tirés du dépôt, légèrement raccourcis.

## Synthèse

| Écart | Le plus simple | Pourquoi |
| --- | --- | --- |
| [Déclarer un module](#1-déclarer-un-module) | Spring | Dépendances autorisées déclarées et vérifiées par un test |
| [Beans et découverte](#2-beans-et-découverte-des-extensions) | Équivalent | Renommage : `Instance` → `ObjectProvider` |
| [Implémentation par défaut](#3-implémentation-par-défaut-remplaçable) | Quarkus | `@DefaultBean` n'a pas d'équivalent direct |
| [Contrat REST](#4-contrat-rest-serveur-et-client) | Équivalent | Même idée (une interface, deux usages), annotations différentes |
| [Erreurs HTTP](#5-erreurs-http-du-contrat) | Quarkus | Côté client, il faut écrire la conversion soi-même |
| [Client REST](#6-obtenir-un-client-rest) | Quarkus | Injection par `@RestClient` contre fabrique et URL |
| [Flux SSE](#7-flux-dévénements-sse) | Équivalent | Mutiny `Multi` contre Reactor `Flux` |
| [Événements entre modules](#8-événements-entre-modules) | Équivalent | Mêmes phases transactionnelles |
| [Code au démarrage](#9-code-exécuté-au-démarrage) | Équivalent | `StartupEvent` contre `InitializingBean` |
| [Configuration](#10-configuration-dun-assemblage) | Quarkus | CORS et client REST en propriétés, pas en code |
| [JSON Kotlin](#11-json-et-kotlin) | Quarkus | Jackson 3 refuse les `null` des maps d'extension |
| [Contrat OpenAPI](#12-contrat-openapi-pour-lihm) | Quarkus | Écrit au build ; springdoc demande un ajustement |
| [Tests d'intégration](#13-tests-dintégration) | Équivalent | Base embarquée branchée différemment |
| [Vérifier l'architecture](#14-vérifier-larchitecture) | Spring | Un test de 10 lignes, aucun équivalent Quarkus |
| [Assemblage exécutable](#15-assemblage-exécutable) | Équivalent | Spring demande une classe `main` par assemblage |

## Volume de code écrit

Lignes non vides, hors code généré et IHM npm.

| | Test 3 | Test 7 | Test 5 | Test 6 |
| --- | ---: | ---: | ---: | ---: |
| Code principal | 752 | 880 | 1 165 | 1 344 |
| Tests | 178 | 246 | 300 | 399 |
| Gradle | 96 | 95 | 123 | 129 |
| Propriétés | 20 | 16 | 43 | 31 |

Environ 15 % de code principal en plus côté Spring : les `package-info.java` des modules, la fabrique de clients REST, le réglage JSON. Les tests en plus sont ceux de modularité, qui n'existent pas côté Quarkus.

## 1. Déclarer un module

Quarkus ne connaît pas la notion de module : un module Gradle doit seulement être indexé par CDI. Spring Modulith fait de chaque sous-paquet direct de l'application un module, qui déclare ce qu'il publie et ce qu'il a le droit d'utiliser.

**Quarkus** — `src/main/resources/META-INF/beans.xml`

```xml
<!-- Dans chaque module, pour que Quarkus
     indexe ses beans -->
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
       bean-discovery-mode="all"/>

// Paquet libre : dev.testia.mono3.ext.shipping
// Les règles (« le noyau ne dépend d'aucune
// extension ») sont dans AGENTS.md et tenues
// par la relecture et les dépendances Gradle.
```

**Spring Modulith** — `src/main/java/…/shipping/package-info.java`

```java
// Paquet imposé : sous-paquet direct de
// dev.testia.mono7 (sinon le module s'appelle « ext »)
@ApplicationModule(
    displayName = "Plug-in shipping (ext-backend)",
    allowedDependencies = {"core::api", "core::spi"})
package dev.testia.mono7.shipping;

// Noyau : il ne dépend de rien
@ApplicationModule(allowedDependencies = {})
package dev.testia.mono7.core;

// Sous-paquet publié par le noyau
@NamedInterface("spi")
package dev.testia.mono7.core.spi;
```

> **À retenir.** Côté Spring, la règle d'architecture est écrite à côté du code, dans un fichier Java (Kotlin n'a pas d'annotation de paquet). Tout sous-paquet sans `@NamedInterface` est interne au module.

## 2. Beans et découverte des extensions

Le noyau découvre les implémentations de ses points d'extension sans les connaître. Seuls les noms changent.

**Quarkus** — ProductService.kt (test 3)

```kotlin
@ApplicationScoped
class ProductService(
    private val em: EntityManager,
    extensions: Instance<ProductExtension>,
    quoteSteps: Instance<QuoteStep>,
    policies: Instance<FieldPolicy>,
    private val labelFormatter: LabelFormatter,
) : ProductOperations {
    private val quoteSteps =
        quoteSteps.sortedBy { it.order }

    @jakarta.transaction.Transactional
    override fun get(id: Long) = …
```

**Spring** — ProductService.kt (test 7)

```kotlin
@Service
@Primary  // voir l'écart 4
class ProductService(
    private val em: EntityManager,
    extensions: ObjectProvider<ProductExtension>,
    quoteSteps: ObjectProvider<QuoteStep>,
    policies: ObjectProvider<FieldPolicy>,
    private val labelFormatter: LabelFormatter,
) : ProductOperations {
    private val quoteSteps = quoteSteps
        .orderedStream().toList().sortedBy { it.order }

    @Transactional(readOnly = true)  // Spring
    override fun get(id: Long) = …
```

> **À retenir.** `ObjectProvider` plutôt que `List<X>` : il accepte qu'aucune extension ne soit présente (assemblage noyau seul). Côté Gradle, le plugin `allopen` de Quarkus devient `kotlin("plugin.spring")`.

## 3. Implémentation par défaut remplaçable

Le noyau fournit un libellé par défaut ; le plug-in peut le remplacer. C'est le seul écart de conception entre les deux versions.

**Quarkus** — noyau et plug-in (test 3)

```kotlin
// Noyau : désactivé si un autre bean existe
@DefaultBean
@ApplicationScoped
class CoreLabelFormatter : LabelFormatter {
    override fun label(p: ProductDto) = coreLabel(p)
}

// Plug-in : rien de particulier à déclarer
@ApplicationScoped
class ShippingLabelFormatter : LabelFormatter {
    override fun label(p: ProductDto) =
        coreLabel(p) + …
}
```

**Spring** — noyau et plug-in (test 7)

```kotlin
// Noyau : implémentation ordinaire
@Component
class CoreLabelFormatter : LabelFormatter {
    override fun label(p: ProductDto) = coreLabel(p)
}

// Plug-in : c'est lui qui se déclare prioritaire
@Component
@Primary
class ShippingLabelFormatter : LabelFormatter {
    override fun label(p: ProductDto) =
        coreLabel(p) + …
}
```

> **Piège.** `@ConditionalOnMissingBean` ressemble à `@DefaultBean` mais n'est fiable que dans une auto-configuration, pas sur des classes trouvées par le scan. L'initiative passe donc du noyau au plug-in. Deux plug-ins `@Primary` font échouer le démarrage, comme deux remplaçants avec Quarkus.

## 4. Contrat REST serveur et client

Dans les deux cas, une interface étend le contrat Kotlin et sert à la fois au contrôleur et au client. Les annotations changent, et le service doit être marqué prioritaire.

**Quarkus (JAX-RS)** — `ProductApi.kt`

```kotlin
@RegisterRestClient(configKey = "product-api")
@RegisterProvider(ProductErrorMapper::class)
@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ProductApi : ProductOperations {
    @GET @Path("/{id}")
    override fun get(@PathParam("id") id: Long): ProductDto

    @POST
    override fun create(product: ProductDto): ProductDto

    @GET @Path("/{id}/quote")
    override fun quote(@PathParam("id") id: Long,
        @QueryParam("quantity") quantity: Int): Quote
}

@Path("/api/products")
class ProductResource(private val service: ProductService)
    : ProductApi {
    override fun get(id: Long) = service.get(id)
    …
}
```

**Spring (@HttpExchange)** — `ProductApi.kt`

```kotlin
@HttpExchange("/api/products",
    accept = [MediaType.APPLICATION_JSON_VALUE])
interface ProductApi : ProductOperations {
    @GetExchange("/{id}")
    override fun get(@PathVariable("id") id: Long): ProductDto

    @PostExchange
    override fun create(@RequestBody product: ProductDto): ProductDto

    @GetExchange("/{id}/quote")
    override fun quote(@PathVariable("id") id: Long,
        @RequestParam("quantity") quantity: Int): Quote
}

@RestController
class ProductResource(private val service: ProductService)
    : ProductApi {
    override fun get(id: Long) = service.get(id)
    …
}

// Le contrôleur implémente aussi ProductOperations :
// le service est marqué @Primary pour que
// « inject ProductOperations » donne l'appel direct.
```

> **À retenir.** Spring MVC lit les `@HttpExchange` de l'interface implémentée par le contrôleur. Le corps de requête doit être annoté `@RequestBody` ; JAX-RS le déduisait.

## 5. Erreurs HTTP du contrat

Les exceptions du contrat deviennent des statuts HTTP côté serveur, puis redeviennent les mêmes exceptions côté client.

**Quarkus** — ProductResource.kt, ProductApi.kt

```kotlin
// Serveur
@ServerExceptionMapper
fun invalid(e: ProductValidationException): Response =
    Response.status(400).type(APPLICATION_JSON)
        .entity(ValidationErrors(e.violations)).build()

// Client : branché par @RegisterProvider
class ProductErrorMapper
    : ResponseExceptionMapper<RuntimeException> {
    override fun handles(status: Int, h: …) =
        status == 400 || status == 404
    override fun toThrowable(r: Response) =
        when (r.status) {
            404 -> ProductNotFoundException(null)
            400 -> ProductValidationException(
                r.readEntity(ValidationErrors::class.java)
                    .violations)
            else -> null
        }
}
```

**Spring** — ProductResource.kt, RestClients.kt

```kotlin
// Serveur
@RestControllerAdvice
class ProductErrorHandler {
    @ExceptionHandler
    fun invalid(e: ProductValidationException) =
        ResponseEntity.status(400)
            .body(ValidationErrors(e.violations))
}

// Client : fabrique WebClient écrite une fois
WebClient.builder()
    .baseUrl(baseUrl)
    .defaultStatusHandler(
        { it.value() == 400 || it.value() == 404 },
        ::toException)  // lit le corps → exception
    .build()
// puis HttpServiceProxyFactory
//   .builderFor(WebClientAdapter.create(web))
//   .build().createClient(ProductApi::class.java)
```

## 6. Obtenir un client REST

Le client tiers utilise le même contrat en appel direct et en REST.

**Quarkus** — `ThirdPartyClient.kt`

```kotlin
@ApplicationScoped
class ThirdPartyClient(
    direct: ProductService,
    @RestClient rest: ProductApi,
) {
    private val operations = mapOf(
        Mode.DIRECT to direct, Mode.REST to rest)
}

# application.properties
quarkus.rest-client.product-api.url=http://localhost:8084
%test.quarkus.rest-client.product-api.url=http://localhost:8081
```

**Spring** — `ThirdPartyClient.kt`

```kotlin
@Component
class ThirdPartyClient(
    private val direct: ProductOperations,
    private val env: Environment,
) {
    // créé au premier appel : le port réel n'est
    // connu qu'une fois le serveur démarré
    private val rest by lazy {
        productApiClient(env.getProperty("product-api.url")
            ?: "http://localhost:${env
                .getRequiredProperty("local.server.port")}")
    }
}
```

> **Piège.** Un client REST déclaré comme bean implémenterait lui aussi `ProductApi` : trois beans du même type (service, contrôleur, client). D'où la fabrique appelée à la demande plutôt qu'une injection.

## 7. Flux d'événements SSE

Le noyau et le calcul publient un flux chaud ; le premier élément confirme l'abonnement.

**Quarkus (Mutiny)** — ProductEvents.kt, ProductApi.kt

```kotlin
fun stream(): Multi<ProductEvent> =
    Multi.createFrom().emitter { emitter ->
        subscribers += emitter
        emitter.onTermination { subscribers -= emitter }
        emitter.emit(ProductEvent(SUBSCRIBED))
    }

@GET @Path("/events")
@Produces(MediaType.SERVER_SENT_EVENTS)
@RestStreamElementType(MediaType.APPLICATION_JSON)
override fun events(): Multi<ProductEvent>

// Abonnement
flux.subscribe().with { queue.add(it) }
```

**Spring (Reactor)** — ProductEvents.kt, ProductApi.kt

```kotlin
fun stream(): Flux<ProductEvent> =
    Flux.create { sink ->
        subscribers += sink
        sink.onDispose { subscribers -= sink }
        sink.next(ProductEvent(SUBSCRIBED))
    }

@GetExchange("/events",
    accept = [MediaType.TEXT_EVENT_STREAM_VALUE])
override fun events(): Flux<ProductEvent>

// Abonnement
flux.subscribe { queue.add(it) }

# application.properties : pas de délai maximal
spring.mvc.async.request-timeout=-1
```

> **À retenir.** Le client SSE passe par WebClient (module `spring-webflux`, avec le client HTTP du JDK) même si le serveur reste en Spring MVC.

## 8. Événements entre modules

Le noyau signale un changement validé ; `checking` recalcule le rapport du produit.

**Quarkus (CDI)** — ProductService.kt, ProductEvents.kt

```kotlin
class ProductService(
    private val changes: Event<ProductEvent>, …)
    …
    changes.fire(ProductEvent(UPDATED, id))

// Diffusé seulement après commit
fun onCommitted(
    @Observes(during = TransactionPhase.AFTER_SUCCESS)
    event: ProductEvent) { … }

// checking s'abonne au flux du noyau,
// comme un client externe
products.events()
    .emitOn(Infrastructure.getDefaultWorkerPool())
    .subscribe().with { onProductChange(it) }
```

**Spring** — ProductService.kt, CheckingService.kt

```kotlin
class ProductService(
    private val changes: ApplicationEventPublisher, …)
    …
    changes.publishEvent(ProductEvent(UPDATED, id))

// Diffusé seulement après commit
@TransactionalEventListener(phase = AFTER_COMMIT)
fun onCommitted(event: ProductEvent) { … }

// checking écoute l'événement du module core :
// calcul dans la transaction, diffusion après commit
@TransactionalEventListener(phase = BEFORE_COMMIT)
fun onProductChange(event: ProductEvent) {
    val out = report(event) ?: return
    TransactionSynchronizationManager
        .registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = publish(out)
        })
}
```

> **Piège.** Un écouteur asynchrone (`@ApplicationModuleListener`, l'outil phare de Spring Modulith) relit le produit plus tard : il a publié pour un changement l'état du changement suivant pendant la mise au point. D'où le calcul en `BEFORE_COMMIT`. Le registre des publications d'événements de Spring Modulith (table `event_publication`) ne sert que pour un écouteur qui écrit des données.

## 9. Code exécuté au démarrage

Le migrateur applique les scripts SQL de chaque module avant la première requête.

**Quarkus** — `Persistence.kt`

```kotlin
@ApplicationScoped
class SchemaMigrator(
    private val dataSource: DataSource,
    private val contributions: Instance<SchemaContribution>,
) {
    fun onStart(@Observes @Priority(1) event: StartupEvent) =
        migrate()
}
```

**Spring** — `Persistence.kt`

```kotlin
@Component
class SchemaMigrator(
    private val dataSource: DataSource,
    private val contributions: ObjectProvider<SchemaContribution>,
) : InitializingBean {
    override fun afterPropertiesSet() = migrate()
}
```

## 10. Configuration d'un assemblage

Mêmes réglages ; Spring en met une partie dans le code.

**Quarkus** — `app/…/application.properties`

```properties
quarkus.http.host=0.0.0.0
quarkus.http.port=8094
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5435/postgres
quarkus.devservices.enabled=false
quarkus.hibernate-orm.schema-management.strategy=none

quarkus.rest-client.checking-api.url=http://localhost:8094
%test.quarkus.rest-client.checking-api.url=http://localhost:8081

quarkus.http.cors.enabled=true
quarkus.http.cors.origins=app://desktop,/http://.*:5173/
```

**Spring** — `app/…/application.properties + CorsConfig.kt`

```properties
server.address=0.0.0.0
server.port=8100
spring.datasource.url=jdbc:postgresql://localhost:5436/postgres
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.mvc.async.request-timeout=-1
catalogue.cors.origins=app://desktop,http://*:5173

// Pas de propriété CORS globale pour Spring MVC
@Configuration
class CorsConfig(@Value("\${catalogue.cors.origins:}")
    private val origins: List<String>) : WebMvcConfigurer {
    override fun addCorsMappings(r: CorsRegistry) {
        r.addMapping("/api/**")
            .allowedOriginPatterns(*origins.toTypedArray())
    }
}
```

> **À retenir.** Pas de profil `%test` côté Spring : le port de test est aléatoire et lu au premier appel (écart 6).

## 11. JSON et Kotlin

Les champs d'extension sont une `Map<String, Map<String, Any?>>` dont les valeurs peuvent être nulles.

**Quarkus (Jackson 2)**

```kotlin
// Rien à écrire : le module quarkus-kotlin
// accepte les null dans les maps imbriquées.
```

**Spring Boot 4 (Jackson 3)** — `core/rest/Json.kt`

```kotlin
object Json {
    val kotlinModule: KotlinModule = KotlinModule.Builder()
        .disable(KotlinFeature.StrictNullChecks)
        .build()
    val mapper: JsonMapper = JsonMapper.builder()
        .findAndAddModules().addModule(kotlinModule).build()
}

@Configuration
class JsonConfig {
    // remplace le module trouvé par Spring Boot
    @Bean
    fun kotlinJsonCustomizer() =
        JsonMapperBuilderCustomizer { it.addModule(Json.kotlinModule) }
}
```

> **Piège.** Sans ce réglage, tout `POST` d'un produit dont une valeur d'extension est nulle répond 400 (« Invalid null value encountered for property extensions »), avant même d'atteindre le code.

## 12. Contrat OpenAPI pour l'IHM

Les types TypeScript des frontends sont générés depuis le contrat publié par l'assemblage `app-core`.

**Quarkus** — `app-core`

```kotlin
// build.gradle.kts
implementation("io.quarkus:quarkus-smallrye-openapi")

# application.properties : écrit au build
quarkus.smallrye-openapi.store-schema-directory=build/openapi

// Documenter une erreur
@APIResponse(responseCode = "400",
    content = [Content(schema = Schema(implementation = ApiError::class))])
```

**Spring (springdoc)** — `app-core`

```kotlin
// build.gradle.kts
implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.1")

// Le JSON contient toujours chaque propriété :
// les déclarer toutes obligatoires
@Bean
fun everyPropertyIsPresent() = OpenApiCustomizer { api ->
    api.components?.schemas?.values?.forEach { s ->
        s.properties?.let { s.required = it.keys.sorted() }
    }
}

// Écrit par un test (pas de génération au build)
val json = given().get("/v3/api-docs")…extract().asString()
Files.writeString(Path.of("build/openapi/openapi.json"), json)
```

> **Piège.** Sans le personnalisateur, springdoc rend optionnelles les propriétés nullables (`id?: number | null`) et le contrat entre l'IHM du noyau et son plug-in ne compile plus.

## 13. Tests d'intégration

Chaque assemblage est testé sur un vrai PostgreSQL embarqué, en appel direct et en REST.

**Quarkus** — `WithPluginTest.kt`

```kotlin
@QuarkusTest
@WithTestResource(EmbeddedPostgresResource::class)
class WithPluginTest {
    @Inject lateinit var client: ThirdPartyClient
    …
}

class EmbeddedPostgresResource
    : QuarkusTestResourceLifecycleManager {
    override fun start() = mapOf(
        "quarkus.datasource.jdbc.url" to pg.getJdbcUrl(…), …)
    override fun stop() { postgres?.close() }
}
```

**Spring** — `WithPluginTest.kt`

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(
    initializers = [EmbeddedPostgresInitializer::class])
class WithPluginTest {
    @Autowired lateinit var client: ThirdPartyClient
    @LocalServerPort var port = 0
    @BeforeEach fun ra() { RestAssured.port = port }
    …
}

class EmbeddedPostgresInitializer
    : ApplicationContextInitializer<…> {
    override fun initialize(ctx: …) {
        val pg = EmbeddedPostgres.builder().start()
        // arrêtée après le pool de connexions
        beanFactory.registerDisposableBean("embeddedPostgres") { pg.close() }
        ctx.environment.propertySources.addFirst(…)
    }
}
```

> **À retenir.** Spring Boot 4 ne fixe plus la version de RestAssured : elle est déclarée à la main (`6.3.0`).

## 14. Vérifier l'architecture

Le principal apport de Spring Modulith : les règles deviennent un test qui fait échouer le build.

**Quarkus** — `AGENTS.md`

```kotlin
// Pas d'équivalent. Contrôle à la main :
grep -rniE 'shipping|supplier' \
    core-backend/src checking/src client/src
// Gradle n'empêche qu'une dépendance de module
// entier ; il ne voit pas un import vers un
// paquet interne d'un module autorisé.
```

**Spring Modulith** — `ModularityTest.kt`

```kotlin
class ModularityTest {
    private val modules =
        ApplicationModules.of(WithPluginApplication::class.java)

    @Test
    fun `modules respect their declared dependencies`() {
        modules.verify()
    }

    @Test
    fun `module documentation`() {
        Documenter(modules).writeDocumentation()  // PlantUML / C4
    }
}

// Échec obtenu en faisant utiliser ProductService
// par le plug-in :
// Module 'shipping' depends on non-exposed type
//   dev.testia.mono7.core.service.ProductService
//   within module 'core'!
```

> **À retenir.** Le test a révélé une dépendance cachée du test 3 : le plug-in réutilisait `coreLabel()`, une fonction du service interne du noyau. Elle est désormais publiée dans le SPI.

## 15. Assemblage exécutable

Un assemblage choisit les modules présents par ses dépendances Gradle.

**Quarkus** — `app/build.gradle.kts`

```kotlin
plugins { id("io.quarkus") }

dependencies {
    implementation(project(":core-backend"))
    implementation(project(":ext-backend"))
    implementation("io.quarkus:quarkus-kotlin")
    testImplementation("io.quarkus:quarkus-junit")
}
// Pas de classe main.
// Lancement : java -jar app/build/quarkus-app/quarkus-run.jar
```

**Spring** — `app/build.gradle.kts + WithPluginApplication.kt`

```kotlin
plugins { id("org.springframework.boot") }

dependencies {
    implementation(project(":core-backend"))
    implementation(project(":ext-backend"))
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}

// Une classe par assemblage, dans le paquet racine
package dev.testia.mono7
@SpringBootApplication
class WithPluginApplication
fun main(args: Array<String>) { runApplication<WithPluginApplication>(*args) }
// Lancement : java -jar app/build/libs/app.jar
```

> **À retenir.** L'IHM JavaScript du test 3 est servie sans changement : Spring Boot, comme Quarkus, sert les fichiers de `META-INF/resources` des jars.

---

Code source : dépôt testModulaire, dossiers `test3`, `test5` (Quarkus 3.31) et `test6`, `test7` (Spring Boot 4.2.0, Spring Modulith 2.2.0, springdoc 3.2.1), Kotlin 2.4, JDK 25. Les détails et les pièges sont aussi dans `test6/README.md` et `test7/README.md`.
