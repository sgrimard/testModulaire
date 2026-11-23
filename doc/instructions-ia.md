# Instructions pour les agents IA

Comment les agents de codage (Claude Code, et les autres outils qui lisent `AGENTS.md`) apprennent à
appliquer les modèles de la [note d'architecture](architecture.md) dans ce dépôt.

## Ce qui est en place

| Fichier | Rôle | Quand l'agent le lit |
| --- | --- | --- |
| `AGENTS.md` (racine) | Règles non négociables, carte du dépôt, commandes, vérifications | À chaque session |
| `CLAUDE.md` (racine) | Importe `AGENTS.md` pour Claude Code (`@AGENTS.md`) | À chaque session Claude Code |
| `.claude/skills/<nom>/SKILL.md` | Une recette pas à pas par type de tâche | Quand la tâche correspond à la description du skill |
| `doc/manuel-developpeur.md` | Version détaillée pour les humains, citée par les skills | À la demande |

Les cinq skills :

| Skill | Tâche |
| --- | --- |
| `extension-donnees` | Ajouter des champs persistants au produit (module `ext-backend-*`) |
| `contribution-calcul` | Compléter le rapport de `checking` (module `ext-checking-*`) |
| `specialisation-noyau` | Validation, étape, remplacement (test 2) ou règles d'un champ (test 3) |
| `plugin-ihm` | Plug-in d'IHM en brique verticale (module `*-frontend`) |
| `assemblage` | Créer ou modifier une application `app-*` et ses tests |

## Pourquoi ce découpage

- **Les règles vont dans `AGENTS.md`, toujours chargé.** Une règle comme « le noyau ne dépend
  d'aucune extension » doit s'appliquer même quand la demande ne parle pas d'architecture. Ce
  fichier reste court, car il est lu à chaque session.
- **Les recettes vont dans des skills, chargés à la demande.** Elles sont longues et ne servent
  qu'à une tâche précise. Un skill n'occupe le contexte que lorsque sa description correspond à la
  demande, ou quand on l'appelle par `/nom`.
- **`AGENTS.md` plutôt que `CLAUDE.md` seul**, parce que c'est le nom lu par la plupart des outils
  de codage. `CLAUDE.md` se contente de l'importer, pour que Claude Code le charge aussi.
- **Les skills renvoient au manuel** au lieu de le recopier : une seule source détaillée, à tenir à
  jour quand le code change.

## Utilisation

- Lancer l'agent **depuis la racine du dépôt** (`testModulaire/`), pour qu'il trouve `AGENTS.md`,
  `CLAUDE.md` et `.claude/skills/`.
- Formuler la demande en termes métier (« ajoute le pays d'origine au produit et un contrôle sur
  les produits importés ») : l'agent choisit les skills `extension-donnees`, puis
  `contribution-calcul`, puis `assemblage`.
- Pour forcer une recette dans Claude Code : `/extension-donnees`, `/plugin-ihm`, etc.

## Maintenance

- Une nouvelle règle d'architecture : l'ajouter dans `AGENTS.md` **et** dans la section 2 du manuel.
- Un nouveau type d'extension : un nouveau skill, une nouvelle section du manuel, et une ligne dans
  les tableaux de `AGENTS.md` et de cette page.
- Si un exemple cité change de chemin, mettre à jour les skills qui le citent
  (`grep -rn 'test4/' .claude/skills`).
