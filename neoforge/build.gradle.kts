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
