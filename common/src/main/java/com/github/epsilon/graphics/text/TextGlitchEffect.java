package com.github.epsilon.graphics.text;

import java.awt.*;

public record TextGlitchEffect(
        float chromaticX,
        float chromaticY,
        float glowRadius,
        float glowIntensity,
        float sliceHeight,
        float sliceAmount,
        float glitchStrength,
        float scanlineStrength,
        Color neonColor,
        float noiseStrength
) {

    public TextGlitchEffect {
        chromaticX = clampFinite(chromaticX, 0.0f, 12.0f);
        chromaticY = clampFinite(chromaticY, 0.0f, 12.0f);
        glowRadius = clampFinite(glowRadius, 0.0f, 20.0f);
        glowIntensity = clampFinite(glowIntensity, 0.0f, 5.0f);
        sliceHeight = clampFinite(sliceHeight, 1.0f, 64.0f);
        sliceAmount = clampFinite(sliceAmount, 0.0f, 20.0f);
        glitchStrength = clampFinite(glitchStrength, 0.0f, 1.0f);
        scanlineStrength = clampFinite(scanlineStrength, 0.0f, 1.0f);
        neonColor = neonColor == null ? Color.CYAN : neonColor;
        noiseStrength = clampFinite(noiseStrength, 0.0f, 1.0f);
    }

    public float requiredPadding() {
        float chromaticPadding = Math.max(chromaticX, chromaticY) * 1.5f;
        float glitchPadding = sliceAmount * glitchStrength;
        return Math.max(glowRadius, Math.max(chromaticPadding, glitchPadding)) + 2.0f;
    }

    private static float clampFinite(float value, float min, float max) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : min;
    }

}
