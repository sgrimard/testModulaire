plugins { `java-library` }

dependencies {
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework:spring-webflux")                        // WebClient : client REST du contrat
    api("org.springframework.modulith:spring-modulith-api")          // @ApplicationModule, @NamedInterface
    api("tools.jackson.module:jackson-module-kotlin")
    api("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
}
