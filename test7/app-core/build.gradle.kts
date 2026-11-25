plugins { id("org.springframework.boot") }

dependencies {
    implementation(project(":core-backend"))
    implementation(project(":core-frontend"))
    implementation(project(":client"))
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    testImplementation(project(":pg-embedded"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-docs")
    testImplementation("io.rest-assured:rest-assured:6.3.0")
}

tasks.named<Jar>("jar") { enabled = false } // seul le jar exécutable (bootJar) est produit
