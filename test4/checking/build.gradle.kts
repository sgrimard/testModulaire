plugins { `java-library` }

dependencies {
    api(project(":core-backend"))   // checking lit les données du noyau, sans connaître ses extensions
}
