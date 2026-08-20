package com.github.epsilon.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

@CacheableTask
abstract class MarkGameLibraryJar : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun markGameLibrary() {
        val inputPath = inputJar.get().asFile.toPath()
        val outputPath = outputJar.get().asFile.toPath()
        Files.createDirectories(outputPath.parent)

        val temporaryPath = Files.createTempFile(outputPath.parent, outputPath.fileName.toString(), ".tmp")
        try {
            JarFile(inputPath.toFile()).use { input ->
                val manifest = input.manifest ?: Manifest().apply {
                    mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                }
                manifest.mainAttributes.putValue("FMLModType", "GAMELIBRARY")

                JarOutputStream(Files.newOutputStream(temporaryPath)).use { output ->
                    if (input.getJarEntry(JarFile.MANIFEST_NAME) == null) {
                        writeManifest(output, null, manifest)
                    }

                    val entries = input.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.equals(JarFile.MANIFEST_NAME, ignoreCase = true)) {
                            writeManifest(output, entry, manifest)
                            continue
                        }
                        if (isSignatureFile(entry.name)) {
                            throw GradleException("Cannot mark signed JAR as a NeoForge game library: $inputPath")
                        }

                        output.putNextEntry(JarEntry(entry))
                        if (!entry.isDirectory) {
                            input.getInputStream(entry).use { it.copyTo(output) }
                        }
                        output.closeEntry()
                    }
                }
            }

            try {
                Files.move(
                    temporaryPath,
                    outputPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryPath, outputPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }

    private fun writeManifest(output: JarOutputStream, originalEntry: JarEntry?, manifest: Manifest) {
        val entry = JarEntry(JarFile.MANIFEST_NAME)
        if (originalEntry != null && originalEntry.time >= 0) {
            entry.time = originalEntry.time
        }
        output.putNextEntry(entry)
        manifest.write(output)
        output.closeEntry()
    }

    private fun isSignatureFile(name: String): Boolean {
        val normalized = name.uppercase(Locale.ROOT)
        return normalized.startsWith("META-INF/") && SIGNATURE_EXTENSIONS.any(normalized::endsWith)
    }

    private companion object {
        val SIGNATURE_EXTENSIONS = listOf(".SF", ".RSA", ".DSA", ".EC")
    }

}
