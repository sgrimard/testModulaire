---
name: specialisation-noyau
description: Spécialise un traitement de core-backend depuis une extension (validation additive ProductValidator, étape de pipeline QuoteStep, remplacement d'un @DefaultBean comme LabelFormatter) ou change les valeurs valides d'un champ du noyau (FieldPolicy : liste, bornes, obligatoire). À utiliser quand une extension doit modifier le comportement ou les règles du noyau sans le modifier.
---

# Spécialiser le noyau depuis une extension

Références : `doc/manuel-developpeur.md`, sections 4.3 et 4.4. Exemples :
`test2/ext-backend/.../ShippingExtension.kt` (traitements) et
`test3/ext-backend/.../ShippingExtension.kt` (`ShippingFieldPolicy`).

Ces points d'extension existent dans les tests 2 et 3, **pas dans le test 4**. Si on vous le demande
sur le test 4, il faut d'abord ajouter le point d'extension au noyau (voir plus bas) : annoncez-le
avant de le faire.

## Choisir le mécanisme

| Besoin | Interface | Style |
| --- | --- | --- |
| Une règle de validation métier en plus | `ProductValidator` | additif : toutes s'appliquent |
| Une ligne ou un ajustement dans un calcul en étapes | `QuoteStep` (`order`) | pipeline : reçoit les lignes précédentes |
| Remplacer un comportement du noyau | `LabelFormatter` | remplacement du bean `@DefaultBean` |
| Changer liste de valeurs, bornes, obligatoire | `FieldPolicy` (`order`) | transformation du schéma |

Une contrainte simple sur **un champ de l'extension elle-même** ne demande aucun de ces mécanismes :
déclarez-la dans `fields()` (`required`, `min`, `max`, `options`).

## Règles par mécanisme

- **Validator** : renvoyer des `Violation(champ, message)` avec la clé du champ (`name`,
  `<ns>.<champ>`) ; ne pas lever d'exception.
- **QuoteStep** : `order` > 0 (l'étape de base est à 0) ; renvoyer une nouvelle liste
  (`lines + …`), codes de ligne préfixés par le namespace.
- **Remplacement** : un seul remplacement par assemblage (sinon bean ambigu au démarrage) ;
  réutiliser la fonction publique du noyau (`coreLabel(product)`) plutôt que la recopier.
- **FieldPolicy** :
  - `field.copy(...)` uniquement ; ne pas changer `namespace`, `name`, `type` (le noyau refuse) ;
  - resserrer ou compléter (`maxOf` / `minOf`, ajout à `options`) pour rester combinable ;
  - `draft` (produit en cours de saisie, ou null) permet des règles qui dépendent d'autres champs ;
  - une valeur existante devenue interdite reste lisible et sera refusée à l'enregistrement : le
    signaler dans le compte rendu à l'utilisateur.

## Ajouter un nouveau point d'extension au noyau

C'est une modification du noyau : demandez confirmation d'abord. Ensuite :

1. Interface dans `core-backend/.../spi/Spi.kt`, avec un KDoc qui donne le style (additif,
   pipeline, remplacement).
2. Injection `Instance<…>` dans le service, tri par `order` si besoin ; pour un remplacement, une
   implémentation `@DefaultBean` dans le noyau.
3. Test dans `app-core` (comportement par défaut) et dans `app` (comportement spécialisé).

## Vérifier

```bash
./gradlew test     # app-core prouve que le noyau seul est inchangé ; app prouve la spécialisation
```

Pour une `FieldPolicy`, ouvrez aussi le formulaire dans l'IHM : listes et bornes doivent suivre le
schéma, y compris en changeant les champs dont elles dépendent.
