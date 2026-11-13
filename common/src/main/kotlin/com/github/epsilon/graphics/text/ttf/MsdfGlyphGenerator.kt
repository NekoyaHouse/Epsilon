package com.github.epsilon.graphics.text.ttf

import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.msdfgen.MSDFGen.*
import org.lwjgl.util.msdfgen.MSDFGenBitmap
import org.lwjgl.util.msdfgen.MSDFGenTransform
import org.lwjgl.util.msdfgen.MSDFGenVector2
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** STB 负责字体解析，msdfgen 仅接收轮廓，不使用 FreeType 的字体加载接口。 */
internal object MsdfGlyphGenerator {
    // 与 ui_font_texture.glsl 的 EPSILON_MSDF_PIXEL_RANGE 保持一致；轮廓两侧各覆盖四个 atlas 像素。
    private const val PIXEL_RANGE = 8.0

    fun generate(
        fontInfo: STBTTFontinfo, glyphIndex: Int, scale: Float,
        width: Int, height: Int, xOffset: Int, yOffset: Int
    ): ByteBuffer? {
        if (width <= 0 || height <= 0) return null
        val vertices = STBTruetype.stbtt_GetGlyphShape(fontInfo, glyphIndex) ?: return null
        try {
            return render(width, height, scale.toDouble(), xOffset, yOffset) { builder ->
                var x = 0.0
                var y = 0.0
                var startX = 0.0
                var startY = 0.0
                for (index in 0 until vertices.remaining()) {
                    val vertex = vertices[index]
                    val nextX = vertex.x().toDouble()
                    val nextY = vertex.y().toDouble()
                    when (vertex.type()) {
                        STBTruetype.STBTT_vmove -> {
                            if (builder.hasContour) builder.line(x, y, startX, startY)
                            builder.beginContour()
                            startX = nextX
                            startY = nextY
                        }

                        STBTruetype.STBTT_vline -> builder.line(x, y, nextX, nextY)
                        STBTruetype.STBTT_vcurve -> builder.edge(
                            MSDF_SEGMENT_TYPE_QUADRATIC,
                            x, y, vertex.cx().toDouble(), vertex.cy().toDouble(), nextX, nextY
                        )

                        STBTruetype.STBTT_vcubic -> builder.edge(
                            MSDF_SEGMENT_TYPE_CUBIC,
                            x, y, vertex.cx().toDouble(), vertex.cy().toDouble(),
                            vertex.cx1().toDouble(), vertex.cy1().toDouble(), nextX, nextY
                        )

                        else -> error("Unsupported STB glyph vertex type: ${vertex.type()}")
                    }
                    x = nextX
                    y = nextY
                }
                builder.line(x, y, startX, startY)
            }
        } finally {
            STBTruetype.stbtt_FreeShape(fontInfo, vertices)
        }
    }

    fun generateFallback(boxWidth: Int, boxHeight: Int, stroke: Int, padding: Int): ByteBuffer {
        return render(boxWidth + padding * 2, boxHeight + padding * 2, 1.0, -padding, -boxHeight - padding) { builder ->
            builder.rectangle(0.0, 0.0, boxWidth.toDouble(), boxHeight.toDouble())
            builder.rectangle(
                stroke.toDouble(),
                stroke.toDouble(),
                (boxWidth - stroke).toDouble(),
                (boxHeight - stroke).toDouble()
            )
        }
    }

    private fun render(
        width: Int, height: Int, scale: Double, xOffset: Int, yOffset: Int,
        build: (ShapeBuilder) -> Unit
    ): ByteBuffer = MemoryStack.stackPush().use { stack ->
        val handle = stack.mallocPointer(1)
        checked(msdf_shape_alloc(handle), "allocate shape")
        val shape = handle[0]
        try {
            build(ShapeBuilder(shape, stack))
            checked(msdf_shape_normalize(shape), "normalize shape")
            val valid = stack.mallocInt(1)
            checked(msdf_shape_validate(shape, valid), "validate shape")
            check(valid[0] == MSDF_TRUE) { "Invalid STB glyph outline" }
            // STB 的复合字形可能含相反绕向的孔洞，统一朝向后由 msdfgen 处理重叠轮廓。
            checked(msdf_shape_orient_contours(shape), "orient contours")
            checked(msdf_shape_edge_colors_simple(shape, 3.0), "color edges")

            val bitmap = MSDFGenBitmap.calloc(stack)
            checked(msdf_bitmap_alloc(MSDF_BITMAP_TYPE_MSDF, width, height, bitmap), "allocate bitmap")
            try {
                val transform = MSDFGenTransform.calloc(stack)
                transform.scale().set(scale, scale)
                transform.translation().set(-xOffset / scale, (yOffset + height) / scale)
                transform.distance_mapping().set(-PIXEL_RANGE * 0.5 / scale, PIXEL_RANGE * 0.5 / scale)
                checked(msdf_generate_msdf(bitmap, shape, transform), "generate MSDF")
                checked(msdf_bitmap_get_pixels(bitmap, handle), "read bitmap")
                val source = MemoryUtil.memFloatBuffer(handle[0], width * height * 3)
                val output = MemoryUtil.memAlloc(width * height * 4)
                try {
                    for (row in 0 until height) {
                        for (column in 0 until width) {
                            // msdfgen 使用向上的字体坐标；atlas 与 STB 覆盖率位图均从顶行开始。
                            val sourceIndex = ((height - row - 1) * width + column) * 3
                            val targetIndex = (row * width + column) * 4
                            for (channel in 0..2) {
                                output.put(
                                    targetIndex + channel,
                                    (source[sourceIndex + channel].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                                )
                            }
                            output.put(targetIndex + 3, 0xFF.toByte())
                        }
                    }
                    output
                } catch (failure: Throwable) {
                    MemoryUtil.memFree(output)
                    throw failure
                }
            } finally {
                msdf_bitmap_free(bitmap)
            }
        } finally {
            // Shape 持有所有 contour 与已移交的 segment，必须整体释放，不能重复释放边。
            msdf_shape_free(shape)
        }
    }

    private class ShapeBuilder(private val shape: Long, stack: MemoryStack) {
        private val handle = stack.mallocPointer(1)
        private val point = MSDFGenVector2.malloc(stack)
        private var contour = 0L
        val hasContour: Boolean get() = contour != 0L

        fun beginContour() {
            checked(msdf_shape_add_contour(shape, handle), "add contour")
            contour = handle[0]
        }

        fun line(x0: Double, y0: Double, x1: Double, y1: Double) {
            if (x0 != x1 || y0 != y1) edge(MSDF_SEGMENT_TYPE_LINEAR, x0, y0, x1, y1)
        }

        fun rectangle(left: Double, bottom: Double, right: Double, top: Double) {
            beginContour()
            line(left, bottom, right, bottom)
            line(right, bottom, right, top)
            line(right, top, left, top)
            line(left, top, left, bottom)
        }

        fun edge(type: Int, vararg coordinates: Double) {
            checked(msdf_segment_alloc(type, handle), "allocate segment")
            val segment = handle[0]
            var transferred = false
            try {
                for (index in 0 until coordinates.size / 2) {
                    point.set(coordinates[index * 2], coordinates[index * 2 + 1])
                    checked(msdf_segment_set_point(segment, index.toLong(), point), "set segment point")
                }
                checked(msdf_contour_add_edge(contour, segment), "add edge")
                transferred = true
            } finally {
                if (!transferred) msdf_segment_free(segment)
            }
        }
    }

    private fun checked(result: Int, operation: String) {
        check(result == MSDF_SUCCESS) { "msdfgen failed to $operation (error $result)" }
    }
}
