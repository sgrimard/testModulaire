# testModulaire

Prototypes de monolithe modulaire Kotlin / Quarkus (tests 1 à 5) ou Spring Boot + Spring Modulith
(tests 6 et 7) / PostgreSQL : comment ajouter des fonctions à
une application (champs, traitements, calculs, écrans) sans modifier son noyau.

| Dossier | Modèle |
| --- | --- |
| `test1` | Services découverts par nom |
| `test2` | Champs d'extension et spécialisation des traitements |
| `test3` | Test 2 + règles des champs modifiables par une extension |
| `test4` | Données et calcul extensibles (socle recommandé) |
| `test5` | Test 4 avec une IHM React/TypeScript livrée dans Electron (Windows, Linux) |
| `test6` | Test 5 sur Spring Boot + Spring Modulith : règles d'architecture vérifiées par un test |
| `test7` | Test 3 sur Spring Boot + Spring Modulith |

## Documentation

- [Note d'architecture](doc/architecture.md) : quel modèle choisir, comparaison, risques.
- [Manuel du développeur](doc/manuel-developpeur.md) : règles et recettes pour écrire les modules.
- [Instructions pour l'IA](doc/instructions-ia.md) : `AGENTS.md`, `CLAUDE.md` et les skills de
  `.claude/skills/`.

## Démarrage rapide

```bash
cd test4
./gradlew test          # JDK 25 ; PostgreSQL embarqué, sans Docker
```
