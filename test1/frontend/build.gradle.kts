plugins { `java-library` }

// Brique verticale : l'IHM ne dépend que de son backend (server), dont elle appelle l'API REST.
// Ressources statiques uniquement (META-INF/resources), servies par Quarkus depuis le jar.
dependencies {
    implementation(project(":server"))
}
