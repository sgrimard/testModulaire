# Manuel du développeur : écrire et assembler des modules

Ce manuel explique comment appliquer les modèles retenus dans la
[note d'architecture](architecture.md). Chaque recette part d'un exemple réel du dépôt : ouvrez-le à
côté. Les instructions équivalentes pour un agent IA sont décrites dans
[instructions-ia.md](instructions-ia.md).

## 1. Vocabulaire

| Terme | Sens | Exemple (test 4) |
| --- | --- | --- |
| Noyau | Module toujours présent, qui publie des points d'extension | `core-backend`, `checking` |
| Point d'extension (SPI) | Interface Kotlin du noyau, implémentée par des beans CDI | `ProductExtension`, `ProductCheck` |
| Extension | Module optionnel qui implémente un point d'extension | `ext-backend-1`, `ext-checking-3` |
| API typée | Vue Kotlin qu'une extension publie sur ses propres données, pour les autres modules | `ProductDto.shipping()` |
| Brique verticale | Un module backend et son module d'IHM ; l'IHM ne dépend que de ce backend | `checking` + `checking-frontend` |
| Assemblage | Module Quarkus exécutable qui choisit les modules présents | `app`, `app-ext1`, `app-core` |

## 2. Les règles à ne jamais enfreindre

1. **Un noyau ne dépend d'aucune extension.** Ni import, ni nom d'extension dans son code, ni
   dépendance Gradle. Il découvre les implémentations avec `Instance<Spi>`.
2. **Une IHM ne dépend que de son module backend.** `x-frontend` a une seule dépendance de projet :
   `x`. Un plug-in d'IHM n'importe rien de `core-frontend` ; il reçoit le registre à l'exécution.
3. **On lit les données d'une autre extension par son API typée**, jamais par sa table ni par ses
   entités. La dépendance Gradle vers cette extension est déclarée explicitement.
4. **Chaque module possède ses tables** : scripts SQL dans `src/main/resources/db/<module>/`,
   déclarés par une `SchemaContribution`. Une table d'extension référence `product(id)`.
5. **Un service = un contrat Kotlin** (`XxxOperations`) implémenté en direct (service) et en REST
   (interface `XxxApi` qui étend le contrat). Les erreurs sont des exceptions du contrat, retraduites
   des deux côtés.
6. **Chaque assemblage a ses tests d'intégration**, en appel direct et en REST, sur un vrai
   PostgreSQL embarqué.

Pour vérifier la règle 1 avant un commit, cherchez les noms des extensions dans le noyau :

```bash
grep -rniE 'shipping|supplier|landed' core-backend/src checking/src client/src
# seuls des commentaires sont tolérés
```

## 3. Choisir la recette

| Je veux… | Recette | Exemple |
| --- | --- | --- |
| Ajouter des champs persistants à l'objet du noyau | [4.1](#41-ajouter-une-extension-de-données) | `test4/ext-backend-1` |
| Compléter un calcul existant | [4.2](#42-ajouter-une-contribution-au-calcul) | `test4/ext-checking-3` |
| Modifier un traitement du noyau (validation, étape, remplacement) | [4.3](#43-spécialiser-un-traitement-du-noyau) | `test2/ext-backend` |
| Changer les valeurs valides d'un champ du noyau | [4.4](#44-changer-les-règles-dun-champ-du-noyau) | `test3/ext-backend` |
| Offrir un service autonome adressé par son nom | [4.5](#45-offrir-un-service-par-nom) | `test1/ext-two` |
| Afficher quelque chose de plus dans l'IHM | [4.6](#46-ajouter-un-plug-in-dihm) | `test4/checking-frontend` |
| Exposer un nouveau service (direct + REST) | [4.7](#47-exposer-un-nouveau-service) | `test4/checking` |
| Livrer une nouvelle combinaison de modules | [4.8](#48-créer-un-assemblage) | `test4/app-ext1` |

## 4. Recettes

### 4.1 Ajouter une extension de données

Exemple complet : `test4/ext-backend-2` (fournisseur).

1. **Créer le module** `ext-backend-N` et l'ajouter à `settings.gradle.kts`.

   ```kotlin
   // ext-backend-N/build.gradle.kts
   plugins { `java-library` }
   dependencies {
       api(project(":core-backend"))   // l'extension dépend du noyau, jamais l'inverse
   }
   ```

   Ajouter `src/main/resources/META-INF/beans.xml` (copie de celui de `core-backend`) pour que
   Quarkus indexe le module.

2. **Créer la table**, dans `src/main/resources/db/<namespace>/001_<table>.sql` :

   ```sql
   create table supplier_product (
       product_id     bigint primary key references product (id),
       unit_cost      numeric(12, 2),
       lead_time_days integer
   );
   ```

   Et la déclarer (ordre ≥ 100 : après le noyau, qui a l'ordre 0) :

   ```kotlin
   @ApplicationScoped
   class SupplierSchema : SchemaContribution {
       override val module = SUPPLIER
       override val order = 100
       override val scripts = listOf("db/supplier/001_supplier_product.sql")
   }
   ```

   Un script déjà appliqué ne doit plus jamais être modifié : ajoutez `002_….sql`.

3. **Implémenter `ProductExtension`** : le `namespace` est le nom de l'extension (découverte par nom
   et préfixe des champs). Les contraintes des champs (`required`, `min`, `max`, `options`) sont la
   seule validation : le noyau les applique, l'IHM les affiche.

   ```kotlin
   override fun fields() = listOf(
       FieldDescriptor(SUPPLIER, "unitCost", "Coût d'achat unitaire", FieldType.NUMBER, min = 0.0, max = 100_000.0),
   )
   ```

   `load` reçoit plusieurs identifiants (une requête pour toute la liste) ; `save` crée ou met à
   jour ; `delete` supprime la ligne. Tout s'exécute dans la transaction du noyau. Les valeurs
   numériques arrivent en `Double` (voir `FieldType.coerce`).

4. **Publier l'API typée** pour les autres modules, dans un fichier `XxxApi.kt` :

   ```kotlin
   const val SUPPLIER = "supplier"
   data class SupplierInfo(val supplier: String?, val unitCost: BigDecimal?, val leadTimeDays: Int?)
   fun ProductDto.supplier(): SupplierInfo { /* lit extensions[SUPPLIER] */ }
   ```

   C'est le seul point d'entrée des autres modules : le format du dictionnaire et la table restent
   internes.

5. **Ajouter le module aux assemblages** concernés (recette 4.8) et compléter leurs tests.

Résultat attendu sans autre code : le schéma (`GET /api/products/schema`) contient les nouveaux
champs, `GET /api/products/extensions` liste le namespace, le formulaire de l'IHM les affiche, et
`ops.extension("supplier").put(id, …)` fonctionne.

### 4.2 Ajouter une contribution au calcul

Exemple : `test4/ext-checking-3` (coût de revient, qui lit deux extensions).

1. **Créer le module** `ext-checking-N` ; il dépend de `checking` et des extensions de données qu'il
   lit, et de rien d'autre :

   ```kotlin
   dependencies {
       implementation(project(":checking"))
       implementation(project(":ext-backend-1"))
       implementation(project(":ext-backend-2"))
   }
   ```

   Grâce à ces dépendances, Gradle refuse un assemblage qui contiendrait la contribution sans ses
   données.

2. **Implémenter `ProductCheck`** :

   ```kotlin
   @ApplicationScoped
   class LandedCostCheck : ProductCheck {
       override val name = "landed"
       override val order = 200 // après les contributions qui ne lisent qu'une extension
       override fun evaluate(product: ProductDto): CheckResult {
           val unitCost = product.supplier().unitCost ?: return CheckResult()
           val shipping = product.shipping().costPerUnit() ?: return CheckResult()
           …
       }
   }
   ```

   Règles :
   - `evaluate` est une fonction pure du produit : pas d'état, pas d'écriture, pas d'appel REST.
   - Codes des métriques et règles préfixés par `name` (`landed.cost`, `landed.margin-absorbed`).
   - Une contribution ne lit pas le résultat d'une autre : elle recalcule à partir des données
     (ici `costPerUnit()` vient de l'API typée d'`ext-backend-1`).
   - Donnée manquante : renvoyer un résultat vide si une autre contribution la signale déjà, sinon
     un constat `WARNING`.
   - Pénalité : 0 pour `INFO`, 5 à 20 pour `WARNING`, 40 à 50 pour `BLOCKING`.
   - Une exception est rattrapée par `checking` (constat « Contrôle indisponible »), mais ne comptez
     pas dessus.

3. **Ajouter le module aux assemblages** et un test qui vérifie le rapport en direct et en REST.

Le client et l'IHM n'ont rien à changer : ils affichent le rapport tel qu'il arrive.

### 4.3 Spécialiser un traitement du noyau

Exemple : `test2/ext-backend/…/ShippingExtension.kt`. Trois styles, à choisir selon le besoin :

| Style | Interface | Comportement | Exemple |
| --- | --- | --- | --- |
| Additif | `ProductValidator` | Toutes les implémentations s'appliquent ; leurs violations s'ajoutent | `ShippingValidator` |
| Pipeline | `QuoteStep` (`order`) | Chaque étape reçoit les lignes précédentes et renvoie la nouvelle liste | `ShippingQuoteStep` (ordre 100, après l'étape de base à 0) |
| Remplacement | `LabelFormatter` | Le noyau fournit une implémentation `@DefaultBean` ; une extension la supplante en déclarant son propre bean | `ShippingLabelFormatter` |

Pour le remplacement, une seule extension peut supplanter le comportement par défaut : deux
remplacements dans le même assemblage font échouer le démarrage (bean ambigu). Pour réutiliser le
comportement du noyau, appelez sa fonction publique (`coreLabel(product)`).

Pour ajouter un nouveau point d'extension au noyau, définissez l'interface dans son paquet `spi`,
injectez `Instance<…>` dans le service, triez par `order` si l'ordre compte, et documentez le style
(additif, pipeline ou remplacement) dans le KDoc.

### 4.4 Changer les règles d'un champ du noyau

Exemple : `test3/ext-backend/…/ShippingExtension.kt` (`ShippingFieldPolicy`).

```kotlin
@ApplicationScoped
class ShippingFieldPolicy : FieldPolicy {
    override val order = 100
    override fun adjust(field: FieldDescriptor, draft: ProductDto?): FieldDescriptor = when (field.key) {
        "price" -> field.copy(min = maxOf(field.min ?: 0.01, 0.01), max = minOf(field.max ?: 5_000.0, 5_000.0))
        else -> field
    }
}
```

Règles :
- Renvoyer une copie (`field.copy(…)`) ; ne jamais changer `namespace`, `name` ni `type` (le noyau
  le vérifie et refuse).
- Ne faire que **resserrer** ou **compléter** ce que les politiques précédentes ont produit
  (`maxOf` / `minOf`, ajout à `options`), pour que plusieurs politiques restent combinables.
- `draft` est le produit en cours de saisie (null pour le schéma sans contexte) : il permet des
  règles qui dépendent d'autres champs. L'IHM redemande le schéma à chaque modification
  (`POST /api/products/schema`).
- Prévoir les données existantes devenues invalides : elles restent lisibles, l'IHM les affiche
  « (non autorisée) », et le backend refuse de les réenregistrer. Prévenez les utilisateurs avant de
  déployer une politique plus stricte.

### 4.5 Offrir un service par nom

Exemple : `test1/ext-two/…/CounterExtension.kt`. À utiliser pour un service sans données liées à
l'objet du noyau.

```kotlin
@ApplicationScoped
class CounterExtension @Inject constructor(private val events: ExtensionEvents) : Extension {
    override val name = "counter"
    override fun get(key: String): String? = …
    override fun put(key: String, value: String) {
        …
        events.publish(ExtensionEvent(name, "incremented", key, after.toString()))
    }
}
```

Le nom doit être unique dans l'assemblage. Le client le découvre par `gateway.names()` et l'utilise
par `gateway.extension(name)`. L'IHM générique du test 1 l'affiche sans modification.

### 4.6 Ajouter un plug-in d'IHM

Exemple : `test4/checking-frontend`.

1. **Créer le module** `x-frontend`, qui ne dépend que de son backend :

   ```kotlin
   plugins { `java-library` }
   dependencies {
       implementation(project(":checking"))   // jamais core-frontend
   }
   ```

2. **Déclarer le plug-in** dans `src/main/resources/META-INF/ui-plugin.properties` :

   ```properties
   id=checking
   module=/plugins/checking/plugin.js
   ```

   `core-frontend` lit tous ces fichiers au démarrage et les sert sur `GET /ui/plugins`.

3. **Écrire le module ES** dans `src/main/resources/META-INF/resources/plugins/<id>/plugin.js`. Il
   exporte `register(registry)` et n'importe rien de `/core/…` :

   ```js
   export function register(registry) {
     registry.namespaceTitle('supplier', 'Fournisseur');              // titre d'un groupe de champs
     registry.fieldFormatter('supplier.unitCost', (v) => `${v} €`);   // affichage dans la liste
     registry.fieldEditor('supplier.supplier', (d, value) => ({ element, value: () => … }));
     registry.section('product-detail', (product) => element /* ou null */);
   }
   ```

   Contrat d'un éditeur : `{ element, value(), update?(descripteur), inline? }` (voir le commentaire
   en tête de `core-frontend/…/core/registry.js`).

4. **Règles** :
   - Appeler son propre backend par son API REST (`/api/checking/…`), jamais celle d'un autre module
     que son backend et le noyau.
   - Tolérer l'absence de données (renvoyer `null` pour une section) : une exception est rattrapée
     par le noyau, mais le plug-in disparaît alors de la page.
   - Styles : les injecter depuis le module, avec les variables CSS du noyau (`--line`, `--muted`,
     `--warn`, `--danger`, `--accent-soft`).
   - Mises à jour en direct : un seul `EventSource` par plug-in, partagé par toutes ses sections.

5. **Tester** dans l'assemblage : `GET /ui/plugins` contient l'`id`, et le module est servi. Le
   contrat JavaScript n'est pas vérifié à la compilation : vérifiez aussi la page dans un navigateur.

#### Variante React/TypeScript (test 5)

Exemple : `test5/checking-frontend`. Le plug-in est un paquet npm à côté de son backend.

1. `package.json` : `react` en `peerDependencies`, aucune dépendance vers `@test5/core-frontend`.
   Script `"api"` qui appelle `tools/openapi-slice.mjs` avec le préfixe REST de son backend.
2. `src/api/schema.ts` : généré par `npm run api`, à ne pas modifier. `src/api/client.ts` : client
   typé (`openapi-fetch`) et abonnement SSE.
3. `src/host.ts` : la forme du registre que le plug-in attend, avec des composants décrits comme
   des fonctions `(props) => ReactNode` (et non `ComponentType`, trop strict pour cette comparaison).
4. `src/index.ts` : exporte `xxxPlugin = { id, register: (registry: HostRegistry) => { … } }`.
   `register` est une **propriété fléchée**, pour que TypeScript compare strictement son paramètre.
5. Ajouter le plug-in à la liste de `desktop/src/renderer/main.tsx`, et son CSS (variables de l'hôte).
6. Vérifier : `npm run typecheck` (contrat avec l'hôte), `npm test`, puis `npm run dev` dans un
   navigateur.

### 4.7 Exposer un nouveau service

Exemple : `test4/checking`.

1. **Le contrat** (`api/XxxOperations.kt`) : interface Kotlin, DTO avec valeurs par défaut (pour
   Jackson), exceptions métier documentées en `@throws`. Un flux d'événements se déclare en
   `Multi<…>` dont le premier élément est un accusé `SUBSCRIBED`.
2. **Le service** (`service/XxxService.kt`) : `@ApplicationScoped`, implémente le contrat. C'est
   l'implémentation « appel direct ».
3. **L'interface REST** (`rest/XxxApi.kt`) étend le contrat et porte les annotations JAX-RS, plus
   `@RegisterRestClient(configKey = "xxx-api")` et un `ResponseExceptionMapper` qui retraduit les
   statuts HTTP en exceptions du contrat. Un flux se déclare avec
   `@Produces(SERVER_SENT_EVENTS)` et `@RestStreamElementType(APPLICATION_JSON)`.
4. **La ressource** (`XxxResource`) délègue au service, sans logique.
5. **Les erreurs** : un `@ServerExceptionMapper` par exception, qui renvoie un `ApiError`
   (`code`, `message`, `subject`, `violations`).

Pour injecter le service, utilisez la classe concrète (`CheckingService`) : l'interface du contrat a
plusieurs implémentations (service, ressource, client REST).

### 4.8 Créer un assemblage

Exemple : `test4/app-ext1`.

1. Module `app-xxx` avec le plugin `io.quarkus`, qui liste les modules présents :

   ```kotlin
   plugins { id("io.quarkus") }
   dependencies {
       implementation(project(":core-backend"))
       implementation(project(":checking"))
       implementation(project(":client"))
       implementation(project(":core-frontend"))
       implementation(project(":checking-frontend"))
       implementation(project(":ext-backend-1"))
       implementation(project(":ext-checking-1"))
       implementation("io.quarkus:quarkus-kotlin")
       testImplementation(project(":pg-embedded"))
       testImplementation("io.quarkus:quarkus-junit")
       testImplementation("io.rest-assured:rest-assured")
   }
   ```

2. `application.properties` : port HTTP propre, base, URL des clients REST (avec la variante
   `%test` sur le port 8081), et `quarkus.hibernate-orm.schema-management.strategy=none`.
3. Un test `@QuarkusTest @WithTestResource(EmbeddedPostgresResource::class)` qui vérifie, **dans les
   deux modes** (`Mode.entries.forEach`), ce qui est propre à cette combinaison : extensions
   découvertes, contributions présentes dans le rapport, plug-ins d'IHM servis.

### 4.9 Variante Spring Boot + Spring Modulith (tests 6 et 7)

Les recettes 4.1 à 4.8 s'appliquent avec ces équivalences :

| Quarkus (tests 1 à 5) | Spring (test 6) |
| --- | --- |
| `META-INF/beans.xml` dans le module | rien : l'application scanne `dev.testia.monoN` |
| `@ApplicationScoped` | `@Component` (`@Service` pour un service, marqué `@Primary` s'il implémente un contrat aussi implémenté par son contrôleur) |
| `Instance<Spi>` | `ObjectProvider<Spi>` (`orderedStream()`) |
| Interface JAX-RS `XxxApi` + `@RegisterRestClient` | Interface `@HttpExchange` `XxxApi` ; `@RestController` qui l'implémente ; client `RestClients.create(url, XxxApi::class.java)` |
| `Multi` | `Flux` |
| `@DefaultBean` sur l'implémentation du noyau (remplacement, test 7) | Implémentation du noyau en `@Component` simple ; celle du plug-in en `@Component @Primary` |
| Événement CDI observé après commit | `ApplicationEventPublisher.publishEvent` + `@TransactionalEventListener` |
| `@QuarkusTest @WithTestResource(EmbeddedPostgresResource::class)` | `@SpringBootTest(webEnvironment = RANDOM_PORT) @ContextConfiguration(initializers = [EmbeddedPostgresInitializer::class])` |

Un nouveau module demande en plus :

1. Un paquet `dev.testia.mono6.<module>` (sous-paquet direct : c'est ce qui en fait un module).
2. `src/main/java/dev/testia/mono6/<module>/package-info.java` :

   ```java
   @org.springframework.modulith.ApplicationModule(
       displayName = "Extension xxx (ext-backend-N)",
       allowedDependencies = {"core::api", "core::spi"})
   package dev.testia.mono6.xxx;
   ```

   Le code qui ne doit pas être utilisé par les autres modules va dans un sous-paquet `internal`.
   Un sous-paquet à publier porte `@NamedInterface("nom")` dans son propre `package-info.java`.
3. Dans chaque assemblage qui l'inclut, ajouter le module à la liste `EXPECTED` de
   `ModularityTest`. Si `verify()` échoue, corriger la dépendance ou la déclarer ; ne pas
   désactiver le test.

## 5. Avant de commiter

```bash
./gradlew test                       # tous les assemblages, direct et REST
grep -rniE '<noms des extensions>' core-backend/src checking/src client/src core-frontend/src
```

Et vérifiez à la main :

- [ ] chaque `x-frontend` n'a qu'une dépendance de projet, `x` ;
- [ ] aucun script SQL déjà appliqué n'a été modifié ;
- [ ] les nouvelles règles, métriques et champs portent le préfixe de leur module ;
- [ ] l'IHM a été regardée dans un navigateur si un plug-in a changé.
