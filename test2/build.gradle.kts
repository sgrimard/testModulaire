import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.allopen") version "2.4.10" apply false
    kotlin("plugin.jpa") version "2.4.10" apply false
    id("io.quarkus") version "3.31.4" apply false
}

val quarkusPlatformVersion: String by project

subprojects {
    repositories { mavenCentral() }

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.allopen")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")

    dependencies {
        "implementation"(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))
        "implementation"(enforcedPlatform("org.jetbrains.kotlin:kotlin-bom:2.4.10"))
    }

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
        compilerOptions { javaParameters.set(true) }
    }

    extensions.configure<AllOpenExtension> {
        annotation("jakarta.enterprise.context.ApplicationScoped")
        annotation("jakarta.ws.rs.Path")
        annotation("jakarta.persistence.Entity")
        annotation("io.quarkus.test.junit.QuarkusTest")
    }

    tasks.withType<Test> { useJUnitPlatform() }
}
