plugins { `java-library` }

dependencies {
    implementation(project(":checking"))
    implementation(project(":ext-backend-1"))   // complète le calcul avec les données d'ext-backend-1
}
