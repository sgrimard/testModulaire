pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "testModulaire"

include("server", "client", "ext-one", "ext-two", "frontend", "app", "app-core")
