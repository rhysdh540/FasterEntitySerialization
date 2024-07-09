@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.internal.file.copy.DefaultCopySpec
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.util.capitalized
import xyz.wagyourtail.unimined.util.getField

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import proguard.ConfigurationParser
import proguard.ProGuard
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.Deflater

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
        parchment(version = "parchment_version"())

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

val JAVA_HOME: String = System.getProperty("java.home") ?: error("java.home not set")

tasks.register("squishJar") {
    dependsOn(tasks.jar)
    group = "build"

    doLast {
        val jar = tasks.jar.get().archiveFile.get().asFile
        val proguardInJar = jar.copyTo(File(jar.parentFile, "._proguard-in.jar"), true)

        //region ProGuard
        val proguardConfig = mutableListOf(
            "@${file("proguard.pro").absolutePath}",
            "-injars ${proguardInJar.absolutePath}",
            "-outjars ${jar.absolutePath}",
        )

        val libraries = HashSet<String>()
        libraries.add("${JAVA_HOME}/jmods/java.base.jmod")
        libraries.add("${JAVA_HOME}/jmods/java.desktop.jmod")

        for (minecraftConfig in unimined.minecrafts.values) {
            val prodNamespace = minecraftConfig.mcPatcher.prodNamespace

            libraries.add(minecraftConfig.getMinecraft(prodNamespace, prodNamespace).toFile().absolutePath)

            val minecrafts = listOf(
                minecraftConfig.sourceSet.compileClasspath.files,
                minecraftConfig.sourceSet.runtimeClasspath.files
            )
                .flatten()
                .filter { it: File -> !minecraftConfig.isMinecraftJar(it.toPath()) }
                .toHashSet()

            libraries += minecraftConfig.mods.getClasspathAs(prodNamespace, prodNamespace, minecrafts)
                .filter { it.extension == "jar" && !it.name.startsWith("fes") }
                .map { it.absolutePath }
        }

        proguardConfig.addAll(listOf("-libraryjars", libraries.joinToString(separator = File.pathSeparator) { "\"$it\"" }))

        try {
            ProGuard(proguard.Configuration().also {
                ConfigurationParser(proguardConfig.toTypedArray(), null)
                    .parse(it)
            }).execute()
        } catch (ex: Exception) {
            throw IllegalStateException("ProGuard failed for $jar", ex)
        } finally {
            proguardInJar.delete()
        }
        //endregion

        val contents = linkedMapOf<String, ByteArray>()
        JarFile(jar).use {
            it.entries().asIterator().forEach { entry ->
                if (!entry.isDirectory) {
                    contents[entry.name] = it.getInputStream(entry).readAllBytes()
                }
            }
        }

        jar.delete()

        val json = JsonSlurper()

        JarOutputStream(jar.outputStream()).use { out ->
            out.setLevel(Deflater.BEST_COMPRESSION)
            contents.forEach { var (name, bytes) = it

                if (name.endsWith(".json") || name.endsWith(".mcmeta") || name == "mcmod.info") {
                    bytes = JsonOutput.toJson(json.parse(bytes)).toByteArray()
                }

                if(name.endsWith(".accesswidener") || name.endsWith(".aw")) {
                    val lines = String(bytes).split("\n").toMutableList()
                    var i = 0
                    while(i < lines.size) {
                        var line = lines[i]
                        if(line.contains("#")) {
                            line = line.substring(0, line.indexOf("#"))
                        }

                        if(line.isBlank()) {
                            lines.removeAt(i)
                            continue
                        }

                        lines[i] = line.replace("\\s+".toRegex(), " ")
                        i++
                    }

                    bytes = lines.joinToString("\n").toByteArray()
                }

                if(name.endsWith(".class")) {
                    bytes = processClassFile(bytes)
                }

                out.putNextEntry(JarEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
            out.finish()
        }

        val advzipInstalled = try {
            ProcessBuilder("advzip", "-V").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }

        if(advzipInstalled) {
            try {
                val process = ProcessBuilder("advzip", "-z", "-4", jar.absolutePath).start()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    error("Failed to compress $jar with advzip")
                }
            } catch (e: Exception) {
                error("Failed to compress $jar with advzip: ${e.message}")
            }
        }
    }
}

private fun processClassFile(bytes: ByteArray): ByteArray {
    val classNode = ClassNode()
    ClassReader(bytes).accept(classNode, 0)

    classNode.methods?.forEach { methodNode ->
        methodNode.localVariables?.clear()
        methodNode.tryCatchBlocks?.clear()
        methodNode.exceptions?.clear()
        methodNode.parameters?.clear()
    }

    val strippableAnnotations = setOf(
        "Lorg/spongepowered/asm/mixin/Dynamic;",
        "Lorg/spongepowered/asm/mixin/Final;",
        "Ljava/lang/SafeVarargs;",
    )
    val canStripAnnotation = { annotationNode: AnnotationNode ->
                annotationNode.desc.startsWith("Lorg/jetbrains/annotations/") ||
                strippableAnnotations.contains(annotationNode.desc)
    }

    classNode.invisibleAnnotations?.removeIf(canStripAnnotation)
    classNode.visibleAnnotations?.removeIf(canStripAnnotation)
    classNode.invisibleTypeAnnotations?.removeIf(canStripAnnotation)
    classNode.visibleTypeAnnotations?.removeIf(canStripAnnotation)
    classNode.fields.forEach { fieldNode ->
        fieldNode.invisibleAnnotations?.removeIf(canStripAnnotation)
        fieldNode.visibleAnnotations?.removeIf(canStripAnnotation)
        fieldNode.invisibleTypeAnnotations?.removeIf(canStripAnnotation)
        fieldNode.visibleTypeAnnotations?.removeIf(canStripAnnotation)
    }
    classNode.methods.forEach { methodNode ->
        methodNode.invisibleAnnotations?.removeIf(canStripAnnotation)
        methodNode.visibleAnnotations?.removeIf(canStripAnnotation)
        methodNode.invisibleTypeAnnotations?.removeIf(canStripAnnotation)
        methodNode.visibleTypeAnnotations?.removeIf(canStripAnnotation)
        methodNode.invisibleLocalVariableAnnotations?.removeIf(canStripAnnotation)
        methodNode.visibleLocalVariableAnnotations?.removeIf(canStripAnnotation)
        methodNode.invisibleParameterAnnotations?.forEach { parameterAnnotations ->
            parameterAnnotations?.removeIf(canStripAnnotation)
        }
        methodNode.visibleParameterAnnotations?.forEach { parameterAnnotations ->
            parameterAnnotations?.removeIf(canStripAnnotation)
        }
    }

    if (classNode.invisibleAnnotations?.any { it.desc == "Lorg/spongepowered/asm/mixin/Mixin;" } == true) {
        classNode.methods.removeAll { it.name == "<init>" && it.instructions.size() <= 3 } // ALOAD, super(), RETURN
        classNode.access = classNode.access or ACC_ABSTRACT
    }

    val writer = ClassWriter(0)
    classNode.accept(writer)
    return writer.toByteArray()
}