plugins { `java-library` }

dependencies {
    implementation(project(":core-backend"))   // le plug-in dépend du noyau, jamais l'inverse
}
