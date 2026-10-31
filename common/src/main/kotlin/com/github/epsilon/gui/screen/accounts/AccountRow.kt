package com.github.epsilon.gui.screen.accounts

import com.github.epsilon.accounts.Account
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import net.minecraft.util.Mth
import java.awt.Color

class AccountRow(private val account: Account<*>) {

    private var bounds: UiRect? = null
    private var loginBounds: UiRect? = null
    private var deleteBounds: UiRect? = null
    private var hoverProgress: Float = 0.0f
    private var focusProgress: Float = 0.0f
    private var confirmingDelete: Boolean = false

    fun getAccount(): Account<*> {
        return account
    }

    fun getBounds(): UiRect? {
        return bounds
    }

    fun getLoginBounds(): UiRect? {
        return loginBounds
    }

    fun getDeleteBounds(): UiRect? {
        return deleteBounds
    }

    fun isConfirmingDelete(): Boolean {
        return confirmingDelete
    }

    fun setConfirmingDelete(confirmingDelete: Boolean) {
        this.confirmingDelete = confirmingDelete
    }

    fun draw(
        scope: UiTree.Scope,
        rowBounds: UiRect,
        mouseX: Double,
        mouseY: Double,
        textRenderer: TextRenderer,
        state: State,
    ) {
        this.bounds = rowBounds

        val hovered = rowBounds.contains(mouseX, mouseY)
        hoverProgress = Mth.lerp(0.24f, hoverProgress, if (hovered) 1.0f else 0.0f)
        focusProgress = Mth.lerp(0.24f, focusProgress, if (state.keyboardFocused) 1.0f else 0.0f)

        val deleteX = rowBounds.width - TRAILING_INSET - ACTION_SIZE
        val loginX = deleteX - ACTION_GAP - ACTION_SIZE
        val actionY = (rowBounds.height - ACTION_SIZE) / 2.0f
        loginBounds = UiRect(rowBounds.x + loginX, rowBounds.y + actionY, ACTION_SIZE, ACTION_SIZE)
        deleteBounds = UiRect(rowBounds.x + deleteX, rowBounds.y + actionY, ACTION_SIZE, ACTION_SIZE)

        scope.pushAbsolute(rowBounds) { s ->
            val w = rowBounds.width
            val h = rowBounds.height

            s.roundRect(0.0f, 0.0f, w, h, MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hoverProgress))

            if (state.current) {
                s.roundRect(
                    0.0f, 0.0f, w, h, MD3Theme.CARD_RADIUS.toFloat(),
                    MD3Theme.stateLayer(MD3Theme.PRIMARY, 0.18f, 40),
                )
            }
            if (focusProgress > 0.01f) {
                s.outline(
                    OUTLINE_INSET, OUTLINE_INSET, w - OUTLINE_INSET * 2.0f, h - OUTLINE_INSET * 2.0f,
                    MD3Theme.CARD_RADIUS.toFloat(), 1.0f,
                    MD3Theme.withAlpha(MD3Theme.PRIMARY, (200 * focusProgress).toInt()),
                )
            }

            val head = account.cache.getHeadTexture()
            val avatarY = (h - AVATAR_SIZE) / 2.0f
            s.roundedTexture(
                head, CONTENT_INSET, avatarY, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE * 0.25f,
                0.0f, 0.0f, 1.0f, 1.0f, Color.WHITE,
            )

            val textX = CONTENT_INSET + AVATAR_SIZE + 10.0f
            val available = loginX - textX - 8.0f

            val username = account.username
            val nameScale = 1.0f
            val nameHeight = textRenderer.getHeight(nameScale)
            val clippedName = clip(textRenderer, username, nameScale, available)

            val secondary = account.type.name
            val subScale = 0.76f
            val subHeight = textRenderer.getHeight(subScale)

            val lineGap = 3.0f
            val blockHeight = nameHeight + lineGap + subHeight
            val nameY = (h - blockHeight) / 2.0f
            val subY = nameY + nameHeight + lineGap

            s.text(clippedName, textX, nameY, nameScale, if (state.current) MD3Theme.PRIMARY else MD3Theme.TEXT_PRIMARY)
            s.text(secondary, textX, subY, subScale, MD3Theme.TEXT_MUTED)

            if (state.current) {
                val subWidth = textRenderer.getWidth(secondary, subScale)
                val badge = EpsilonTranslations.Gui.ACCOUNTS_CURRENT.getTranslatedName()
                val badgeScale = 0.46f
                val badgeTextWidth = textRenderer.getWidth(badge, badgeScale)
                val badgeWidth = badgeTextWidth + 10.0f
                val badgeHeight = subHeight + 4.0f
                val badgeX = textX + subWidth + 6.0f
                if (badgeX + badgeWidth <= loginX - 8.0f) {
                    s.roundRect(
                        badgeX, subY - 2.0f, badgeWidth, badgeHeight,
                        MD3Theme.CHIP_RADIUS.toFloat(), MD3Theme.PRIMARY_CONTAINER,
                    )
                    s.text(
                        badge,
                        badgeX + (badgeWidth - badgeTextWidth) / 2.0f,
                        subY - 2.0f + (badgeHeight - textRenderer.getHeight(badgeScale)) / 2.0f,
                        badgeScale,
                        MD3Theme.ON_PRIMARY_CONTAINER,
                    )
                }
            }

            drawLoginAction(s, textRenderer, loginX, actionY, state)
            drawDeleteAction(s, textRenderer, deleteX, actionY, mouseX, mouseY, state)
        }
    }

    private fun drawLoginAction(scope: UiTree.Scope, textRenderer: TextRenderer, x: Float, y: Float, state: State) {
        val bounds = loginBounds
        val hovered = bounds != null && bounds.contains(state.mouseX, state.mouseY)

        if (state.busy) {
            scope.roundRect(x, y, ACTION_SIZE, ACTION_SIZE, ACTION_SIZE / 2.0f, MD3Theme.PRIMARY)
            drawGlyph(scope, textRenderer, IconChars.HOURGLASS_EMPTY, x, y, 1.0f, MD3Theme.ON_PRIMARY)
            return
        }

        val dimmed = state.anyBusy
        val bg = if (dimmed) {
            MD3Theme.withAlpha(MD3Theme.PRIMARY, 60)
        } else {
            MD3Theme.withAlpha(MD3Theme.PRIMARY, if (hovered) 255 else 210)
        }
        val fg = if (dimmed) MD3Theme.withAlpha(MD3Theme.ON_PRIMARY, 110) else MD3Theme.ON_PRIMARY

        scope.roundRect(x, y, ACTION_SIZE, ACTION_SIZE, ACTION_SIZE / 2.0f, bg)
        drawGlyph(scope, textRenderer, IconChars.LOGIN, x, y, 1.0f, fg)
    }

    private fun drawDeleteAction(
        scope: UiTree.Scope,
        textRenderer: TextRenderer,
        x: Float,
        y: Float,
        mouseX: Double,
        mouseY: Double,
        state: State,
    ) {
        val bounds = deleteBounds
        val hovered = bounds != null && bounds.contains(mouseX, mouseY)

        if (confirmingDelete) {
            scope.roundRect(x, y, ACTION_SIZE, ACTION_SIZE, ACTION_SIZE / 2.0f, MD3Theme.ERROR)
            drawGlyph(scope, textRenderer, IconChars.DELETE, x, y, 1.0f, Color(40, 20, 20))
            return
        }

        if (state.busy) {
            drawGlyph(scope, textRenderer, IconChars.DELETE, x, y, 1.0f, MD3Theme.withAlpha(MD3Theme.ERROR, 70))
            return
        }

        if (hovered) {
            scope.roundRect(
                x, y, ACTION_SIZE, ACTION_SIZE, ACTION_SIZE / 2.0f,
                MD3Theme.stateLayer(MD3Theme.ERROR, 0.9f, 30),
            )
        }
        drawGlyph(
            scope,
            textRenderer,
            IconChars.DELETE,
            x,
            y,
            1.0f,
            MD3Theme.withAlpha(MD3Theme.ERROR, if (hovered) 255 else 190)
        )
    }

    data class State(
        val current: Boolean,
        val busy: Boolean,
        val anyBusy: Boolean,
        val keyboardFocused: Boolean,
        val mouseX: Double,
        val mouseY: Double,
    )

    companion object {

        const val HEIGHT: Float = 52.0f
        const val GAP: Float = 6.0f

        private const val AVATAR_SIZE = 32.0f
        private const val CONTENT_INSET = 12.0f
        private const val ACTION_SIZE = 28.0f
        private const val ACTION_GAP = 4.0f
        private const val TRAILING_INSET = 8.0f
        private const val OUTLINE_INSET = 0.5f

        private fun drawGlyph(
            scope: UiTree.Scope,
            textRenderer: TextRenderer,
            glyph: String,
            x: Float,
            y: Float,
            scale: Float,
            color: Color,
        ) {
            val glyphWidth = textRenderer.getWidth(glyph, scale, StaticFontLoader.ICONS)
            val glyphHeight = textRenderer.getHeight(scale, StaticFontLoader.ICONS)
            scope.text(
                glyph,
                x + (ACTION_SIZE - glyphWidth) / 2.0f,
                y + (ACTION_SIZE - glyphHeight) / 2.0f,
                scale,
                color,
                StaticFontLoader.ICONS,
            )
        }

        private fun clip(textRenderer: TextRenderer, text: String, scale: Float, available: Float): String {
            if (available <= 0.0f) return ""
            if (textRenderer.getWidth(text, scale) <= available) return text

            val ellipsis = "..."
            val ellipsisWidth = textRenderer.getWidth(ellipsis, scale)
            val builder = StringBuilder()
            for (i in 0 until text.length) {
                builder.append(text[i])
                if (textRenderer.getWidth(builder.toString(), scale) + ellipsisWidth > available) {
                    builder.deleteCharAt(builder.length - 1)
                    break
                }
            }
            return builder.toString() + ellipsis
        }

    }

}
