import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("multiloader-loader")
    alias(libs.plugins.fabric.loom)
}

val modId = project.property("mod_id").toString()

// Minecraft already supplies these modules; nesting them would duplicate classes and inflate the mod jar.
val minecraftProvidedModules = setOf(
    "com.google.code.gson:gson",
    "com.google.errorprone:error_prone_annotations",
    "it.unimi.dsi:fastutil",
    "org.jetbrains:annotations",
    "org.joml:joml",
    "org.projectlombok:lombok",
)

val includeDependencySource by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get())
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
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
    compileOnly(libs.sodium.fabric)
    compileOnly(libs.iris.fabric)
}

configurations.named("include") {
    // Fabric Loom only nests direct include dependencies. Resolve the Via
    // implementation graph once and add its modules as non-transitive direct
    // dependencies, matching ViaFabricPlus's upstream JIJ setup.
    defaultDependencies {
        includeDependencySource.incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .filterNot { id ->
                id.group == "net.fabricmc"
                    || id.group == "net.fabricmc.fabric-api"
                    || id.group == "com.mojang" && id.module == "brigadier"
                    || id.group == "at.yawk.lz4" && id.module == "lz4-java"
                    || id.group == "io.netty"
                    || "${id.group}:${id.module}" in minecraftProvidedModules
            }
            .forEach { id ->
                add(project.dependencies.create("${id.group}:${id.module}:${id.version}").apply {
                    isTransitive = false
                })
            }
    }
    isTransitive = true
}

loom {
    val aw = project(":common").file("src/main/resources/${modId}.accesswidener")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("runs/client")
        }
    }
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements", "includeInternal", "modCompileClasspath").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "fabric")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "fabric")
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
