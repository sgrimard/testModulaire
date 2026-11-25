import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.spring") version "2.4.10" apply false
    kotlin("plugin.jpa") version "2.4.10" apply false
    id("org.springframework.boot") version "4.2.0" apply false
}

val springBootVersion: String by project
val springModulithVersion: String by project

subprojects {
    repositories { mavenCentral() }

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring") // classes @Component, @Configuration… ouvertes
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")    // constructeur sans argument des @Entity

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "implementation"(platform("org.springframework.modulith:spring-modulith-bom:$springModulithVersion"))
    }

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
        compilerOptions {
            javaParameters.set(true)
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    tasks.withType<Test> { useJUnitPlatform() }
}
