plugins { `java-library` }

dependencies {
    implementation(project(":checking"))
    implementation(project(":ext-backend-1"))   // utilise les données des deux extensions
    implementation(project(":ext-backend-2"))
}
