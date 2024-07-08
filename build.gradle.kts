import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.internal.file.copy.DefaultCopySpec
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.util.capitalized

plugins {
    id("java")
    id("idea")
    id("xyz.wagyourtail.unimined")
    id("com.github.johnrengelman.shadow")
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

tasks.withType<ProcessResources> {
    val replaceProperties = mapOf(
        "version" to project.version,
        "minecraft_version" to "minecraft_version"(),
    )

    inputs.properties(replaceProperties)

    filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml")) {
        expand(replaceProperties)
    }
}

tasks.withType<RemapJarTask> {
    mixinRemap {
        disableRefmap()
    }
}

fun setupShadow(platform: String) {
    val platformCapitalized = platform.capitalized()

    val preShadowTaskName = "${platform}PreShadow"

    val remapJar = tasks.named<RemapJarTask>("remap${platformCapitalized}Jar") {
        archiveClassifier.set(platform)
    }

    tasks.register<ShadowJar>(preShadowTaskName) {
        dependsOn(remapJar)
        outputs.upToDateWhen { false }

        configurations = emptyList()
        from(zipTree(remapJar.get().archiveFile)) {
            rename { if(it == "fastnbt.mixins.json") "fastnbt-$platform.mixins.json" else it }
        }

        archiveClassifier.set(preShadowTaskName)
        destinationDirectory = temporaryDir

        relocate("dev.rdh.fastnbt", "dev.rdh.fastnbt.$platform")
    }
}

setupShadow("fabric")
setupShadow("forge")

tasks.jar {
    this.clearSourcePaths()
    dependsOn("fabricPreShadow", "forgePreShadow")

    val oldMixinConfig = "fastnbt.mixins.json"
    fun newMixinConfig(platform: String) = "fastnbt-$platform.mixins.json"

    val oldMixinPackage = "dev.rdh.fastnbt.mixin"
    fun newMixinPackage(platform: String) = "dev.rdh.fastnbt.$platform.mixin"

    from(zipTree((tasks["fabricPreShadow"] as ShadowJar).archiveFile)) {
        val newMixinConfig = newMixinConfig("fabric")
        val newMixinPackage = newMixinPackage("fabric")

        filesMatching("fabric.mod.json") {
            filter { it.replace(oldMixinConfig, newMixinConfig) }
        }
        filesMatching(newMixinConfig) {
            filter { it.replace(oldMixinPackage, newMixinPackage) }
        }
    }

    from(zipTree((tasks["forgePreShadow"] as ShadowJar).archiveFile)) {
        exclude("*.aw")

        filesMatching(newMixinConfig("forge")) {
            filter { it.replace(oldMixinPackage, newMixinPackage("forge")) }
        }
    }

    archiveClassifier = ""
}

fun Jar.clearSourcePaths() {
    val mainSpecField = AbstractCopyTask::class.java.getDeclaredField("mainSpec")
    mainSpecField.isAccessible = true
    val mainSpec = mainSpecField.get(this) as DefaultCopySpec
    mainSpecField.isAccessible = false

    mainSpec.sourcePaths.clear()
}