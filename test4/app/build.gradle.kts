plugins { id("io.quarkus") }

dependencies {
    implementation(project(":core-backend"))
    implementation(project(":checking"))
    implementation(project(":client"))
    implementation(project(":ext-backend-1"))
    implementation(project(":ext-backend-2"))
    implementation(project(":ext-checking-1"))
    implementation(project(":ext-checking-2"))
    implementation(project(":ext-checking-3"))
    implementation("io.quarkus:quarkus-kotlin")

    testImplementation(project(":pg-embedded"))
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.rest-assured:rest-assured")
}
