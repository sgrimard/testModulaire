# test7 : le test 3 sur Spring Boot + Spring Modulith

Même modèle que le test 3 (qui inclut le test 2) : un plug-in ajoute des champs persistants au noyau,
spécialise ses traitements (validation, devis, libellé) et modifie les valeurs valides de ses champs
(`FieldPolicy`). Le backend passe de Quarkus à **Spring Boot 4.2** et **Spring Modulith 2.2** ;
l'IHM JavaScript et les tests sont inchangés.

## Organisation

| Dossier | Module Spring Modulith (paquet) | Rôle | Dépendances autorisées |
| --- | --- | --- | --- |
| `core-backend` | `core` | Noyau : produit, schéma effectif, traitements | aucune |
| `core-frontend` | `ui` | IHM du noyau (fichiers statiques) + découverte des plug-ins d'IHM | aucune |
| `ext-backend` | `shipping` | Plug-in : champs, table, règles des champs, devis, libellé | `core::api`, `core::spi` |
| `ext-frontend` | — (pas de code Kotlin) | IHM du plug-in, déclarée par `META-INF/ui-plugin.properties` | — |
| `client` | `client` | Client tiers | `core::api`, `core::rest` |
| `app`, `app-core` | — (paquet racine) | Assemblages : avec plug-in (8106), sans (8108) ; base 5437 | — |
| `pg-embedded` | — (`dev.testia.devdb`) | PostgreSQL embarqué | — |

Le noyau publie `api` (contrat, DTO), `spi` (points d'extension) et `rest` (contrat REST et client) ;
`service` et `persistence` sont internes. Le plug-in ne publie rien : tout son code est dans
`shipping.internal`. `ModularityTest` vérifie ces règles dans chaque assemblage.

## Ce qui a changé par rapport au test 3

- **Remplacement d'un traitement du noyau.** Quarkus désactivait `CoreLabelFormatter` (`@DefaultBean`)
  dès qu'un plug-in fournissait un `LabelFormatter`. Spring n'a pas d'équivalent fiable pour les
  classes du scan (`@ConditionalOnMissingBean` n'est sûr que dans une auto-configuration) : le
  plug-in marque son implémentation **`@Primary`**. Sans plug-in, l'implémentation du noyau est la
  seule ; avec deux plug-ins `@Primary`, le démarrage échoue, comme avec Quarkus.
- **`coreLabel()` déplacé du service vers le SPI.** Le plug-in réutilise le libellé du noyau. Cette
  fonction était dans `core/service`, que Spring Modulith considère comme interne : le plug-in en
  dépendait sans que rien ne le signale. Elle est maintenant publiée dans `core/spi`.
- **Paquet du plug-in renommé** de `ext.shipping` en `shipping` : un module Spring Modulith est un
  sous-paquet direct de l'application, sinon le module se serait appelé `ext`.
- **IHM.** Spring Boot sert les fichiers de `META-INF/resources` des jars comme Quarkus : `index.html`,
  `core/*.js` et `plugins/shipping/plugin.js` ne changent pas. `UiPluginResource` devient un
  `@RestController`. Spring Modulith ne vérifie que le code Kotlin : la règle « `ext-frontend` ne
  dépend que de `ext-backend` » reste tenue par Gradle et par le registre JavaScript.
- **Équivalences** (détaillées dans `test6/README.md`) : `@Component`/`@Service`, `ObjectProvider`,
  contrat REST en interface `@HttpExchange`, `@RestControllerAdvice`, service `@Primary`, module
  Kotlin de Jackson 3 sans `StrictNullChecks`, `@SpringBootTest` + `EmbeddedPostgresInitializer`.

`FieldPolicy` (les règles des champs du noyau modifiées par le plug-in) ne demande aucune
adaptation : c'est un point d'extension comme les autres.

## Commandes

```bash
./gradlew test                     # 14 tests : les 9 du test 3 + 5 de modularité
./gradlew build installDist -x test
pg-embedded/build/install/pg-embedded/bin/pg-embedded 5437 &
java -jar app/build/libs/app.jar &          # http://localhost:8106 (sans plug-in : app-core, 8108)
```

L'IHM est servie à la racine de l'application. Les deux applications peuvent partager la base ; le
migrateur prend un verrou PostgreSQL, elles peuvent donc démarrer en même temps.
