plugins { `java-library` }

dependencies {
    api(project(":checking"))       // seule dépendance du client tiers (core-backend vient avec)
}
