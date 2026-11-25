plugins { `java-library` }

// Brique verticale : l'IHM du noyau ne dépend que de son backend.
dependencies {
    implementation(project(":core-backend"))
}
