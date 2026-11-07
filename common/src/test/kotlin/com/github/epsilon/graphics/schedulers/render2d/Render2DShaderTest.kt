package com.github.epsilon.graphics.schedulers.render2d

import org.lwjgl.util.shaderc.Shaderc
import kotlin.test.*

class Render2DShaderTest {
    @Test fun retainedPropertiesCompileUnderActualGlsl410Rules() {
        val include = checkNotNull(javaClass.getResource("/assets/epsilon/shaders/include/ui_properties.glsl")).readText()
        val compiler = Shaderc.shaderc_compiler_initialize()
        val options = Shaderc.shaderc_compile_options_initialize()
        try {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_3)
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true)
            // 不提升 GLSL 版本，否则会遗漏 4.10 不允许 layout 位置表达式的问题。
            for (location in listOf(2, 5, 7)) {
                val source = """
                    #version 410 core
                    #define EPSILON_RETAINED
                    #define UI_PROPERTY_LOCATION $location
                    #define UI_PROPERTY_ROW1_LOCATION ${location + 1}
                    #define UI_PROPERTY_TINT_LOCATION ${location + 2}
                    $include
                    layout(location = 0) in vec2 Position;
                    void main() {
                        gl_Position = epsilonPosition(vec4(Position, 0.0, 1.0));
                        epsilonTint = UiTint;
                    }
                """.trimIndent()
                val result = Shaderc.shaderc_compile_into_spv(compiler, source, Shaderc.shaderc_vertex_shader, "retained-properties.vert", "main", options)
                try {
                    assertEquals(Shaderc.shaderc_compilation_status_success,
                        Shaderc.shaderc_result_get_compilation_status(result), Shaderc.shaderc_result_get_error_message(result))
                } finally { Shaderc.shaderc_result_release(result) }
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options)
            Shaderc.shaderc_compiler_release(compiler)
        }
    }
}
