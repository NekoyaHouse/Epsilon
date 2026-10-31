package com.github.epsilon.events.impl

import net.minecraft.client.gui.GuiGraphicsExtractor

open class Render2DEvent protected constructor(val guiGraphics: GuiGraphicsExtractor) {

    class Level(guiGraphics: GuiGraphicsExtractor) : Render2DEvent(guiGraphics)

    class HUD(guiGraphics: GuiGraphicsExtractor) : Render2DEvent(guiGraphics)

}
