plugins {
    `java-library`
    application
}

dependencies {
    api("io.zonky.test:embedded-postgres:2.1.0")
    api("io.quarkus:quarkus-test-common")
}

application { mainClass.set("dev.testia.mono3.pg.DevDatabaseKt") }

tasks.named<JavaExec>("run") { standardInput = System.`in` }
