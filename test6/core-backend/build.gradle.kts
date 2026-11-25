plugins { `java-library` }

val springdocVersion: String by project

dependencies {
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework:spring-webflux")                        // WebClient : clients REST (flux SSE compris)
    api("org.springframework.modulith:spring-modulith-api")          // @ApplicationModule, @NamedInterface
    api("tools.jackson.module:jackson-module-kotlin")
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("io.swagger.core.v3:swagger-annotations:3.0.0")     // documente le format des erreurs
    runtimeOnly("org.postgresql:postgresql")
}
