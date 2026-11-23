---
name: contribution-calcul
description: Ajoute une contribution au calcul de checking (module ext-checking-*, interface ProductCheck) qui complète le rapport de contrôle avec des métriques et des constats tirés d'une ou plusieurs extensions de données. À utiliser quand on demande un nouveau contrôle, une nouvelle règle, un indicateur ou une métrique dans le rapport, sans modifier checking ni le client.
---

# Ajouter une contribution au calcul

Référence : `doc/manuel-developpeur.md`, section 4.2. Exemples dans `test4` : `ext-checking-1`
(une extension) et `ext-checking-3` (deux extensions).

## Avant de commencer

- Identifiez les données nécessaires : champs du noyau (`ProductDto`) et extensions de données.
  Si une donnée manque, créez d'abord l'extension (skill `extension-donnees`).
- Choisissez un **nom** court (`landed`) : il préfixe les métriques (`landed.cost`) et les règles
  (`landed.margin-absorbed`).
- Ne modifiez ni `checking`, ni `client`, ni `checking-frontend`.

## Étapes

1. **Module** `ext-checking-N/` :
   - `build.gradle.kts` : `implementation(project(":checking"))` et une ligne
     `implementation(project(":ext-backend-X"))` par extension lue. Rien d'autre.
   - `META-INF/beans.xml` (copie) et ajout à `settings.gradle.kts`.
2. **Classe** `dev.testia.mono4.checking.<nom>.<Nom>Check` :

   ```kotlin
   @ApplicationScoped
   class LandedCostCheck : ProductCheck {
       override val name = "landed"
       override val order = 200          // 100 si elle ne lit qu'une extension
       override fun evaluate(product: ProductDto): CheckResult { … }
   }
   ```

   - Lire les extensions par leur API typée : `product.shipping()`, `product.supplier()`.
   - Renvoyer `CheckResult(metrics, findings)`. `Metric(code, label, value, unit)` avec `unit` en
     `"€"` ou `"%"` ; `Finding(rule, severity, message, penalty)`.
   - Pénalités : `INFO` 0 ; `WARNING` 5 à 20 ; `BLOCKING` 40 à 50.
   - Donnée manquante déjà signalée par une autre contribution : `return CheckResult()`.
3. **Assemblages** : ajouter le module aux `app*` qui contiennent toutes les extensions qu'il lit.
   Gradle l'impose : un assemblage sans elles ne compile pas.
4. **Tests** (`Mode.entries.forEach`) dans l'assemblage complet :
   - `report.contributors` contient le nom ;
   - valeurs des métriques sur un cas calculé à la main ;
   - les constats attendus (`report.findings.map { it.rule }`), le score et le statut.

## Interdits

- État mutable, écriture en base, appel REST, horloge : `evaluate` est une fonction pure.
- Lire le résultat d'une autre contribution, ou dépendre d'un autre module `ext-checking-*`.
- Accéder aux tables ou entités d'une extension.

## Vérifier

```bash
./gradlew test
grep -rni '<nom>' checking/src client/src checking-frontend/src   # aucun résultat
```

L'IHM affiche le nouveau contenu sans modification. Ouvrez le détail d'un produit pour le constater.
