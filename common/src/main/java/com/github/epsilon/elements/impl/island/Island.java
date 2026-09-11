package com.github.epsilon.elements.impl.island;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.island.instance.LandController;
import com.github.epsilon.elements.impl.island.instance.impl.*;
import com.github.epsilon.elements.impl.island.pattern.impl.CheckPattern;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.modules.impl.player.Timer;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.client.ClientPlatform;
import com.github.epsilon.utils.client.PlatformRequirement;
import com.github.epsilon.utils.player.InvHelper;
import com.google.common.base.Suppliers;
import me.sofurry.smtc.SmtcService;
import me.sofurry.smtc.SmtcSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.function.Supplier;

public class Island extends HudModule {

    public static final Island INSTANCE = new Island();

    private Island() {
        super("Island", 0.0f, 0.0f, 154.0f, 24.0f);
    }

    private final BoolSetting targetHud = boolSetting("Target", true);
    private final BoolSetting scaffoldBlocks = boolSetting("Scaffold Blocks", true);
    private final BoolSetting timerBalance = boolSetting("Timer Balance", true);
    private final BoolSetting tabList = boolSetting("Tab List", true);
    private final BoolSetting music = boolSetting("Music", true).platformOnly(PlatformRequirement.WINDOWS_X64);
    public final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 50));
    public final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    public final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    public final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 1.0, 20.0, 1.0, backgroundBlur::getValue);
    public final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 10.0, 2.0, 32.0, 1.0, drawShadow::getValue);
    public final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(255, 255, 255, 110), drawShadow::getValue);
    public final BoolSetting outline = boolSetting("Outline", false);
    public final DoubleSetting outlineWidth = doubleSetting("Outline Width", 1.0, 0.1, 3.0, 0.1, outline::getValue);
    public final ColorSetting outlineColor = colorSetting("Outline Color", new Color(0, 0, 0, 125));
    public final BoolSetting textGlitch = boolSetting("Text Glitch", false);
    public final ColorSetting glitchColor = colorSetting("Glitch Color", Color.WHITE, false, textGlitch::getValue);
    public final DoubleSetting chromaticX = doubleSetting("Chromatic X", 0.8, 0.0, 6.0, 0.1, textGlitch::getValue);
    public final DoubleSetting chromaticY = doubleSetting("Chromatic Y", 0.0, 0.0, 6.0, 0.1, textGlitch::getValue);
    public final DoubleSetting glitchGlowRadius = doubleSetting("Glitch Glow Radius", 1.5, 0.0, 10.0, 0.1, textGlitch::getValue);
    public final DoubleSetting glitchGlowIntensity = doubleSetting("Glitch Glow Intensity", 0.9, 0.0, 3.0, 0.1, textGlitch::getValue);
    public final DoubleSetting sliceHeight = doubleSetting("Slice Height", 4.0, 1.0, 32.0, 0.5, textGlitch::getValue);
    public final DoubleSetting sliceAmount = doubleSetting("Slice Amount", 1.2, 0.0, 10.0, 0.1, textGlitch::getValue);
    public final DoubleSetting glitchStrength = doubleSetting("Glitch Strength", 0.35, 0.0, 1.0, 0.05, textGlitch::getValue);
    public final DoubleSetting scanlineStrength = doubleSetting("Scanline Strength", 0.2, 0.0, 1.0, 0.05, textGlitch::getValue);
    public final DoubleSetting noiseStrength = doubleSetting("Noise Strength", 0.1, 0.0, 1.0, 0.05, textGlitch::getValue);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final LandRenderer landRenderer = new LandRenderer(LandController.INSTANCE, 48.0f, 48.0f);

    private LivingEntity target;

    @Override
    protected void onEnable() {
        if (music.getValue() && ClientPlatform.isWindowsX64()) SmtcService.INSTANCE.start();
    }

    @Override
    protected void onDisable() {
        LandController.INSTANCE.removeInstances(TargetInstance.class);
        LandController.INSTANCE.removeInstances(ScaffoldBlocksInstance.class);
        LandController.INSTANCE.removeInstances(BalanceInstance.class);
        LandController.INSTANCE.removeInstances(TabListInstance.class);
        LandController.INSTANCE.removeInstances(MusicInstance.class);
        SmtcService.INSTANCE.stop();
        target = null;
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        landRenderer.setTextRenderer(textRendererSupplier.get());
        landRenderer.update();

        updateTargetInstance();
        updateBlockInstance();
        updateBalanceInstance();
        updateTabListInstance();
        updateMusicInstance();

        setBounds(landRenderer.getWidth(), landRenderer.getHeight());

        landRenderer.syncPosition(this.x, this.y);
        landRenderer.draw(this, renderScope());
    }

    private void updateTargetInstance() {
        if (!targetHud.getValue()) {
            target = null;
            LandController.INSTANCE.removeInstances(TargetInstance.class);
            return;
        }

        LivingEntity next = null;
        if (KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.target != null) {
            next = KillAura.INSTANCE.target;
        } else if (mc.gui.screen() instanceof ChatScreen) {
            next = mc.player;
        }
        target = next;

        TargetInstance instance = LandController.INSTANCE.getInstance(TargetInstance.class);
        if (target != null && (instance == null || instance.getTarget() != target)) {
            LivingEntity captured = target;
            LandController.INSTANCE.post(new TargetInstance(captured, textRendererSupplier,
                    new CheckPattern(() -> target != captured))
            );
        }
    }

    private void updateBlockInstance() {
        if (!scaffoldBlocks.getValue() || !Scaffold.INSTANCE.isEnabled()) {
            LandController.INSTANCE.removeInstances(ScaffoldBlocksInstance.class);
            return;
        }

        ScaffoldBlocksInstance instance = LandController.INSTANCE.getInstance(ScaffoldBlocksInstance.class);
        if (instance == null) {
            Supplier<Integer> count = InvHelper::getBlockCountInInventory;
            Supplier<ItemStack> icon = Scaffold.INSTANCE::getBlockStack;
            LandController.INSTANCE.post(new ScaffoldBlocksInstance(count, icon, textRendererSupplier,
                    new CheckPattern(() -> !scaffoldBlocks.getValue() || !Scaffold.INSTANCE.isEnabled()))
            );
        }
    }

    private void updateBalanceInstance() {
        if (!timerBalance.getValue() || !Timer.INSTANCE.isEnabled()) {
            LandController.INSTANCE.removeInstances(BalanceInstance.class);
            return;
        }

        BalanceInstance instance = LandController.INSTANCE.getInstance(BalanceInstance.class);
        if (instance == null) {
            LandController.INSTANCE.post(new BalanceInstance(
                    Timer.INSTANCE::getBalanceTime,
                    Timer.INSTANCE::getMaxBalance,
                    textRendererSupplier,
                    new CheckPattern(() -> !timerBalance.getValue()
                            || !Timer.INSTANCE.isEnabled()
                            || (!Timer.INSTANCE.isReleasing() && Timer.INSTANCE.getBalanceTime() >= Timer.INSTANCE.getMaxBalance()))
            ));
        }
    }

    private void updateTabListInstance() {
        if (!tabList.getValue() || nullCheck() || mc.gui.screen() != null || !mc.options.keyPlayerList.isDown()) {
            LandController.INSTANCE.removeInstances(TabListInstance.class);
            return;
        }

        if (LandController.INSTANCE.getInstance(TabListInstance.class) == null) {
            LandController.INSTANCE.post(new TabListInstance(textRendererSupplier,
                    new CheckPattern(() -> !tabList.getValue() || nullCheck() || mc.gui.screen() != null || !mc.options.keyPlayerList.isDown()))
            );
        }
    }

    private void updateMusicInstance() {
        if (music.getValue() && ClientPlatform.isWindowsX64()) {
            SmtcService.INSTANCE.start();
            SmtcSnapshot snapshot = SmtcService.INSTANCE.snapshot();
            if (!snapshot.available() || !snapshot.isPlaying()) {
                LandController.INSTANCE.removeInstances(MusicInstance.class);
                return;
            }

            if (LandController.INSTANCE.getInstance(MusicInstance.class) == null) {
                LandController.INSTANCE.post(new MusicInstance(
                        SmtcService.INSTANCE,
                        textRendererSupplier,
                        new CheckPattern(() -> {
                            SmtcSnapshot current = SmtcService.INSTANCE.snapshot();
                            return !music.getValue() || !current.available() || !current.isPlaying();
                        })
                ));
            }
        } else {
            LandController.INSTANCE.removeInstances(MusicInstance.class);
            SmtcService.INSTANCE.stop();
        }
    }

}
