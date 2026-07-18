pluginManagement {
    val libsVersions = file("gradle/libs.versions.toml").readLines()
    fun catalogVersion(alias: String): String {
        val key = "$alias = "
        val versionsSection = libsVersions
            .dropWhile { it.trim() != "[versions]" }
            .drop(1)
            .takeWhile { !it.trim().startsWith("[") }
        return versionsSection.firstNotNullOfOrNull { line ->
            line.trim().takeIf { it.startsWith(key) }
                ?.substringAfter('"')
                ?.substringBefore('"')
        } ?: error("Missing version catalog entry: $alias")
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                }
            }
            filter {
                includeGroupAndSubgroups("net.fabricmc")
            }
        }
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version catalogVersion("foojay-resolver-convention")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "Epsilon"

include("common")
include("fabric")
include("neoforge")
