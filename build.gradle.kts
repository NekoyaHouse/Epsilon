plugins {
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.neoforged.moddev) apply false
}

val catalogJavaVersion = libs.versions.project.java.get()
val catalogMinecraftVersion = libs.versions.project.minecraft.asProvider().get()
val catalogMinecraftVersionRange = libs.versions.project.minecraft.range.get()
val catalogFabricVersion = libs.versions.project.fabric.api.get()
val catalogFabricLoaderVersion = libs.versions.project.fabric.loader.get()
val catalogNeoFormVersion = libs.versions.project.neoform.get()
val catalogNeoForgeVersion = libs.versions.project.neoforge.asProvider().get()
val catalogNeoForgeLoaderVersionRange = libs.versions.project.neoforge.loader.range.get()

allprojects {
    extra["java_version"] = catalogJavaVersion
    extra["minecraft_version"] = catalogMinecraftVersion
    extra["minecraft_version_range"] = catalogMinecraftVersionRange
    extra["fabric_version"] = catalogFabricVersion
    extra["fabric_loader_version"] = catalogFabricLoaderVersion
    extra["neo_form_version"] = catalogNeoFormVersion
    extra["neoforge_version"] = catalogNeoForgeVersion
    extra["neoforge_loader_version_range"] = catalogNeoForgeLoaderVersionRange
}
