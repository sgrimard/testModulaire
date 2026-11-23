plugins { id("io.quarkus") }

dependencies {
    implementation(project(":core-backend"))
    implementation(project(":checking"))
    implementation(project(":client"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-smallrye-openapi")  // contrat REST des deux briques (types TypeScript)

    testImplementation(project(":pg-embedded"))
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.rest-assured:rest-assured")
}
