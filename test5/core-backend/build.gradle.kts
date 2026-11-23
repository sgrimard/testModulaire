plugins { `java-library` }

dependencies {
    api("io.quarkus:quarkus-arc")
    api("io.quarkus:quarkus-hibernate-orm")
    api("io.quarkus:quarkus-jdbc-postgresql")
    api("io.quarkus:quarkus-rest-client-jackson")
    api("org.eclipse.microprofile.rest.client:microprofile-rest-client-api")
    api("io.smallrye.reactive:mutiny")
    api("io.quarkus:quarkus-rest")
    api("org.eclipse.microprofile.openapi:microprofile-openapi-api") // documente le format des erreurs
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-kotlin")
}
