import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.internal.file.copy.DefaultCopySpec
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.util.capitalized
import xyz.wagyourtail.unimined.util.getField

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
    archivesName = "fes"
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

val accessWidenerPath = "src/main/resources/fes.aw"

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
        mixinConfig("fes.mixins.json")
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

tasks.withType<AbstractArchiveTask> {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.named<AbstractArchiveTask>("forgeJar") {
    destinationDirectory = layout.buildDirectory.dir("devlibs")
    exclude("*.aw")
}
tasks.named<AbstractArchiveTask>("fabricJar") {
    destinationDirectory = layout.buildDirectory.dir("devlibs")
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
            rename { if(it == "fes.mixins.json") "fes-$platform.mixins.json" else it }
            includeEmptyDirs = false
        }

        archiveClassifier.set(preShadowTaskName)
        destinationDirectory = temporaryDir

        relocate("dev.rdh.fes", "dev.rdh.fes.$platform")
    }
}

setupShadow("fabric")
setupShadow("forge")

tasks.jar {
    this.clearSourcePaths()
    dependsOn("fabricPreShadow", "forgePreShadow")

    val oldMixinConfig = "fes.mixins.json"
    fun newMixinConfig(platform: String) = "fes-$platform.mixins.json"

    val oldMixinPackage = "dev.rdh.fes.mixin"
    fun newMixinPackage(platform: String) = "dev.rdh.fes.$platform.mixin"

    from(zipTree((tasks["fabricPreShadow"] as ShadowJar).archiveFile)) {
        includeEmptyDirs = false
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
        includeEmptyDirs = false
        filesMatching(newMixinConfig("forge")) {
            filter { it.replace(oldMixinPackage, newMixinPackage("forge")) }
        }
    }

    archiveClassifier = ""

    manifest {
        attributes["MixinConfigs"] = newMixinConfig("forge")
    }
}

fun Jar.clearSourcePaths() {
    AbstractCopyTask::class.java.getDeclaredField("mainSpec").let {
        it.isAccessible = true
        val thing = it.get(this) as DefaultCopySpec
        thing.sourcePaths.clear()
        it.isAccessible = false
    }
}