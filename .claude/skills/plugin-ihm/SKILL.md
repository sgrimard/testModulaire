---
name: plugin-ihm
description: Ajoute ou modifie un plug-in d'IHM (module *-frontend) qui s'insère dans l'IHM du noyau par le registre JavaScript (éditeur de champ, format d'affichage, titre de groupe, section du détail produit), en brique verticale - le frontend ne dépend que de son module backend. À utiliser pour toute demande d'affichage, d'écran ou de composant d'interface lié à une extension ou à checking.
---

# Ajouter un plug-in d'IHM

Référence : `doc/manuel-developpeur.md`, section 4.6. Exemple : `test4/checking-frontend`
(section « Contrôle » mise à jour en direct). Autre exemple : `test3/ext-frontend` (éditeur et
format de champs).

## Règle de la brique verticale

`x-frontend` dépend **uniquement** de `x` (son backend). Jamais de `core-frontend`, jamais d'un
autre frontend. Le lien avec l'IHM du noyau est un contrat d'exécution :
`META-INF/ui-plugin.properties` + `register(registry)`.

## Étapes

1. **Module** `x-frontend/` :
   - `build.gradle.kts` : ``plugins { `java-library` }`` et `implementation(project(":x"))`.
   - Pas de code Kotlin ni de `beans.xml` : uniquement des ressources.
   - Ajouter à `settings.gradle.kts`, puis aux assemblages qui contiennent `x`.
2. **Descripteur** `src/main/resources/META-INF/ui-plugin.properties` :

   ```properties
   id=<x>
   module=/plugins/<x>/plugin.js
   ```

3. **Module ES** `src/main/resources/META-INF/resources/plugins/<x>/plugin.js` :

   ```js
   export function register(registry) {
     registry.namespaceTitle('<ns>', 'Titre du groupe');
     registry.fieldFormatter('<ns>.<champ>', (v) => …);                  // cellule de la liste
     registry.fieldEditor('<ns>.<champ>', (d, value) => ({ element, value: () => … }));
     registry.section('product-detail', (product) => element || null);  // bloc du détail
   }
   ```

   - N'importer **aucun** fichier de `/core/…` ; tout passe par `registry`.
   - Appeler l'API REST de son backend (`/api/<x>/…`) ; `product.extensions.<ns>` pour les champs.
   - Section : renvoyer `null` si les données sont absentes.
   - Styles : une balise `<style>` injectée par `register`, avec les variables du noyau
     (`--line`, `--muted`, `--warn`, `--danger`, `--accent`, `--accent-soft`) pour le mode sombre.
   - Direct : un seul `EventSource` par plug-in, créé dans `register`, partagé par les sections ;
     ignorer une section qui n'est plus dans la page (`element.isConnected`).

## Vérifier

1. Test d'assemblage : `GET /ui/plugins` contient `<x>` et `GET /plugins/<x>/plugin.js` répond 200.
2. `./gradlew test`.
3. **Dans un navigateur** (le contrat JS n'est vérifié par aucun compilateur) : construire
   (`./gradlew build installDist -x test`), lancer la base et l'application (voir AGENTS.md), ouvrir
   `http://<nom-du-conteneur>:<port>/`, sélectionner un produit, vérifier l'affichage. La console
   ne doit contenir aucune erreur hormis `favicon.ico`.
4. `build.gradle.kts` du frontend : une seule dépendance `project(...)`.
