package com.github.epsilon.modules.impl.render;

import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.managers.target.TargetManager;
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

    public enum GlowColorMode {
        Solid,
        Gradient
    }

    private final SettingGroup sgTargets = settingGroup("Targets");
    private final SettingGroup sgEntityShader = settingGroup("Entity Shader");
    private final SettingGroup sgHandsShader = settingGroup("Hands Shader");
    private final SettingGroup sgChestShader = settingGroup("Chest Shader");

    public final BoolSetting hands = boolSetting("Hands", true).group(sgTargets);
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

    public final ShaderSettings entityShader = new ShaderSettings("", sgEntityShader, this::hasEntityTargets);
    public final ColorSetting friendsColor = colorSetting("Friends Color", new Color(0x30FF00), false, friends::getValue).group(sgEntityShader);
    public final ShaderSettings handsShader = new ShaderSettings("Hands ", sgHandsShader, hands::getValue);
    public final ShaderSettings chestShader = new ShaderSettings("Chest ", sgChestShader, chests::getValue);

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
            if (isFriendOrSameTeam(player)) {
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

    public int getOutlineColor(ShaderSettings settings) {
        if (settings.outlineMode.is(OutlineMode.Glow)) {
            Color glowColor = settings.glowColorMode.is(GlowColorMode.Gradient) ? settings.glowColor1.getValue() : settings.glowColor.getValue();
            return glowColor.getRGB();
        }
        return settings.outlineColor.getValue().getRGB();
    }

    public int getOutlineColor(Entity entity, ShaderSettings settings) {
        if (settings == entityShader && isFriendOrSameTeam(entity)) {
            return friendsColor.getValue().getRGB();
        }
        return getOutlineColor(settings);
    }

    private boolean isFriendOrSameTeam(Entity entity) {
        return entity != mc.player && entity instanceof Player player && (FriendManager.INSTANCE.isFriend(player) || TargetManager.INSTANCE.isSameTeam(player));
    }

    private boolean hasEntityTargets() {
        return players.getValue() || friends.getValue() || crystals.getValue() || creatures.getValue()
                || monsters.getValue() || ambients.getValue() || others.getValue();
    }

    public class ShaderSettings {
        public final EnumSetting<ShaderManager.Shader> mode;
        public final EnumSetting<OutlineMode> outlineMode;
        public final EnumSetting<GlowColorMode> glowColorMode;
        public final IntSetting glowRadius;
        public final DoubleSetting glowExposure;
        public final ColorSetting glowColor;
        public final ColorSetting glowColor1;
        public final ColorSetting glowColor2;
        public final DoubleSetting glowGradientSpeed;
        public final DoubleSetting factor;
        public final DoubleSetting gradient;
        public final IntSetting gradientAlpha;
        public final IntSetting lineWidth;
        public final IntSetting quality;
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
            mode = enumSetting(prefix + "Mode", ShaderManager.Shader.Default, targetEnabled).group(group);
            outlineMode = enumSetting(prefix + "Outline Mode", OutlineMode.Outline, targetEnabled).group(group);
            Setting.Dependency outlineAvailable = () -> targetEnabled.check() && outlineMode.is(OutlineMode.Outline);
            Setting.Dependency glowAvailable = () -> targetEnabled.check() && outlineMode.is(OutlineMode.Glow);
            glowColorMode = enumSetting(prefix + "Glow Color Mode", GlowColorMode.Solid, glowAvailable).group(group);
            glowRadius = intSetting(prefix + "Glow Radius", 4, 2, 30, 1, glowAvailable).group(group);
            glowExposure = doubleSetting(prefix + "Glow Exposure", 2.2, 0.5, 3.5, 0.1, glowAvailable).group(group);
            glowColor = colorSetting(prefix + "Glow Color", new Color(255, 183, 197), () -> glowAvailable.check() && glowColorMode.is(GlowColorMode.Solid)).group(group);
            glowColor1 = colorSetting(prefix + "Glow Color 1", new Color(255, 183, 197), () -> glowAvailable.check() && glowColorMode.is(GlowColorMode.Gradient)).group(group);
            glowColor2 = colorSetting(prefix + "Glow Color 2", new Color(0, 255, 200), () -> glowAvailable.check() && glowColorMode.is(GlowColorMode.Gradient)).group(group);
            glowGradientSpeed = doubleSetting(prefix + "Glow Gradient Speed", 2.0, 0.1, 10.0, 0.1, () -> glowAvailable.check() && glowColorMode.is(GlowColorMode.Gradient)).group(group);
            factor = doubleSetting(prefix + "Gradient Factor", 2.0, 0.0, 20.0, 0.1, () -> outlineAvailable.check() && mode.is(ShaderManager.Shader.Gradient)).group(group);
            gradient = doubleSetting(prefix + "Gradient", 2.0, 0.0, 20.0, 0.1, () -> outlineAvailable.check() && mode.is(ShaderManager.Shader.Gradient)).group(group);
            gradientAlpha = intSetting(prefix + "Gradient Alpha", 170, 0, 255, 1, () -> outlineAvailable.check() && mode.is(ShaderManager.Shader.Gradient)).group(group);
            lineWidth = intSetting(prefix + "Line Width", 2, 0, 500, 1, () -> outlineAvailable.check() && !mode.is(ShaderManager.Shader.Snow)).group(group);
            quality = intSetting(prefix + "Quality", 3, 0, 6, 1, outlineAvailable).group(group);
            octaves = intSetting(prefix + "Smoke Octaves", 10, 5, 30, 1, () -> outlineAvailable.check() && (mode.is(ShaderManager.Shader.Smoke) || mode.is(ShaderManager.Shader.Gradient))).group(group);
            fillAlpha = intSetting(prefix + "Fill Alpha", 170, 0, 255, 1, () -> outlineAvailable.check() && (mode.is(ShaderManager.Shader.Smoke) || mode.is(ShaderManager.Shader.Gradient) || mode.is(ShaderManager.Shader.Fade))).group(group);
            glow = boolSetting(prefix + "Smoke Glow", true, () -> outlineAvailable.check() && !mode.is(ShaderManager.Shader.Snow)).group(group);
            outlineColor = colorSetting(prefix + "Outline", new Color(255, 255, 255, 136), () -> outlineAvailable.check() && !mode.is(ShaderManager.Shader.Gradient) && !mode.is(ShaderManager.Shader.Snow)).group(group);
            smokeOutlineColor1 = colorSetting(prefix + "Smoke Outline", new Color(255, 255, 255, 136), () -> outlineAvailable.check() && mode.is(ShaderManager.Shader.Smoke)).group(group);
            smokeOutlineColor2 = colorSetting(prefix + "Smoke Outline 2", new Color(250, 250, 250, 136), () -> outlineAvailable.check() && mode.is(ShaderManager.Shader.Smoke)).group(group);
            fillColor1 = colorSetting(prefix + "Fill", new Color(255, 255, 255, 136), () -> outlineAvailable.check() && !mode.is(ShaderManager.Shader.Gradient)).group(group);
            fillColor2 = colorSetting(prefix + "Smoke Fill", new Color(255, 255, 255, 136), () -> outlineAvailable.check() && (mode.is(ShaderManager.Shader.Smoke) || mode.is(ShaderManager.Shader.Fade))).group(group);
            fillColor3 = colorSetting(prefix + "Smoke Fill 2", new Color(255, 255, 255, 136), () -> outlineAvailable.check() && mode.is(ShaderManager.Shader.Smoke)).group(group);
        }
    }

}
