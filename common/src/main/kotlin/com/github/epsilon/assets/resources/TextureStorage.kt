package com.github.epsilon.assets.resources

import net.minecraft.resources.Identifier

object TextureStorage {

    val CIRCLE: Identifier = texture("circle")
    val CRACK: Identifier = texture("crack")
    val CLOUD: Identifier = texture("cloud")
    val DOLLAR: Identifier = texture("dollar")
    val DROP: Identifier = texture("drop")
    val FIREFLY: Identifier = texture("firefly")
    val HEART: Identifier = texture("heart")
    val SNOWFLAKE: Identifier = texture("snowflake")
    val STAR: Identifier = texture("star")
    val SHIT: Identifier = texture("shit")
    val TRIANGLE1: Identifier = texture("triangle1")
    val TRIANGLE2: Identifier = texture("triangle2")
    val TRIANGLE3: Identifier = texture("triangle3")
    val TRIANGLE4: Identifier = texture("triangle4")
    val HALO: Identifier = texture("halo")
    val TRIANGLE5: Identifier = texture("triangle5")
    val HIT_BUBBLE: Identifier = texture("hitbubble")
    val TARGET: Identifier = texture("target")

    private fun texture(name: String): Identifier = ResourceLocationUtils.getIdentifier("textures/particles/$name.png")

}
