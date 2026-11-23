plugins { `java-library` }

// Brique verticale : l'IHM du noyau ne dépend que de son backend.
dependencies {
    implementation(project(":core-backend"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-kotlin")
}
