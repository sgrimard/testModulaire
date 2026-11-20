plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    id("java-library")
    id("io.quarkus")
}

val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))
    implementation(enforcedPlatform("org.jetbrains.kotlin:kotlin-bom:2.4.10"))
    implementation(project(":server"))
    implementation(project(":client"))
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

kotlin {
    jvmToolchain(25)
    compilerOptions { javaParameters = true }
}

allOpen {
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.ws.rs.Path")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

tasks.withType<Test> { useJUnitPlatform() }
