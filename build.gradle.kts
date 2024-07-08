plugins {
    id("java")
    id("idea")
    id("xyz.wagyourtail.unimined") version("1.2.14")
}

operator fun String.invoke(): String = rootProject.properties[this] as? String ?: error("Property $this not found")

group = "dev.rdh"
version = "0.1"

base {
    archivesName = "fastnbt"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

idea {
    module {
        isDownloadSources = true
    }
}

sourceSets {
    create("fabric")
    create("forge")
}

repositories {
    mavenCentral()
}

configurations.all {
    resolutionStrategy {
        force("net.fabricmc:sponge-mixin:${"mixin_version"()}")
    }
}

val accessWidenerPath = "src/main/resources/fastnbt.aw"

unimined.minecraft {
    version = "minecraft_version"()

    mappings {
        intermediary()
        mojmap()
        parchment("minecraft_version"(), "parchment_version"())

        devFallbackNamespace("intermediary")
    }

    accessWidener {
        accessWidener(accessWidenerPath)
    }

    runs.off = true
    defaultRemapJar = false
}

unimined.minecraft(sourceSets["fabric"]) {
    combineWith(sourceSets["main"])

    fabric {
        loader("fabric_version"())
        accessWidener(accessWidenerPath)
    }

    runs.off = false
    defaultRemapJar = true
}

unimined.minecraft(sourceSets["forge"]) {
    combineWith(sourceSets["main"])

    neoForge {
        loader("forge_version"())
        mixinConfig("fastnbt.mixins.json")
        accessTransformer(aw2at(accessWidenerPath))
    }

    runs.off = false
    defaultRemapJar = true
}

dependencies {
    // we need this in main where it isn't by default
    implementation("net.fabricmc:sponge-mixin:${"mixin_version"()}")
    implementation("io.github.llamalad7:mixinextras-common:0.3.5")

    "fabricModImplementation"("net.fabricmc.fabric-api:fabric-api:${"fabric_api_version"()}+${"minecraft_version"()}")
}

tasks.jar {
    enabled = false
}

val replaceProperties = mapOf(
        "version" to project.version,
        "minecraft_version" to "minecraft_version"(),
)

(tasks["processFabricResources"] as ProcessResources).apply {
    inputs.properties(replaceProperties)

    filesMatching("fabric.mod.json") {
        expand(replaceProperties)
    }
}

(tasks["processForgeResources"] as ProcessResources).apply {
    inputs.properties(replaceProperties)

    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
}