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

buildscript {
    repositories.mavenCentral()
    dependencies {
        classpath("com.guardsquare:proguard-base:${providers.gradleProperty("proguard_version").get()}")
        classpath("net.fabricmc:mapping-io:0.3.0") // update when unimined switches to its own mapping library
    }
}

plugins {
    id("xyz.wagyourtail.unimined") version("1.2.14") apply(false)
    id("com.github.johnrengelman.shadow") version("8.1.1") apply(false)
}

rootProject.name = "FasterEntitySerialization"