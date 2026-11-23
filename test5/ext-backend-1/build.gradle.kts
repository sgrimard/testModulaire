plugins { `java-library` }

dependencies {
    api(project(":core-backend"))   // l'extension dépend du noyau, jamais l'inverse
}
