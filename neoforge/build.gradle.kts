import net.neoforged.moddevgradle.tasks.JarJar
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

plugins {
    id("multiloader-loader")
    alias(libs.plugins.neoforged.moddev)
}

val neoforgeVersion = project.property("neoforge_version").toString()
val modId = project.property("mod_id").toString()

val sodiumNeoForgeOuterJar by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val extractSodiumNeoForgeModJar by tasks.registering(Copy::class) {
    from({ zipTree(sodiumNeoForgeOuterJar.singleFile) }) {
        include("META-INF/jarjar/*-mod.jar")
        eachFile {
            path = name
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("extracted-sodium-neoforge"))
}

val extractedSodiumNeoForgeModJar = files(
    layout.buildDirectory.dir("extracted-sodium-neoforge")
        .map { it.asFileTree.matching { include("*.jar") } }
).builtBy(extractSodiumNeoForgeModJar)

dependencies {
    compileOnly(libs.sodium.neoforge)
    sodiumNeoForgeOuterJar(libs.sodium.neoforge)
    compileOnly(extractedSodiumNeoForgeModJar)
    compileOnly(libs.iris.neoforge)
    implementation(jarJar("org.bytedeco:javacpp:1.5.10")!!)
    implementation(jarJar("org.bytedeco:javacv:1.5.10")!!)
    implementation(jarJar("org.bytedeco:ffmpeg:6.1.1-1.5.10")!!)
    // JavaCPP 的 JNI 桥按平台随 jar 分发：视频能力需要 jnijavacpp 才能加载下载得到的 FFmpeg 原生库。
    runtimeOnly(jarJar("org.bytedeco:javacpp:1.5.10:windows-x86_64")!!)
    runtimeOnly(jarJar("org.bytedeco:javacpp:1.5.10:macosx-arm64")!!)
    // FFmpeg 原生库不再随 jar 分发，改为首次使用时下载到 ~/.epsilon/assets/ffmpeg/natives。
    // 开发环境仍保留在运行时类路径，便于本地调试。
    runtimeOnly("org.bytedeco:ffmpeg:6.1.1-1.5.10:windows-x86_64")
    runtimeOnly("org.bytedeco:ffmpeg:6.1.1-1.5.10:macosx-arm64")
}

// NeoForge 26.2 resolves Jar-in-Jar dependencies by group and artifact only;
// classifiers are ignored. Give every platform jar a distinct identifier so the Java API jar and each
// platform's JNI bridge stay loadable side by side at runtime.
tasks.named<JarJar>("jarJar") {
    doLast {
        val metadataFile = outputDirectory.dir("META-INF/jarjar/metadata.json").get().asFile
        val metadataLegacy = metadataFile.readText()
            .replace(
                "\"artifact\": \"javacpp\",\n      }\n      ,\n      \"version\": {\n        \"range\": \"[1.5.10,)\",\n        \"artifactVersion\": \"1.5.10\"\n      },\n      \"path\": \"META-INF/jarjar/javacpp-1.5.10-windows-x86_64.jar\"",
                "\"artifact\": \"javacpp-windows-x86_64\",\n      },\n      \"version\": {\n        \"range\": \"[1.5.10,)\",\n        \"artifactVersion\": \"1.5.10\"\n      },\n      \"path\": \"META-INF/jarjar/javacpp-1.5.10-windows-x86_64.jar\""
            )
        fun renameArtifact(metadata: String, path: String, artifact: String): String {
            val pathIndex = metadata.indexOf("\"path\": \"$path\"")
            check(pathIndex >= 0) { "JarJar metadata does not contain $path" }
            val identifierIndex = metadata.lastIndexOf("\"identifier\": {", pathIndex)
            val marker = "\"artifact\": \""
            val artifactIndex = metadata.indexOf(marker, identifierIndex)
            check(identifierIndex >= 0 && artifactIndex >= 0 && artifactIndex < pathIndex) {
                "JarJar metadata entry for $path is malformed"
            }
            val valueStart = artifactIndex + marker.length
            val valueEnd = metadata.indexOf('"', valueStart)
            return metadata.substring(0, valueStart) + artifact + metadata.substring(valueEnd)
        }

        val metadata = metadataLegacy
            .let { renameArtifact(it, "META-INF/jarjar/javacpp-1.5.10-windows-x86_64.jar", "javacpp-windows-x86_64") }
            .let { renameArtifact(it, "META-INF/jarjar/javacpp-1.5.10-macosx-arm64.jar", "javacpp-macosx-arm64") }
        metadataFile.writeText(metadata)
    }
}

fun fixJarJarMetadata(metadata: String): String {
    fun rename(input: String, path: String, artifact: String): String {
        val pathIndex = input.indexOf("\"path\": \"$path\"")
        check(pathIndex >= 0) { "JarJar metadata does not contain $path" }
        val identifierIndex = input.lastIndexOf("\"identifier\": {", pathIndex)
        val marker = "\"artifact\": \""
        val artifactIndex = input.indexOf(marker, identifierIndex)
        val valueStart = artifactIndex + marker.length
        val valueEnd = input.indexOf('"', valueStart)
        return input.substring(0, valueStart) + artifact + input.substring(valueEnd)
    }
    return metadata
        .let { rename(it, "META-INF/jarjar/javacpp-1.5.10-windows-x86_64.jar", "javacpp-windows-x86_64") }
        .let { rename(it, "META-INF/jarjar/javacpp-1.5.10-macosx-arm64.jar", "javacpp-macosx-arm64") }
}

tasks.named<Jar>("jar") {
    dependsOn("jarJar")
    doLast {
        val archive = archiveFile.get().asFile.toPath()
        val temporary = Files.createTempFile(archive.parent, archive.fileName.toString(), ".tmp")
        try {
            JarFile(archive.toFile()).use { input ->
                JarOutputStream(Files.newOutputStream(temporary)).use { output ->
                    input.entries().asSequence().forEach { entry ->
                        val contents = input.getInputStream(entry).readBytes()
                        output.putNextEntry(JarEntry(entry.name))
                        output.write(if (entry.name == "META-INF/jarjar/metadata.json") fixJarJarMetadata(String(contents)) .toByteArray() else contents)
                        output.closeEntry()
                    }
                }
            }
            Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

neoForge {
    version = neoforgeVersion
    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    runs {
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            ideName = "NeoForge ${name.replaceFirstChar { it.uppercase() }} (${project.path})"
            logLevel = org.slf4j.event.Level.DEBUG
            systemProperty("terminal.jline", "true")
        }
        register("client") {
            client()
            gameDirectory = file("runs/client").also { it.mkdirs() }
        }
        register("data") {
            clientData()
            gameDirectory = file("runs/data").also { it.mkdirs() }
            programArguments.addAll(
                "--mod",
                modId,
                "--all",
                "--output",
                file("src/generated/resources/").absolutePath,
                "--existing",
                file("src/main/resources/").absolutePath
            )
        }
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "neoforge")
        }
    }
}
sourceSets.configureEach {
    listOf(
        compileClasspathConfigurationName,
        runtimeClasspathConfigurationName,
        getTaskName(null, "jarJar")
    ).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "neoforge")
            }
        }
    }
}

/*
tasks.register<Copy>("extractRuntimeClasspath") {
    from(configurations.runtimeClasspath)
    into("$projectDir/build/runtimeClasspath")
    doFirst {
        file("$projectDir/build/runtimeClasspath").mkdirs()
    }
}
*/
