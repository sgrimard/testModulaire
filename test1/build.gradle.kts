plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.allopen") version "2.4.10" apply false
    id("io.quarkus") version "3.31.4" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
