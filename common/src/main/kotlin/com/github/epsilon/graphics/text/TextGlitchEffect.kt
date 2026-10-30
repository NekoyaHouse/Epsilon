package com.github.epsilon.graphics.text

import java.awt.Color

/**
 * 文本故障（glitch）效果参数。
 *
 * 原 Java 版本是紧凑构造器里做钳制与 null 回退的 record。`@JvmRecord` 无法改写构造参数值，
 * 因此按 GUIDE 2.2 改为普通 class：构造参数数量与顺序保持不变（包外 `new TextGlitchEffect(...)`），
 * 并用 `@get:JvmName` 生成 record 风格的字段访问器（包内大量 `effect.xxx()` 调用），
 * 同时手工实现 record 语义的 equals/hashCode/toString。
 */
class TextGlitchEffect(
    chromaticX: Float,
    chromaticY: Float,
    glowRadius: Float,
    glowIntensity: Float,
    sliceHeight: Float,
    sliceAmount: Float,
    glitchStrength: Float,
    scanlineStrength: Float,
    neonColor: Color?,
    noiseStrength: Float
) {

    @get:JvmName("chromaticX")
    val chromaticX: Float = clampFinite(chromaticX, 0.0f, 12.0f)

    @get:JvmName("chromaticY")
    val chromaticY: Float = clampFinite(chromaticY, 0.0f, 12.0f)

    @get:JvmName("glowRadius")
    val glowRadius: Float = clampFinite(glowRadius, 0.0f, 20.0f)

    @get:JvmName("glowIntensity")
    val glowIntensity: Float = clampFinite(glowIntensity, 0.0f, 5.0f)

    @get:JvmName("sliceHeight")
    val sliceHeight: Float = clampFinite(sliceHeight, 1.0f, 64.0f)

    @get:JvmName("sliceAmount")
    val sliceAmount: Float = clampFinite(sliceAmount, 0.0f, 20.0f)

    @get:JvmName("glitchStrength")
    val glitchStrength: Float = clampFinite(glitchStrength, 0.0f, 1.0f)

    @get:JvmName("scanlineStrength")
    val scanlineStrength: Float = clampFinite(scanlineStrength, 0.0f, 1.0f)

    @get:JvmName("neonColor")
    val neonColor: Color = neonColor ?: Color.CYAN

    @get:JvmName("noiseStrength")
    val noiseStrength: Float = clampFinite(noiseStrength, 0.0f, 1.0f)

    fun requiredPadding(): Float {
        val chromaticPadding = Math.max(chromaticX, chromaticY) * 1.5f
        val glitchPadding = sliceAmount * glitchStrength
        return Math.max(glowRadius, Math.max(chromaticPadding, glitchPadding)) + 2.0f
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextGlitchEffect) return false
        return chromaticX.compareTo(other.chromaticX) == 0 &&
                chromaticY.compareTo(other.chromaticY) == 0 &&
                glowRadius.compareTo(other.glowRadius) == 0 &&
                glowIntensity.compareTo(other.glowIntensity) == 0 &&
                sliceHeight.compareTo(other.sliceHeight) == 0 &&
                sliceAmount.compareTo(other.sliceAmount) == 0 &&
                glitchStrength.compareTo(other.glitchStrength) == 0 &&
                scanlineStrength.compareTo(other.scanlineStrength) == 0 &&
                neonColor == other.neonColor &&
                noiseStrength.compareTo(other.noiseStrength) == 0
    }

    override fun hashCode(): Int {
        var result = chromaticX.hashCode()
        result = 31 * result + chromaticY.hashCode()
        result = 31 * result + glowRadius.hashCode()
        result = 31 * result + glowIntensity.hashCode()
        result = 31 * result + sliceHeight.hashCode()
        result = 31 * result + sliceAmount.hashCode()
        result = 31 * result + glitchStrength.hashCode()
        result = 31 * result + scanlineStrength.hashCode()
        result = 31 * result + neonColor.hashCode()
        result = 31 * result + noiseStrength.hashCode()
        return result
    }

    override fun toString(): String {
        return "TextGlitchEffect[chromaticX=$chromaticX, chromaticY=$chromaticY, glowRadius=$glowRadius, " +
                "glowIntensity=$glowIntensity, sliceHeight=$sliceHeight, sliceAmount=$sliceAmount, " +
                "glitchStrength=$glitchStrength, scanlineStrength=$scanlineStrength, neonColor=$neonColor, " +
                "noiseStrength=$noiseStrength]"
    }

    private companion object {

        fun clampFinite(value: Float, min: Float, max: Float): Float {
            return if (value.isFinite()) Math.clamp(value, min, max) else min
        }

    }

}
