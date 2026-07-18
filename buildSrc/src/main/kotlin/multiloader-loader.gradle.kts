plugins {
    id("multiloader-common")
}

val commonJava by configurations.creating {
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
    commonResources(project(path = ":common", configuration = "commonResources"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(commonJava, commonBuildConfig)
    source(commonJava)
    source(commonBuildConfigJava)
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
    dependsOn(commonResources)
    from(commonResources)
}
