# test6 : le test 5 sur Spring Boot + Spring Modulith

Même découpage, mêmes règles et même IHM React/TypeScript que le test 5. Le backend passe de
Quarkus à **Spring Boot 4.2** et **Spring Modulith 2.2** (Kotlin 2.4, JDK 25), et les règles
d'architecture sont désormais **vérifiées par un test** au lieu de reposer sur la relecture.

## Organisation

| Dossier | Module Spring Modulith (paquet) | Rôle | Dépendances autorisées |
| --- | --- | --- | --- |
| `core-backend` | `core` | Noyau des données | aucune |
| `ext-backend-1` | `shipping` | Extension de données (poids, matière dangereuse) | `core::api`, `core::spi` |
| `ext-backend-2` | `supplier` | Extension de données (fournisseur, coût, délai) | `core::api`, `core::spi` |
| `checking` | `checking` | Calcul extensible | `core::api`, `core::rest` |
| `ext-checking-1` | `shippingcheck` | Contribution au calcul (shipping) | `core::api`, `checking::api`, `checking::spi`, `shipping` |
| `ext-checking-2` | `suppliercheck` | Contribution au calcul (supplier) | idem avec `supplier` |
| `ext-checking-3` | `landedcost` | Coût de revient (les deux extensions) | idem avec `shipping` et `supplier` |
| `client` | `client` | Client tiers | `checking::api`, `checking::rest` |
| `app`, `app-ext1`, `app-core` | — (paquet racine) | Assemblages Spring Boot : ports 8100 / 8102 / 8104, base 5436 | — |
| `pg-embedded` | — (`dev.testia.devdb`) | PostgreSQL embarqué (tests et lancement local) | — |
| `core-frontend`, `checking-frontend`, `desktop` | — | IHM npm, identique au test 5 | — |

Un module Gradle = un module Spring Modulith = un sous-paquet direct de `dev.testia.mono6`. La
classe d'application de chaque assemblage est dans le paquet racine ; les modules présents sont ceux
que son `build.gradle.kts` met sur le classpath.

### Ce que déclare chaque module

Dans `src/main/java/.../package-info.java` (Kotlin n'a pas d'annotation de paquet) :

- `@ApplicationModule(allowedDependencies = …)` sur le paquet de base : les seuls modules (ou
  interfaces nommées `module::nom`) que le module a le droit d'utiliser. `{}` pour le noyau : il
  ne dépend de rien.
- `@NamedInterface("api" | "spi" | "rest")` sur les sous-paquets publiés. Tout autre sous-paquet
  (`service`, `persistence`, `internal`) est **interne** : un autre module qui l'importe fait
  échouer la vérification.
- Pour les extensions de données, le paquet de base est l'API typée (`ProductDto.shipping()`) et
  `internal` contient la table, l'entité et l'implémentation du SPI.

`ModularityTest` (dans chaque assemblage) appelle `ApplicationModules.of(…).verify()` : cycles,
accès à un paquet interne et dépendance non déclarée font échouer `./gradlew test`. Dans `app`, il
écrit aussi les diagrammes des modules (PlantUML / C4) dans `app/build/spring-modulith-docs`.

## Correspondance Quarkus → Spring

| Test 5 (Quarkus) | Test 6 (Spring) |
| --- | --- |
| Bean `@ApplicationScoped` | `@Component` / `@Service` |
| Découverte `Instance<ProductExtension>` | `ObjectProvider<ProductExtension>` |
| Contrat REST JAX-RS + client `@RegisterRestClient` | Interface `@HttpExchange` : implémentée par le `@RestController`, et client généré par `HttpServiceProxyFactory` (`productApiClient(url)`, `checkingApiClient(url)`) |
| `ResponseExceptionMapper` / `@ServerExceptionMapper` | `RestClients` (WebClient) / `@RestControllerAdvice` |
| Flux `Multi` (Mutiny) | `Flux` (Reactor), servi en SSE par Spring MVC |
| Événement CDI `@Observes(during = AFTER_SUCCESS)` | Événement Spring `@TransactionalEventListener` (après commit) |
| `checking` s'abonne au flux de `core-backend` | `checking` écoute l'événement `ProductEvent` du module `core` |
| `quarkus-smallrye-openapi`, `/q/openapi` | springdoc, `/v3/api-docs` (assemblage `app-core`) |
| `@QuarkusTest` + `@WithTestResource` | `@SpringBootTest(RANDOM_PORT)` + `EmbeddedPostgresInitializer` |
| — | `ModularityTest` : règles d'architecture vérifiées |

### Points d'attention

- **Deux beans pour un contrat.** Le contrôleur REST implémente aussi `ProductOperations` (il
  implémente `ProductApi`). Le service est donc marqué `@Primary` : un module qui injecte le
  contrat reçoit l'appel direct.
- **Jackson 3 et Kotlin.** L'option `StrictNullChecks` du module Kotlin, active par défaut, refuse
  les valeurs `null` des champs d'extension (`Map<String, Map<String, Any?>>`). Elle est désactivée
  pour le serveur et les clients REST (`core/rest/Json.kt`).
- **Contrat OpenAPI.** springdoc rend optionnelles les propriétés nullables. Comme le JSON contient
  toujours chaque propriété, `app-core` les déclare toutes obligatoires (`OpenApiConfig`) : les
  types TypeScript restent ceux du test 5, et le contrat entre l'IHM du noyau et son plug-in compile.
  Le fichier `app-core/build/openapi/openapi.json` est écrit par le test `CoreOnlyTest`.
- **Rapports recalculés.** `checking` écoute `ProductEvent` en phase `BEFORE_COMMIT` : le rapport
  est calculé dans la transaction qui écrit, donc sur l'état exact de ce commit, puis diffusé après
  le commit réussi, dans l'ordre des commits. Un écouteur asynchrone (`@ApplicationModuleListener`)
  relirait le produit plus tard, et pourrait publier pour un changement l'état d'un changement
  suivant (cas observé pendant la mise au point). Contrepartie : le calcul allonge la transaction
  d'écriture ; une contribution qui échoue est journalisée sans faire échouer l'écriture.
- **Registre des publications d'événements** (`spring-modulith-starter-jdbc`) : non utilisé.
  L'écouteur ne fait que pousser des rapports aux connexions ouvertes : rien à rejouer après un
  redémarrage. Il deviendrait utile pour un écouteur qui écrit des données (outbox).

## Commandes

Prérequis : JDK 25, Node 22 ou plus, npm 10 ou 11.

```bash
# Backend
./gradlew test                     # 20 tests : les 14 du test 5 + 6 de modularité
./gradlew build installDist -x test
pg-embedded/build/install/pg-embedded/bin/pg-embedded 5436 &
java -jar app/build/libs/app.jar &          # http://localhost:8100 (app-ext1 : 8102, app-core : 8104)

# IHM (identique au test 5)
npm install
npm run api          # régénère les types depuis app-core/build/openapi/openapi.json (après ./gradlew :app-core:test)
npm run typecheck
npm test
npm run dev          # http://<hôte>:5173/?backend=http://<hôte>:8100
npm run dist         # application Electron : desktop/release/
```

L'application de bureau vise `http://localhost:8100` par défaut (`--backend=URL` ou `BACKEND_URL`
pour un autre serveur). Voir le README du test 5 pour l'installation sous Windows et Linux.
