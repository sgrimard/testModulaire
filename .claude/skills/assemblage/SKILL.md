---
name: assemblage
description: Crée ou modifie un assemblage Quarkus (module app-*) qui choisit les modules présents dans un exécutable, avec sa configuration et ses tests d'intégration en appel direct et en REST. À utiliser quand on ajoute un module à une application, quand on veut une nouvelle combinaison de modules, ou quand on doit tester une extension dans une application.
---

# Créer ou modifier un assemblage

Référence : `doc/manuel-developpeur.md`, section 4.8. Exemples dans `test4` : `app` (complet),
`app-ext1` (partiel), `app-core` (noyau seul).

## Créer un assemblage

1. **Module** `app-<nom>/build.gradle.kts` :

   ```kotlin
   plugins { id("io.quarkus") }
   dependencies {
       implementation(project(":core-backend"))
       implementation(project(":checking"))
       implementation(project(":client"))
       implementation(project(":core-frontend"))
       implementation(project(":checking-frontend"))
       // extensions retenues, avec leur frontend s'il existe
       implementation("io.quarkus:quarkus-kotlin")
       testImplementation(project(":pg-embedded"))
       testImplementation("io.quarkus:quarkus-junit")
       testImplementation("io.rest-assured:rest-assured")
   }
   ```

   Ajouter `"app-<nom>"` à `settings.gradle.kts`.
2. **Configuration** `src/main/resources/application.properties` : copier celle de `app-ext1` et
   changer `quarkus.http.port` (port libre, voir AGENTS.md) ainsi que les URL `quarkus.rest-client.*`
   qui pointent sur ce port. Garder les variantes `%test.…=http://localhost:8081`.
3. **Test** `src/test/kotlin/dev/testia/mono4/app<nom>/<Nom>AssemblyTest.kt` :
   `@QuarkusTest @WithTestResource(EmbeddedPostgresResource::class)`, et pour chaque comportement
   `Mode.entries.forEach { mode -> … }` :
   - extensions découvertes (`products.extensions()`) ;
   - contributions du rapport (`client.check(mode, id).contributors`) ;
   - plug-ins d'IHM servis (`GET /ui/plugins`) ;
   - une extension absente est refusée (`UnknownExtensionException`, violation « Extension inconnue »).

## Ajouter un module à un assemblage existant

- Ajouter la ligne `implementation(project(":…"))` ; pour une contribution au calcul, vérifier que
  les extensions de données qu'elle lit sont présentes (sinon la compilation échoue, c'est voulu).
- Mettre à jour les assertions de liste du test (`extensions()`, `contributors`, `/ui/plugins`) :
  elles sont exactes et échouent si un module apparaît ou disparaît.

## Vérifier

```bash
./gradlew :app-<nom>:test
./gradlew test            # les autres assemblages ne doivent pas changer
```
