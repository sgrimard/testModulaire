# AGENTS.md

Instructions pour les agents IA qui travaillent dans ce dépôt. Les humains lisent
`doc/manuel-developpeur.md`, qui contient les mêmes règles avec plus d'explications.

## Le dépôt

Prototypes de **monolithe modulaire** Kotlin 2.4 / Quarkus 3.31 / JDK 25 / Gradle 9.6 / PostgreSQL.
Chaque dossier `testN/` est un projet Gradle autonome (son propre `gradlew`) :

| Dossier | Modèle | Modules principaux |
| --- | --- | --- |
| `test1` | Services découverts par nom (SPI `Extension`, get/put, événements SSE) | server, ext-one, ext-two, frontend, client |
| `test2` | Champs d'extension + spécialisation des traitements (`ProductValidator`, `QuoteStep`, `LabelFormatter`) | core-backend, ext-backend, core-frontend, ext-frontend |
| `test3` | Test 2 + règles des champs modifiables (`FieldPolicy`) | idem test 2 |
| `test4` | **Socle recommandé** : données extensibles + calcul extensible (`ProductExtension`, `ProductCheck`) | core-backend, ext-backend-1/2, checking, ext-checking-1/2/3, core-frontend, checking-frontend |
| `test5` | Backend du test 4 ; IHM en paquets npm React/TypeScript, assemblée dans Electron (`desktop`) | idem test 4 + `core-frontend`, `checking-frontend`, `desktop` (npm) |

Le choix entre ces modèles est expliqué dans `doc/architecture.md`. Pour un nouveau développement,
partez du test 4 sauf demande contraire.

## Règles non négociables

1. **Un noyau ne dépend d'aucune extension** : ni dépendance Gradle, ni import, ni nom d'extension
   dans son code. Il découvre les implémentations par `jakarta.enterprise.inject.Instance<Spi>`.
2. **Brique verticale** : un module `x-frontend` n'a qu'une dépendance de projet, son backend `x`.
   Un plug-in d'IHM ne dépend jamais de `core-frontend` ; il se déclare par
   `META-INF/ui-plugin.properties` et reçoit le registre JavaScript à l'exécution.
   En TypeScript (test 5) : le paquet du plug-in n'importe rien de `core-frontend`, déclare
   lui-même la forme du registre qu'il attend (`src/host.ts`), et ses types d'API sont générés
   depuis le contrat OpenAPI de son seul backend (`npm run api`).
3. **Données d'une autre extension = son API typée** (`ProductDto.shipping()`…), jamais sa table ni
   ses entités. Déclarer la dépendance Gradle vers cette extension.
4. **Chaque module possède ses tables** (`src/main/resources/db/<module>/NNN_*.sql` + une
   `SchemaContribution`). Ne jamais modifier un script SQL existant : en ajouter un nouveau.
5. **Un service = un contrat Kotlin** implémenté en appel direct et en REST (interface `XxxApi` qui
   étend `XxxOperations`), avec les mêmes exceptions des deux côtés.
6. **Tests dans les deux modes** : tout comportement visible par un client est testé en
   `Mode.DIRECT` et `Mode.REST`, dans chaque assemblage concerné, sur PostgreSQL embarqué.

Si une demande oblige à enfreindre une de ces règles, arrêtez-vous et expliquez le conflit au lieu
de le contourner.

## Recettes (skills)

Les procédures pas à pas sont dans `.claude/skills/` (format `SKILL.md`, lisible par tout agent) :

| Tâche | Skill |
| --- | --- |
| Ajouter des champs persistants à l'objet du noyau | `.claude/skills/extension-donnees/SKILL.md` |
| Compléter le calcul de `checking` | `.claude/skills/contribution-calcul/SKILL.md` |
| Modifier un traitement ou les règles d'un champ du noyau | `.claude/skills/specialisation-noyau/SKILL.md` |
| Afficher quelque chose dans l'IHM | `.claude/skills/plugin-ihm/SKILL.md` |
| Créer ou modifier un assemblage (module `app-*`) | `.claude/skills/assemblage/SKILL.md` |

Pour un service par nom (test 1) ou un nouveau contrat direct + REST, suivez les sections 4.5 et 4.7
de `doc/manuel-developpeur.md`.

## Construire et tester

```bash
cd testN
./gradlew test                    # tous les modules et assemblages
./gradlew :app:test               # un seul assemblage
./gradlew build installDist -x test
```

- JDK 25 requis. Dans le conteneur de développement :
  `export JAVA_HOME=~/.local/jdk/jdk-25.0.5+8 PATH=$JAVA_HOME/bin:$PATH`.
- Les tests démarrent un PostgreSQL embarqué (pas de Docker) et Quarkus sur le port 8081.
- L'avertissement « bean discovery mode of 'all' » au build est attendu.

Lancer une application pour la regarder :

```bash
pg-embedded/build/install/pg-embedded/bin/pg-embedded <port-base> &   # 5432 test2, 5433 test3, 5434 test4
java -jar app/build/quarkus-app/quarkus-run.jar &
```

Ports HTTP : test1 8080 ; test2 8080 / 8082 ; test3 8084 / 8086 ; test4 8088 (app), 8090 (app-ext1),
8092 (app-core) ; test5 8094 / 8096 / 8098 (base 5435), IHM Vite 5173. Les applications écoutent sur `0.0.0.0`. Si le navigateur tourne dans un autre
conteneur, ouvrez `http://<nom-du-conteneur>:<port>`, pas `localhost`. Pour arrêter une application,
tuez son PID précis : plusieurs tests utilisent la même ligne de commande `java -jar app/...`.

IHM du test 5 (depuis `test5/`) : `npm install`, `npm run api`, `npm run typecheck`, `npm test`,
`npm run dev` (navigateur, port 5173), `npm run dist` (Electron). Electron ne peut pas s'ouvrir dans
un conteneur sans bibliothèques graphiques : tester l'IHM avec `npm run dev` ou `vite preview`.

## Vérifications avant de rendre la main

```bash
./gradlew test
grep -rniE '<noms des extensions>' core-backend/src checking/src client/src core-frontend/src   # commentaires seulement
```

- Chaque `x-frontend` n'a qu'une dépendance `project(...)`.
- Les nouveaux champs, métriques et règles portent le préfixe de leur module.
- Si l'IHM a changé : page ouverte dans un navigateur, console sans autre erreur que `favicon.ico`.

## Conventions

- Code, commentaires et messages en français ; KDoc sur chaque interface publique et SPI.
- Paquets `dev.testia.monoN.<module>` (`mono` pour le test 1).
- Beans `@ApplicationScoped`, injection par constructeur.
- DTO : `data class` avec valeurs par défaut (désérialisation Jackson).
- Messages de commit en français : un titre, puis le pourquoi.
