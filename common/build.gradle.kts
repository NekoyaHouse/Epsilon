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
    compileOnly(libs.jsr305)
    testImplementation(libs.gson)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
