plugins {
    id("multiloader-loader")
    alias(libs.plugins.fabric.loom)
}

val modId = project.property("mod_id").toString()

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    compileOnly(libs.sodium.fabric)
    compileOnly(libs.iris.fabric)
    implementation(include("org.bytedeco:javacpp:1.5.10")!!)
    implementation(include("org.bytedeco:javacv:1.5.10")!!)
    implementation(include("org.bytedeco:ffmpeg:6.1.1-1.5.10")!!)
    runtimeOnly(include("org.bytedeco:javacpp:1.5.10:windows-x86_64")!!)
    runtimeOnly(include("org.bytedeco:ffmpeg:6.1.1-1.5.10:windows-x86_64")!!)
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
listOf(
    "apiElements",
    "runtimeElements",
    "sourcesElements",
    "includeInternal",
    "modCompileClasspath"
).forEach { variant ->
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
