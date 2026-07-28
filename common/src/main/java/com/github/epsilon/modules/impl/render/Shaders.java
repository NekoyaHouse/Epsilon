package com.github.epsilon.modules.impl.render;

import com.github.epsilon.holders.ShaderHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;

import java.awt.*;

public class Shaders extends Module {

    public static final Shaders INSTANCE = new Shaders();

    private Shaders() {
        super("Shaders", Category.RENDER);
    }

    public enum OutlineMode {
        Outline,
        Glow
    }

    private final SettingGroup sgTargets = settingGroup("Targets");
    private final SettingGroup sgEntityShader = settingGroup("Entity Shader");
    private final SettingGroup sgHandsShader = settingGroup("Hands Shader");
    private final SettingGroup sgChestShader = settingGroup("Chest Shader");

    private final BoolSetting hands = boolSetting("Hands", true).group(sgTargets);
    private final BoolSetting players = boolSetting("Players", true).group(sgTargets);
    private final BoolSetting self = boolSetting("Self", true, players::getValue).group(sgTargets);
    private final BoolSetting friends = boolSetting("Friends", true).group(sgTargets);
    private final BoolSetting crystals = boolSetting("Crystals", true).group(sgTargets);
    private final BoolSetting chests = boolSetting("Chests", true).group(sgTargets);
    private final BoolSetting creatures = boolSetting("Creatures", false).group(sgTargets);
    private final BoolSetting monsters = boolSetting("Monsters", false).group(sgTargets);
    private final BoolSetting ambients = boolSetting("Ambients", false).group(sgTargets);
    private final BoolSetting others = boolSetting("Others", false).group(sgTargets);

    public final IntSetting maxRange = intSetting("Max Range", 64, 16, 256, 1, () -> hasEntityTargets() || chests.getValue()).group(sgTargets);

    public final ShaderSettings entityShader = new ShaderSettings("Entity", sgEntityShader, this::hasEntityTargets);
    public final ShaderSettings handsShader = new ShaderSettings("Hands", sgHandsShader, hands::getValue);
    public final ShaderSettings chestShader = new ShaderSettings("Chest", sgChestShader, chests::getValue);

    public boolean shouldRenderHands() {
        return hands.getValue();
    }

    public boolean shouldRenderChest(BlockPos blockPos) {
        if (mc.player == null) {
            return false;
        }

        return chests.getValue() && mc.player.blockPosition().distSqr(blockPos) <= maxRange.getValue() * maxRange.getValue();
    }

    public boolean shouldRender(Entity entity) {
        if (mc.player == null) {
            return false;
        }

        if (mc.player.distanceToSqr(entity.position()) > maxRange.getValue() * maxRange.getValue()) {
            return false;
        }

        if (entity instanceof Player player) {
            if (player == mc.player && !self.getValue()) {
                return false;
            }
            if (Managers.FRIEND.isFriend(player)) {
                return friends.getValue();
            }
            return players.getValue();
        }

        if (entity instanceof EndCrystal) {
            return crystals.getValue();
        }

        return switch (entity.getType().getCategory()) {
            case CREATURE, WATER_CREATURE -> creatures.getValue();
            case MONSTER -> monsters.getValue();
            case AMBIENT, WATER_AMBIENT -> ambients.getValue();
            default -> others.getValue();
        };
    }

    private boolean hasEntityTargets() {
        return players.getValue() || friends.getValue() || crystals.getValue() || creatures.getValue() || monsters.getValue() || ambients.getValue() || others.getValue();
    }

    public class ShaderSettings {
        public final EnumSetting<ShaderHolder.Shader> mode;
        public final EnumSetting<OutlineMode> outlineMode;
        public final DoubleSetting factor;
        public final DoubleSetting gradient;
        public final IntSetting gradientAlpha;
        public final IntSetting lineWidth;
        public final IntSetting quality;
        public final IntSetting glowStrength;
        public final DoubleSetting glowMultiplier;
        public final IntSetting glowQuality;
        public final IntSetting octaves;
        public final IntSetting fillAlpha;
        public final BoolSetting glow;
        public final ColorSetting outlineColor;
        public final ColorSetting smokeOutlineColor1;
        public final ColorSetting smokeOutlineColor2;
        public final ColorSetting fillColor1;
        public final ColorSetting fillColor2;
        public final ColorSetting fillColor3;

