# test5 : socle du test 4, IHM React/TypeScript dans Electron

Même backend que le test 4 (données et calcul extensibles). L'IHM est réécrite en
TypeScript/React et livrée comme application de bureau Electron (Windows et Linux).

## Organisation

| Dossier | Type | Rôle | Dépend de |
| --- | --- | --- | --- |
| `core-backend`, `checking`, `ext-*`, `client`, `app*` | Gradle | Backend, identique au test 4 (ports 8094 / 8096 / 8098, base 5435) | — |
| `core-frontend` | npm | IHM du noyau : liste et formulaire produits pilotés par le schéma, registre de plug-ins | API de `core-backend` |
| `checking-frontend` | npm | Plug-in : carte « Contrôle » du détail produit, mise à jour en direct | API de `checking` |
| `desktop` | npm | Assemblage : réunit l'IHM du noyau et ses plug-ins, application Electron | `core-frontend`, `checking-frontend` |
| `tools/openapi-slice.mjs` | outil | Génère les types TypeScript d'une brique à partir du contrat OpenAPI | — |

### Brique verticale en TypeScript

- Chaque frontend ne connaît que l'API REST **de son backend** : ses types sont générés depuis le
  contrat OpenAPI publié par `app-core`, en ne gardant que ses propres chemins (`/api/products` ou
  `/api/checking`). Fichiers générés : `src/api/schema.ts`.
- `checking-frontend` **n'importe rien** de `core-frontend`. Il déclare dans `src/host.ts` la forme
  du registre qu'il attend. `desktop` passe le plug-in à l'IHM du noyau : TypeScript compare alors
  les deux formes. Si l'hôte ne fournit plus ce que le plug-in attend, `desktop` ne compile plus.
  (Dans le test 4, en JavaScript, ce contrat n'était vérifié qu'à l'exécution.)
- Comme les assemblages `app*` côté backend, `desktop` est le seul endroit qui choisit les plug-ins
  (`src/renderer/main.tsx`).

## Commandes

Prérequis : JDK 25, Node 22, npm 10 ou 11.

```bash
# Backend
./gradlew test                                # tests backend (12 + CORS + OpenAPI)
./gradlew build installDist -x test
pg-embedded/build/install/pg-embedded/bin/pg-embedded 5435 &
java -jar app/build/quarkus-app/quarkus-run.jar &          # http://localhost:8094

# IHM
npm install
npm run api          # régénère les types depuis app-core/build/openapi/openapi.json (après ./gradlew :app-core:quarkusBuild)
npm run typecheck    # inclut la vérification du contrat entre l'IHM du noyau et ses plug-ins
npm test             # tests Vitest
npm run dev          # IHM dans un navigateur : http://<hôte>:5173/?backend=http://<hôte>:8094
npm run dist         # application Electron : desktop/release/*.AppImage et *-win-x64.zip
```

Le `.npmrc` active `legacy-peer-deps` : npm 10.9 plante sinon sur les dépendances « pair » de
Vitest dans un espace de travail. Les outils de test sont donc déclarés explicitement à la racine.

## Lancer l'application de bureau

- **Windows** : décompresser `catalogue-test5-<version>-win-x64.zip`, puis lancer
  `catalogue-test5.exe`. L'exécutable n'est pas signé : Windows SmartScreen peut demander une
  confirmation.
- **Linux** : `chmod +x catalogue-test5-<version>-linux-x86_64.AppImage`, puis le lancer.

Le backend visé est `http://localhost:8094` par défaut. Pour un autre serveur :
`catalogue-test5.exe --backend=http://serveur:8094`, ou la variable d'environnement `BACKEND_URL`.

L'IHM est chargée par le protocole `app://desktop` ; le backend autorise cette origine (et le
serveur Vite sur le port 5173) dans sa configuration CORS (`application.properties`).
