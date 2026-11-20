plugins { `java-library` }

dependencies {
    implementation(project(":core-frontend"))  // le plug-in UI dépend du noyau UI, jamais l'inverse
}
