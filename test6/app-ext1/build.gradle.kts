plugins { id("org.springframework.boot") }

val springdocVersion: String by project

dependencies {
    implementation(project(":core-backend"))
    implementation(project(":checking"))
    implementation(project(":client"))
    implementation(project(":ext-backend-1"))
    implementation(project(":ext-checking-1"))
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    testImplementation(project(":pg-embedded"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-docs")
}

tasks.named<Jar>("jar") { enabled = false } // seul le jar exécutable (bootJar) est produit
