pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "test6"

include(
    "core-backend", "ext-backend-1", "ext-backend-2",           // données : noyau + extensions
    "checking", "ext-checking-1", "ext-checking-2", "ext-checking-3", // calcul : noyau + extensions
    "client",                                                   // client tiers (ne voit que checking)
    "pg-embedded",                                              // PostgreSQL embarqué (tests + lancement local)
    "app", "app-ext1", "app-core",                              // assemblages : complet / partiel / noyau seul
)
