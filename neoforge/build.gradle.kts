import com.github.epsilon.gradle.MarkGameLibraryJar
import net.neoforged.moddevgradle.tasks.JarJar
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("multiloader-loader")
    alias(libs.plugins.neoforged.moddev)
}

val neoforgeVersion = project.property("neoforge_version").toString()
val modId = project.property("mod_id").toString()

data class GameLibrarySpec(val group: String, val artifact: String, val version: String) {
    val notation = "$group:$artifact:$version"
    val fileName = "$artifact-$version.jar"
    val taskSuffix = artifact
        .split(Regex("[^A-Za-z0-9]+"))
        .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
}

// Minecraft already supplies these modules; nesting them would duplicate classes and inflate the mod jar.
val minecraftProvidedModules = setOf(
    "com.google.code.gson:gson",
    "com.google.errorprone:error_prone_annotations",
    "it.unimi.dsi:fastutil",
    "org.jetbrains:annotations",
    "org.joml:joml",
    "org.projectlombok:lombok",
)

val gameLibrarySpecs = listOf(
    GameLibrarySpec("com.viaversion", "viaversion-common", "5.12.0-SNAPSHOT"),
    GameLibrarySpec("com.viaversion", "viabackwards-common", "5.12.0-SNAPSHOT"),
    GameLibrarySpec("com.viaversion", "viaaprilfools-common", "4.2.3-SNAPSHOT"),
    GameLibrarySpec("net.raphimc", "ViaLegacy", "3.1.0-SNAPSHOT"),
    GameLibrarySpec("net.raphimc", "ViaBedrock", "0.0.29-SNAPSHOT"),
    GameLibrarySpec("dev.kastle.netty", "netty-transport-raknet", "1.7.0"),
)

val gameLibrarySources = gameLibrarySpecs.associateWith { spec ->
    configurations.create("gameLibrary${spec.taskSuffix}") {
        isCanBeResolved = true
        isCanBeConsumed = false
        isTransitive = false
    }
}

val markedGameLibraryTasks = gameLibrarySources.map { (spec, source) ->
    tasks.register<MarkGameLibraryJar>("mark${spec.taskSuffix}AsGameLibrary") {
        inputJar.set(layout.file(source.elements.map { it.single().asFile }))
        outputJar.set(layout.buildDirectory.file("game-libraries/${spec.fileName}"))
    }
}

val markedGameLibraryJars = files(markedGameLibraryTasks.map { it.flatMap(MarkGameLibraryJar::outputJar) })

val jarJarDependencySource by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get())
}

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
    implementation("com.viaversion:viaversion-common:5.12.0-SNAPSHOT")
    implementation("com.viaversion:viabackwards-common:5.12.0-SNAPSHOT")
    implementation("com.viaversion:viaaprilfools-common:4.2.3-SNAPSHOT")
    implementation("net.raphimc:ViaLegacy:3.1.0-SNAPSHOT")
    implementation("net.raphimc:ViaBedrock:0.0.29-SNAPSHOT") {
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
        exclude(group = "io.netty")
    }
    implementation("net.lenni0451:Reflect:1.6.4")
    implementation("dev.kastle.netty:netty-transport-raknet:1.7.0") {
        exclude(group = "io.netty")
    }
    implementation("dev.kastle.netty:netty-transport-nethernet:1.7.0") {
        exclude(group = "io.netty")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    listOf("windows-x86_64", "windows-aarch64", "linux-x86_64", "linux-aarch64", "macos-aarch64").forEach { platform ->
        implementation("dev.kastle.webrtc:webrtc-java:1.0.3:$platform")
    }
    sodiumNeoForgeOuterJar(libs.sodium.neoforge)
    compileOnly(extractedSodiumNeoForgeModJar)
    compileOnly(libs.iris.neoforge)

    gameLibrarySources.forEach { (spec, source) ->
        add(source.name, spec.notation)
    }
}

configurations.named("jarJar") {
    // NeoForge JarJar only records direct dependencies. Mirror the upstream
    // ViaFabricPlus build by turning every resolved implementation component
    // into a non-transitive direct dependency.
    defaultDependencies {
        jarJarDependencySource.incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .filterNot { id -> "${id.group}:${id.module}" in minecraftProvidedModules }
            .forEach { id ->
                add(project.dependencies.create("${id.group}:${id.module}:${id.version}").apply {
                    isTransitive = false
                })
            }
    }
    exclude(group = "com.mojang", module = "brigadier")
    exclude(group = "at.yawk.lz4", module = "lz4-java")
    exclude(group = "io.netty")
}

tasks.named<JarJar>("jarJar") {
    inputs.files(markedGameLibraryJars)
        .withPathSensitivity(PathSensitivity.NONE)
    dependsOn(markedGameLibraryTasks)

    doLast {
        val nestedDirectory = outputDirectory.dir("META-INF/jarjar").get().asFile
        markedGameLibraryJars.files.forEach { markedJar ->
            val embeddedJar = nestedDirectory.resolve(markedJar.name)
            check(embeddedJar.isFile) {
                "JarJar did not embed the expected game library ${markedJar.name}"
            }
            markedJar.copyTo(embeddedJar, overwrite = true)
        }
    }
}

neoForge {
    version = neoforgeVersion
    validateAccessTransformers = true
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
            programArguments.addAll("--mod", modId, "--all", "--output", file("src/generated/resources/").absolutePath, "--existing", file("src/main/resources/").absolutePath)
        }
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

val originalRuntimeClasspath = sourceSets.main.get().runtimeClasspath
sourceSets.main.get().runtimeClasspath = originalRuntimeClasspath.filter {
    it.name !in gameLibrarySpecs.map(GameLibrarySpec::fileName)
} + markedGameLibraryJars

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "neoforge")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName, getTaskName(null, "jarJar")).forEach { variant ->
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
