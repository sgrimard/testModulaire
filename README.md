# testModulaire

Prototypes de monolithe modulaire Kotlin / Quarkus / PostgreSQL : comment ajouter des fonctions à
une application (champs, traitements, calculs, écrans) sans modifier son noyau.

| Dossier | Modèle |
| --- | --- |
| `test1` | Services découverts par nom |
| `test2` | Champs d'extension et spécialisation des traitements |
| `test3` | Test 2 + règles des champs modifiables par une extension |
| `test4` | Données et calcul extensibles (socle recommandé) |

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
