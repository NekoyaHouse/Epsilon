package com.github.epsilon.graphics.text.ttf

import org.lwjgl.util.shaderc.Shaderc.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MsdfShaderTest {
    @Test
    fun compileImmediateRetainedAndTextureTableVariants() {
        val root = Path.of("../src/main/resources/assets/epsilon/shaders")
        val include = Files.readString(root.resolve("include/ui_font_texture.glsl"))
        val compiler = shaderc_compiler_initialize()
        check(compiler != 0L)
        try {
            val shader = "ttf_font.fsh"
            val source = Files.readString(root.resolve(shader)).replace("#include <epsilon:ui_font_texture.glsl>", include)
            for (retained in listOf(false, true)) {
                for (textureTable in listOf(false, true)) {
                    val options = shaderc_compile_options_initialize()
                    check(options != 0L)
                    try {
                        // 与当前 RenderPearl 编译器相同，面向 Vulkan 并自动分配 sampler binding。
                        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_3)
                        shaderc_compile_options_set_auto_bind_uniforms(options, true)
                        if (retained) shaderc_compile_options_add_macro_definition(options, "EPSILON_RETAINED", "")
                        if (textureTable) shaderc_compile_options_add_macro_definition(options, "EPSILON_TEXTURE_TABLE", "")
                        val result = shaderc_compile_into_spv(compiler, source, shaderc_fragment_shader, shader, "main", options)
                        check(result != 0L)
                        try {
                            assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                                "$shader retained=$retained table=$textureTable: ${shaderc_result_get_error_message(result)}")
                        } finally {
                            shaderc_result_release(result)
                        }
                    } finally {
                        shaderc_compile_options_release(options)
                    }
                }
            }
        } finally {
            shaderc_compiler_release(compiler)
        }
    }
}
