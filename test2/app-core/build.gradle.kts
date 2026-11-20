plugins { id("io.quarkus") }

dependencies {
    implementation(project(":core-backend"))
    implementation(project(":core-frontend"))
    implementation(project(":client"))
    implementation("io.quarkus:quarkus-kotlin")

    testImplementation(project(":pg-embedded"))
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.rest-assured:rest-assured")
}
