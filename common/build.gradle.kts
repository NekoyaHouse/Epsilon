plugins {
    id("multiloader-common")
    alias(libs.plugins.neoforged.moddev)
    alias(libs.plugins.buildconfig)
}

buildConfig {
    packageName("com.github.epsilon")
    useJavaOutput()
    buildConfigField("String", "MOD_ID", "\"${project.property("mod_id")}\"")
    val effectiveVersion = project.version.toString()
    buildConfigField("String", "VERSION", "new String(\"${effectiveVersion.replace("\\", "\\\\").replace("\"", "\\\"")}\")")
}

tasks.named("generateBuildConfigClasses") {
    inputs.file(rootProject.layout.projectDirectory.file("gradle.properties"))
        .withPropertyName("gradleProperties")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

neoForge {
    neoFormVersion = project.property("neo_form_version").toString()
    val at = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras.common)
    annotationProcessor(libs.mixinextras.common)
    compileOnly(libs.asm)
    compileOnly("org.ow2.asm:asm-tree:${libs.versions.asm.get()}")

    // ViaFabricPlus keeps these libraries in the loader-specific jar while common
    // compiles against their shared APIs.
    compileOnly("com.viaversion:viaversion-common:5.12.0-SNAPSHOT")
    compileOnly("com.viaversion:viabackwards-common:5.12.0-SNAPSHOT")
    compileOnly("com.viaversion:viaaprilfools-common:4.2.2")
    compileOnly("net.raphimc:ViaLegacy:3.0.16")
    compileOnly("net.raphimc:ViaBedrock:0.0.29-SNAPSHOT") {
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
        exclude(group = "io.netty")
    }
    compileOnly("net.lenni0451:Reflect:1.6.4")
    compileOnly("dev.kastle.netty:netty-transport-raknet:1.7.0") {
        exclude(group = "io.netty")
    }
    compileOnly("dev.kastle.netty:netty-transport-nethernet:1.7.0") {
        exclude(group = "io.netty")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    listOf("windows-x86_64", "windows-aarch64", "linux-x86_64", "linux-aarch64", "macos-aarch64").forEach { platform ->
        compileOnly("dev.kastle.webrtc:webrtc-java:1.0.3:$platform")
    }

}

configurations {
    create("commonJava") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonResources") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

artifacts {
    add("commonJava", file("src/main/java"))
    add("commonResources", file("src/main/resources"))
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)

listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "common")
        }
    }
}

sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "common")
            }
        }
    }
}
