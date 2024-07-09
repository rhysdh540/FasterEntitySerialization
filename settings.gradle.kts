pluginManagement {
    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.wagyourtail.xyz/releases")
        gradlePluginPortal() {
            content {
                excludeGroup("org.apache.logging.log4j")
            }
        }
    }
}

plugins {
    id("xyz.wagyourtail.unimined") version("1.2.14") apply(false)
    id("com.github.johnrengelman.shadow") version("8.1.1") apply(false)
}

rootProject.name = "FasterEntitySerialization"