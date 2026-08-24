package me.sofurry.via.util;

import net.lenni0451.reflect.ClassLoaders;
import net.lenni0451.reflect.stream.RStream;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Allows the user to override the Via* jar files with custom ones using the "jars" folder in the run directory
 */
public class ClassLoaderPriorityUtil {

    private static final Set<String> overridingJars = new HashSet<>();

    /**
     * Loads all overriding jars
     */
    public static void loadOverridingJars(final Path path, final Logger logger) {
        try {
            overridingJars.clear();

            final Path jars = path.resolve("jars");
            if (!Files.exists(jars)) {
                Files.createDirectory(jars);
                return;
            }

            final File[] files = jars.toFile().listFiles();
            if (files == null) {
                return;
            }

            final File[] jarFiles = Arrays.stream(files)
                    .filter(file -> file.getName().endsWith(".jar"))
                    .toArray(File[]::new);
            if (jarFiles.length == 0) {
                return;
            }

            final ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
            try {
                final ClassLoader actualLoader = RStream.of(oldLoader).fields().by("urlLoader").get();
                Thread.currentThread().setContextClassLoader(actualLoader);

                logger.warn("================================");
                logger.warn("OVERRIDING JARS LOADING! THIS CAN CAUSE UNEXPECTED BEHAVIOR AND ISSUES!");
                for (File file : jarFiles) {
                    ClassLoaders.loadToFront(file.toURI().toURL());
                    overridingJars.add(file.getName());
                    logger.warn(" -> {}", file.getName());
                }
                logger.warn("================================");
            } finally {
                Thread.currentThread().setContextClassLoader(oldLoader);
            }
        } catch (Throwable e) {
            logger.error("Failed to load overriding jars", e);
        }
    }

    public static Set<String> getOverridingJars() {
        return overridingJars;
    }

}
