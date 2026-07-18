plugins {
    `java-library`
    `maven-publish`
}

val baseVersion = providers.gradleProperty("version")
val isReleaseBuild = gradle.startParameter.taskNames.any { taskName ->
    taskName == "buildRelease" || taskName.endsWith(":buildRelease")
}
val shortCommitId = if (isReleaseBuild) {
    providers.provider { "" }
} else {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map(String::trim)
}

version = if (isReleaseBuild) {
    baseVersion.get()
} else {
    baseVersion.zip(shortCommitId) { configuredVersion, shortCommit ->
        if (shortCommit.isBlank()) configuredVersion else "$configuredVersion-$shortCommit"
    }.get()
}

val modId = project.property("mod_id").toString()
val minecraftVersion = project.property("minecraft_version").toString()
val javaVersion = project.property("java_version").toString()
val modName = project.findProperty("mod_name")?.toString() ?: ""
val modAuthor = project.findProperty("mod_author")?.toString() ?: ""
val minecraftVersionRange = project.property("minecraft_version_range").toString()
val fabricVersion = project.property("fabric_version").toString()
val fabricLoaderVersion = project.property("fabric_loader_version").toString()
val license = project.property("license").toString()
val neoforgeVersion = project.property("neoforge_version").toString()
val neoforgeLoaderVersionRange = project.property("neoforge_loader_version_range").toString()
val credits = project.findProperty("credits")?.toString() ?: ""

base {
    archivesName.set("${modId}-${project.name}-${minecraftVersion}")
}

tasks.withType<Jar>().configureEach {
    archiveVersion.set(project.version.toString())
}

tasks.register("buildRelease") {
    group = "build"
    description = "Assembles and tests this project without appending Git information to built artifacts."
    dependsOn(tasks.named("build"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven {
                name = "Sponge"
                url = uri("https://repo.spongepowered.org/repository/maven-public")
            }
        }
        filter { includeGroupAndSubgroups("org.spongepowered") }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "CaffeineMC"
                url = uri("https://maven.caffeinemc.net/releases")
            }
        }
        filter { includeGroup("net.caffeinemc") }
    }
}

val licenseFileName = "LICENSE_${modName}"

tasks.named<Jar>("sourcesJar") {
    from(rootProject.file("LICENSE")) {
        // Avoid lambda-based rename to keep configuration cache serialization stable.
        rename("LICENSE", licenseFileName)
    }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) {
        // Avoid lambda-based rename to keep configuration cache serialization stable.
        rename("LICENSE", licenseFileName)
    }

    manifest {
        attributes(
            mapOf(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to archiveVersion,
                "Implementation-Title" to project.name,
                "Implementation-Version" to archiveVersion,
                "Implementation-Vendor" to modAuthor,
                "Built-On-Minecraft" to minecraftVersion
            )
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    val expandProps = mapOf(
        "version" to version,
        "group" to project.group,
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "fabric_version" to fabricVersion,
        "fabric_loader_version" to fabricLoaderVersion,
        "mod_name" to modName,
        "mod_author" to modAuthor,
        "mod_id" to modId,
        "license" to license,
        "description" to (project.findProperty("description")?.toString() ?: ""),
        "neoforge_version" to neoforgeVersion,
        "neoforge_loader_version_range" to neoforgeLoaderVersionRange,
        "credits" to credits,
        "java_version" to javaVersion
    )

    val jsonExpandProps = expandProps.mapValues { (_, value) ->
        if (value is String) value.replace("\n", "\\\\n") else value
    }

    filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
        expand(expandProps)
    }

    filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "*.mixins.json")) {
        expand(jsonExpandProps)
    }

    inputs.properties(expandProps)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri(file("repo").toURI())
        }
    }
}
