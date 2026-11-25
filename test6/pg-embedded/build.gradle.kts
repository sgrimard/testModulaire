plugins {
    `java-library`
    application
}

dependencies {
    api("io.zonky.test:embedded-postgres:2.1.0")
    api("org.springframework:spring-context")   // ApplicationContextInitializer pour les tests
}

application { mainClass.set("dev.testia.devdb.DevDatabaseKt") }

tasks.named<JavaExec>("run") { standardInput = System.`in` }
