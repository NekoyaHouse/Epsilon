package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.utils.render.ColorUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Random;

public class Health extends Module {

    public static final Health INSTANCE = new Health();

    private static final String HEART_CHARACTER = "\u2764";
    private static final int HEART_COLOR = 0xFFFF5555; // §c
    private static final int ABSORPTION_COLOR = 0xFFFFFF55; // §e
    private static final int ABSORPTION_HEART_COLOR = 0xFFFFAA00; // §6
    private static final int HEART_SIZE = 9;
    private static final int HEART_SPACING = 8;
    private static final int HEARTS_PER_ROW = 10;
    private static final int ROW_HEIGHT = 10;

    private static final float[] HEALTH_COLOR_FRACTIONS = {0.0f, 0.5f, 1.0f};
    private static final Color[] HEALTH_COLORS = {
            new Color(255, 37, 0),
            Color.YELLOW,
            Color.GREEN
    };

    private final DecimalFormat decimalFormat = new DecimalFormat("0.#", new DecimalFormatSymbols(Locale.ENGLISH));
    private final Random random = new Random();

    private boolean healthStateInitialized;
    private int lastHealth;
    private int displayHealth;
    private int blinkUntilTick = -1;

    private Health() {
        super("Health", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        resetHealthState();
    }

    @Override
    protected void onDisable() {
        resetHealthState();
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        if (nullCheck()) return;

        renderHealth(event.getGuiGraphics());
    }

    private void renderHealth(GuiGraphicsExtractor graphics) {
        Player player = mc.player;
        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        float maxHealth = player.getMaxHealth();

        updateHealthState(player);

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int offsetY = getScreenOffsetY(mc.gui.screen());

        drawHealthText(graphics, health, absorption, maxHealth, centerX, centerY + 25 + offsetY);
        drawHearts(graphics, player, health, maxHealth, centerX, centerY + 15 + offsetY);
    }

    private void updateHealthState(Player player) {
        int currentHealth = Mth.ceil(player.getHealth());

        if (!healthStateInitialized) {
            lastHealth = currentHealth;
            displayHealth = currentHealth;
            healthStateInitialized = true;
            return;
        }

        if (currentHealth != lastHealth && player.invulnerableTime > 0) {
            blinkUntilTick = player.tickCount + (currentHealth < lastHealth ? 20 : 10);
        }

        lastHealth = currentHealth;

        if (player.tickCount >= blinkUntilTick) {
            displayHealth = currentHealth;
        }
    }

    private void resetHealthState() {
        healthStateInitialized = false;
        lastHealth = 0;
        displayHealth = 0;
        blinkUntilTick = -1;
    }

    private void drawHealthText(GuiGraphicsExtractor graphics, float health, float absorption, float maxHealth, int centerX, int y) {
        int x = centerX - 6 - (absorption > 0.0f ? 12 : 0);
        int cursorX = x;

        String healthText = decimalFormat.format(health / 2.0f);
        Color healthColor = blendColors(
                HEALTH_COLOR_FRACTIONS,
                HEALTH_COLORS,
                Mth.clamp(health / maxHealth, 0.0f, 1.0f)
        );

        graphics.text(mc.font, healthText, cursorX, y, healthColor.getRGB(), true);
        cursorX += mc.font.width(healthText);

        graphics.text(mc.font, HEART_CHARACTER, cursorX, y, HEART_COLOR, true);
        cursorX += mc.font.width(HEART_CHARACTER) + mc.font.width(" ");

        if (absorption > 0.0f) {
            String absorptionText = decimalFormat.format(absorption / 2.0f);
            graphics.text(mc.font, absorptionText, cursorX, y, ABSORPTION_COLOR, true);
            cursorX += mc.font.width(absorptionText);
            graphics.text(mc.font, HEART_CHARACTER, cursorX, y, ABSORPTION_HEART_COLOR, true);
        }
    }

    private void drawHearts(GuiGraphicsExtractor graphics, Player player, float health, float maxHealth, int centerX, int baseY) {
        int currentHealth = Mth.ceil(health);
        int containerCount = Mth.ceil(maxHealth / 2.0f);
        if (containerCount <= 0) return;

        HeartType heartType = HeartType.forPlayer(player);
        boolean hardcore = player.level().getLevelData().isHardcore();
        boolean blink = player.tickCount < blinkUntilTick && (blinkUntilTick - player.tickCount) / 3 % 2 == 1;

        this.random.setSeed((long) player.tickCount * 312871L);
        int regenOffsetIndex = player.hasEffect(MobEffects.REGENERATION)
                ? player.tickCount % Mth.ceil(maxHealth + 5.0f)
                : -1;

        for (int i = containerCount - 1; i >= 0; --i) {
            int row = i / HEARTS_PER_ROW;
            int column = i % HEARTS_PER_ROW;
            int columnsInRow = Math.min(HEARTS_PER_ROW, containerCount - row * HEARTS_PER_ROW);
            float rowX = centerX - (columnsInRow * HEART_SPACING + 1) / 2.0f;
            float x = rowX + column * HEART_SPACING;
            float y = baseY - row * ROW_HEIGHT;

            if (currentHealth <= 4) {
                y += this.random.nextInt(2);
            }

            if (i == regenOffsetIndex) {
                y -= 2.0f;
            }

            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    HeartType.CONTAINER.getSprite(hardcore, false, blink),
                    (int) x,
                    (int) y,
                    HEART_SIZE,
                    HEART_SIZE
            );

            int halves = i * 2;
            if (blink && halves < displayHealth) {
                boolean halfHeart = halves + 1 == displayHealth;
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        heartType.getSprite(hardcore, halfHeart, true),
                        (int) x,
                        (int) y,
                        HEART_SIZE,
                        HEART_SIZE
                );
            }

            if (halves < currentHealth) {
                boolean halfHeart = halves + 1 == currentHealth;
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        heartType.getSprite(hardcore, halfHeart, false),
                        (int) x,
                        (int) y,
                        HEART_SIZE,
                        HEART_SIZE
                );
            }
        }
    }

    private int getScreenOffsetY(Screen screen) {
        if (screen instanceof InventoryScreen) {
            return 70;
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            return 80;
        }
        if (screen instanceof ContainerScreen container) {
            // 现代 ContainerScreen 的高度为 114 + 行数 * 18，等价于旧版 GuiChest 的 ySize。
            int imageHeight = 114 + container.getMenu().getRowCount() * 18;
            return imageHeight / 2 - 15;
        }
        return 0;
    }

    private Color blendColors(float[] fractions, Color[] colors, float progress) {
        if (fractions.length == 0 || fractions.length != colors.length) {
            return Color.WHITE;
        }

        progress = Mth.clamp(progress, 0.0f, 1.0f);

        if (progress <= fractions[0]) {
            return colors[0];
        }
        if (progress >= fractions[fractions.length - 1]) {
            return colors[colors.length - 1];
        }

        for (int i = 0; i < fractions.length - 1; i++) {
            float start = fractions[i];
            float end = fractions[i + 1];
            if (progress >= start && progress <= end) {
                float weight = (progress - start) / (end - start);
                return ColorUtils.interpolateColor(colors[i], colors[i + 1], weight);
            }
        }

        return colors[colors.length - 1];
    }

    private enum HeartType {
        CONTAINER(
                sprite("container"),
                sprite("container_blinking"),
                sprite("container"),
                sprite("container_blinking"),
                sprite("container_hardcore"),
                sprite("container_hardcore_blinking"),
                sprite("container_hardcore"),
                sprite("container_hardcore_blinking")
        ),
        NORMAL(
                sprite("full"),
                sprite("full_blinking"),
                sprite("half"),
                sprite("half_blinking"),
                sprite("hardcore_full"),
                sprite("hardcore_full_blinking"),
                sprite("hardcore_half"),
                sprite("hardcore_half_blinking")
        ),
        POISONED(
                sprite("poisoned_full"),
                sprite("poisoned_full_blinking"),
                sprite("poisoned_half"),
                sprite("poisoned_half_blinking"),
                sprite("poisoned_hardcore_full"),
                sprite("poisoned_hardcore_full_blinking"),
                sprite("poisoned_hardcore_half"),
                sprite("poisoned_hardcore_half_blinking")
        ),
        WITHERED(
                sprite("withered_full"),
                sprite("withered_full_blinking"),
                sprite("withered_half"),
                sprite("withered_half_blinking"),
                sprite("withered_hardcore_full"),
                sprite("withered_hardcore_full_blinking"),
                sprite("withered_hardcore_half"),
                sprite("withered_hardcore_half_blinking")
        ),
        ABSORBING(
                sprite("absorbing_full"),
                sprite("absorbing_full_blinking"),
                sprite("absorbing_half"),
                sprite("absorbing_half_blinking"),
                sprite("absorbing_hardcore_full"),
                sprite("absorbing_hardcore_full_blinking"),
                sprite("absorbing_hardcore_half"),
                sprite("absorbing_hardcore_half_blinking")
        ),
        FROZEN(
                sprite("frozen_full"),
                sprite("frozen_full_blinking"),
                sprite("frozen_half"),
                sprite("frozen_half_blinking"),
                sprite("frozen_hardcore_full"),
                sprite("frozen_hardcore_full_blinking"),
                sprite("frozen_hardcore_half"),
                sprite("frozen_hardcore_half_blinking")
        );

        private final Identifier full;
        private final Identifier fullBlinking;
        private final Identifier half;
        private final Identifier halfBlinking;
        private final Identifier hardcoreFull;
        private final Identifier hardcoreFullBlinking;
        private final Identifier hardcoreHalf;
        private final Identifier hardcoreHalfBlinking;

        HeartType(
                Identifier full,
                Identifier fullBlinking,
                Identifier half,
                Identifier halfBlinking,
                Identifier hardcoreFull,
                Identifier hardcoreFullBlinking,
                Identifier hardcoreHalf,
                Identifier hardcoreHalfBlinking
        ) {
            this.full = full;
            this.fullBlinking = fullBlinking;
            this.half = half;
            this.halfBlinking = halfBlinking;
            this.hardcoreFull = hardcoreFull;
            this.hardcoreFullBlinking = hardcoreFullBlinking;
            this.hardcoreHalf = hardcoreHalf;
            this.hardcoreHalfBlinking = hardcoreHalfBlinking;
        }

        private Identifier getSprite(boolean isHardcore, boolean isHalf, boolean isBlinking) {
            if (!isHardcore) {
                if (isHalf) {
                    return isBlinking ? halfBlinking : half;
                }
                return isBlinking ? fullBlinking : full;
            }

            if (isHalf) {
                return isBlinking ? hardcoreHalfBlinking : hardcoreHalf;
            }
            return isBlinking ? hardcoreFullBlinking : hardcoreFull;
        }

        private static Identifier sprite(String name) {
            return Identifier.withDefaultNamespace("hud/heart/" + name);
        }

        private static HeartType forPlayer(Player player) {
            if (player.hasEffect(MobEffects.POISON)) {
                return POISONED;
            }
            if (player.hasEffect(MobEffects.WITHER)) {
                return WITHERED;
            }
            if (player.isFullyFrozen()) {
                return FROZEN;
            }
            return NORMAL;
        }
    }
}
