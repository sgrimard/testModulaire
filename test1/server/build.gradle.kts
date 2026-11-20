plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    id("java-library")

}

val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))
    implementation(enforcedPlatform("org.jetbrains.kotlin:kotlin-bom:2.4.10"))
    api("io.quarkus:quarkus-arc")
    api("io.quarkus:quarkus-rest-client-jackson")
    api("org.eclipse.microprofile.rest.client:microprofile-rest-client-api")
    api("io.smallrye.reactive:mutiny")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-kotlin")
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
