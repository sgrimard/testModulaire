plugins { `java-library` }

// Brique verticale : l'IHM du plug-in ne dépend que de son backend. Elle ne dépend pas de
// core-frontend : elle s'y branche à l'exécution (descripteur META-INF/ui-plugin.properties
// + module ES qui reçoit le registre de l'IHM du noyau).
dependencies {
    implementation(project(":ext-backend"))
}
