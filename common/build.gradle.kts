plugins {
    id("multiloader-common")
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.buildconfig)
    kotlin("kapt")
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

loom {
    // 预发布版通过 Loom 获取 Minecraft 与源码，共享代码仍只依赖 Minecraft API。
    accessWidenerPath.set(file("src/main/resources/${project.property("mod_id")}.accesswidener"))
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.msdfgen)
    testImplementation(libs.kotlin.test)
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras.common)
    annotationProcessor(libs.mixinextras.common)
    compileOnly(libs.asm)
    compileOnly("org.bytedeco:javacv-platform:1.5.11")
    // 共享 Kotlin 源码可用 kotlinx 库；运行时分发由 fabric/neoforge 的 jar-in-jar 声明负责。
    implementation(libs.kotlinx.coroutines.core)
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

tasks.test {
    workingDir = layout.buildDirectory.get().asFile
}

// MixinExtras 的注解处理器由 javac 执行；启用 kapt 后必须显式保留 javac 注解处理，否则会被关闭。
kapt {
    keepJavacAnnotationProcessors = true
}

configurations {
    create("commonJava") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonKotlin") {
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
    add("commonKotlin", file("src/main/kotlin"))
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
