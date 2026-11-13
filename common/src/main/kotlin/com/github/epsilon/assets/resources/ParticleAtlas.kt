package com.github.epsilon.assets.resources

import net.minecraft.resources.Identifier

object ParticleAtlas {

    private const val WIDTH = 4096.0f
    private const val HEIGHT = 2048.0f

    val texture: Identifier = ResourceLocationUtils.getIdentifier("textures/particles/atlas.png")

    enum class Sprite(x: Int, y: Int, width: Int, height: Int) {
        SMOKE(2944, 32, 800, 800),
        CIRCLE(1856, 1088, 512, 512),
        CRACK(2400, 1088, 512, 512),
        CLOUD(2944, 864, 128, 128),
        DOLLAR(3104, 864, 128, 128),
        DROP(3264, 864, 128, 128),
        FIREFLY(3424, 864, 128, 128),
        HEART(3584, 864, 128, 128),
        SNOWFLAKE(2944, 1024, 128, 128),
        STAR(3104, 1024, 128, 128),
        TRIANGLE1(2944, 1184, 128, 128),
        TRIANGLE2(3104, 1184, 128, 128),
        TRIANGLE3(3264, 1184, 128, 128),
        TRIANGLE4(3424, 1184, 128, 128),
        HALO(3264, 1027, 128, 122),
        TRIANGLE5(3584, 1189, 128, 118),
        HIT_BUBBLE(1856, 32, 1024, 1024),
        TARGET(32, 32, 1766, 1766);

        val u0: Float = x / WIDTH
        val v0: Float = y / HEIGHT
        val u1: Float = (x + width) / WIDTH
        val v1: Float = (y + height) / HEIGHT
    }
}
