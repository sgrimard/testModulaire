pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "test3"

include(
    "core-backend", "ext-backend",     // backend : noyau + plug-in
    "core-frontend", "ext-frontend",   // frontend : noyau + plug-in
    "client",                          // client tierce partie (ne voit que core-backend)
    "pg-embedded",                     // PostgreSQL embarqué (tests + lancement local)
    "app", "app-core",                 // assemblages : avec / sans plug-in
)
