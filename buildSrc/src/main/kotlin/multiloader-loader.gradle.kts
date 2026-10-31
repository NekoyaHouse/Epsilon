import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("multiloader-common")
}

val commonJava by configurations.creating {
    isCanBeResolved = true
}
val commonKotlin by configurations.creating {
    isCanBeResolved = true
}
val commonResources by configurations.creating {
    isCanBeResolved = true
}

val commonProject = project(":common")
val commonBuildConfig = commonProject.tasks.named("generateBuildConfigClasses")
val commonBuildConfigJava = commonProject.layout.buildDirectory.dir("generated/sources/buildConfig/main")

dependencies {
    val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
    compileOnly(project(":common")) {
        attributes {
            attribute(loaderAttribute, "common")
        }
    }
    commonJava(project(path = ":common", configuration = "commonJava"))
    commonKotlin(project(path = ":common", configuration = "commonKotlin"))
    commonResources(project(path = ":common", configuration = "commonResources"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(commonJava, commonBuildConfig)
    source(commonJava)
    source(commonBuildConfigJava)
}

// 共享 Kotlin 源码与共享 Java 源码一样在 loader 工程内重复编译，保持“同一份源码、按平台产物”的既有结构。
tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(commonKotlin)
    source(commonKotlin)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(commonResources)
    from(commonResources)
}

tasks.named<Javadoc>("javadoc") {
    dependsOn(commonJava, commonBuildConfig)
    source(commonJava)
    source(commonBuildConfigJava)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(commonJava)
    from(commonJava)
    dependsOn(commonBuildConfig)
    from(commonBuildConfigJava)
    dependsOn(commonKotlin)
    from(commonKotlin)
    dependsOn(commonResources)
    from(commonResources)
}
