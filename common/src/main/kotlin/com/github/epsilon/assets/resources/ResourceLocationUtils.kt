package com.github.epsilon.assets.resources

import com.github.epsilon.Constants
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.Optional

object ResourceLocationUtils {

    fun getIdentifier(path: String): Identifier {
        return Identifier.fromNamespaceAndPath("epsilon", path)
    }

    fun loadResource(identifier: Identifier): ByteBuffer {
        val manager = Constants.mc.getResourceManager()
        val resource: Optional<Resource> = manager.getResource(identifier)

        if (resource.isEmpty) {
            throw RuntimeException("Couldn't find resource at $identifier")
        }

        try {
            resource.get().open().use { stream ->
                val bytes = stream.readAllBytes()
                val buffer = MemoryUtil.memAlloc(bytes.size)
                buffer.put(bytes)
                buffer.flip()
                return buffer
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to read file", e)
        }
    }

}