        private ShaderSettings(String prefix, SettingGroup group, Setting.Dependency targetEnabled) {
            prefix = prefix + " ";
            mode = enumSetting(prefix + "Mode", ShaderHolder.Shader.Default, targetEnabled).group(group);
            Setting.Dependency outlineAvailable = () -> targetEnabled.check() && !mode.is(ShaderHolder.Shader.Snow);
            outlineMode = enumSetting(prefix + "Outline Mode", OutlineMode.Outline, outlineAvailable).group(group);
            factor = doubleSetting(prefix + "Gradient Factor", 2.0, 0.0, 20.0, 0.1, () -> targetEnabled.check() && mode.is(ShaderHolder.Shader.Gradient)).group(group);
            gradient = doubleSetting(prefix + "Gradient", 2.0, 0.0, 20.0, 0.1, () -> targetEnabled.check() && mode.is(ShaderHolder.Shader.Gradient)).group(group);
            gradientAlpha = intSetting(prefix + "Gradient Alpha", 170, 0, 255, 1, () -> targetEnabled.check() && mode.is(ShaderHolder.Shader.Gradient)).group(group);
            lineWidth = intSetting(prefix + "Line Width", 2, 0, 500, 1, () -> outlineAvailable.check() && outlineMode.is(OutlineMode.Outline)).group(group);
            quality = intSetting(prefix + "Quality", 3, 0, 6, 1, () -> targetEnabled.check() && (mode.is(ShaderHolder.Shader.Snow) || outlineMode.is(OutlineMode.Outline))).group(group);
            glowStrength = intSetting(prefix + "Glow Strength", 6, 1, 20, 1, () -> outlineAvailable.check() && outlineMode.is(OutlineMode.Glow)).group(group);
            glowMultiplier = doubleSetting(prefix + "Glow Multiplier", 1.0, 0.1, 3.0, 0.1, () -> outlineAvailable.check() && outlineMode.is(OutlineMode.Glow)).group(group);
            glowQuality = intSetting(prefix + "Glow Quality", 10, 1, 10, 1, () -> outlineAvailable.check() && outlineMode.is(OutlineMode.Glow)).group(group);
            octaves = intSetting(prefix + "Smoke Octaves", 10, 5, 30, 1, () -> targetEnabled.check() && (mode.is(ShaderHolder.Shader.Smoke) || mode.is(ShaderHolder.Shader.Gradient))).group(group);
            fillAlpha = intSetting(prefix + "Fill Alpha", 170, 0, 255, 1, () -> targetEnabled.check() && (mode.is(ShaderHolder.Shader.Smoke) || mode.is(ShaderHolder.Shader.Gradient) || mode.is(ShaderHolder.Shader.Fade))).group(group);
            glow = boolSetting(prefix + "Smoke Glow", true, () -> outlineAvailable.check() && outlineMode.is(OutlineMode.Outline)).group(group);
            outlineColor = colorSetting(prefix + "Outline", new Color(255, 255, 255, 136), () -> targetEnabled.check() && !mode.is(ShaderHolder.Shader.Gradient) && !mode.is(ShaderHolder.Shader.Snow)).group(group);
            smokeOutlineColor1 = colorSetting(prefix + "Smoke Outline", new Color(255, 255, 255, 136), () -> targetEnabled.check() && mode.is(ShaderHolder.Shader.Smoke)).group(group);
            smokeOutlineColor2 = colorSetting(prefix + "Smoke Outline 2", new Color(255, 255, 255, 136), () -> targetEnabled.check() && mode.is(ShaderHolder.Shader.Smoke)).group(group);
            fillColor1 = colorSetting(prefix + "Fill", new Color(255, 255, 255, 136), () -> targetEnabled.check() && !mode.is(ShaderHolder.Shader.Gradient)).group(group);
            fillColor2 = colorSetting(prefix + "Smoke Fill", new Color(255, 255, 255, 136), () -> targetEnabled.check() && (mode.is(ShaderHolder.Shader.Smoke) || mode.is(ShaderHolder.Shader.Fade))).group(group);
            fillColor3 = colorSetting(prefix + "Smoke Fill 2", new Color(255, 255, 255, 136), () -> targetEnabled.check() && mode.is(ShaderHolder.Shader.Smoke)).group(group);
        }
    }

}
