---
name: extension-donnees
description: Ajoute une extension de données (module ext-backend-*) qui ajoute des champs persistants au produit de core-backend, avec sa table, ses contraintes et son API typée. À utiliser quand on demande d'ajouter des champs, des attributs, des données ou une table liés à l'objet du noyau, sans modifier core-backend.
---

# Ajouter une extension de données

Référence complète : `doc/manuel-developpeur.md`, section 4.1. Exemple à copier :
`test4/ext-backend-2` (namespace `supplier`).

## Avant de commencer

- Travaillez dans le test visé (par défaut `test4`).
- Choisissez un **namespace** court, en minuscules, unique (`supplier`, `shipping`) : il nomme
  l'extension, préfixe ses champs (`supplier.unitCost`), sa table (`supplier_product`) et son
  dossier SQL.
- Ne modifiez **aucun fichier de `core-backend`**. Si le besoin l'exige, arrêtez-vous et expliquez
  pourquoi.

## Étapes

1. **Module** `ext-backend-N/` :
   - `build.gradle.kts` : ``plugins { `java-library` }`` et `api(project(":core-backend"))`, rien d'autre.
   - `src/main/resources/META-INF/beans.xml` : copie de celui de `core-backend`.
   - Ajouter `"ext-backend-N"` à `settings.gradle.kts`.
2. **SQL** `src/main/resources/db/<ns>/001_<ns>_product.sql` : table `<ns>_product` avec
   `product_id bigint primary key references product (id)` et des colonnes nullables.
3. **Code** dans `src/main/kotlin/dev/testia/monoN/<ns>/` :
   - `<Ns>Api.kt` : `const val <NS> = "<ns>"`, une `data class <Ns>Info(...)` typée et
     `fun ProductDto.<ns>(): <Ns>Info` qui lit `extensions[<NS>]` (nombres reçus en `Number`).
   - `<Ns>Extension.kt` :
     - `<Ns>Schema : SchemaContribution` (`module = <NS>`, `order = 100`, la liste des scripts) ;
     - l'entité JPA `@Entity @Table(name = "<ns>_product")` avec `@Id productId` ;
     - `<Ns>Extension : ProductExtension` : `namespace = <NS>`, `fields()` avec les contraintes
       (`required`, `min`, `max`, `options`), `load(ids)` en une requête, `save` qui crée ou met à
       jour, `delete`. Les nombres arrivent en `Double` dans `save`.
4. **Assemblages** : ajoutez `implementation(project(":ext-backend-N"))` aux modules `app*` qui
   doivent l'avoir (skill `assemblage`).
5. **Tests** dans l'assemblage complet (`Mode.entries.forEach`) :
   - `ops.extensions()` contient le namespace ;
   - `ops.extension("<ns>").put(id, …)` puis `get` renvoie les valeurs ;
   - une valeur hors contraintes lève `ProductValidationException` sur `<ns>.<champ>`.

## Vérifier

```bash
./gradlew test
grep -rni '<ns>' core-backend/src checking/src client/src core-frontend/src   # aucun résultat
```

Sans autre code, le formulaire de l'IHM affiche les nouveaux champs : regardez-le si l'IHM est
concernée.

## Pièges

- Ne jamais modifier un script SQL déjà commité : ajouter `002_….sql` et l'ajouter à `scripts`.
- Les autres modules lisent ces données **uniquement** par `ProductDto.<ns>()`.
- Un champ obligatoire (`required = true`) rend invalides les produits existants qui n'ont pas de
  valeur : préférez un champ facultatif, ou prévoyez une migration qui remplit la colonne.
