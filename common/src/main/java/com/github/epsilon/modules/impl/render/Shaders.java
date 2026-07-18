package com.github.epsilon.modules.impl.render;

import com.github.epsilon.holders.ShaderHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
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

    private final BoolSetting hands = boolSetting("Hands", true);
    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting self = boolSetting("Self", true, players::getValue);
    private final BoolSetting friends = boolSetting("Friends", true);
    private final BoolSetting crystals = boolSetting("Crystals", true);
    private final BoolSetting chests = boolSetting("Chests", true);
    private final BoolSetting creatures = boolSetting("Creatures", false);
    private final BoolSetting monsters = boolSetting("Monsters", false);
    private final BoolSetting ambients = boolSetting("Ambients", false);
    private final BoolSetting others = boolSetting("Others", false);

    public final EnumSetting<ShaderHolder.Shader> mode = enumSetting("Mode", ShaderHolder.Shader.Default);
    public final EnumSetting<ShaderHolder.Shader> handsMode = enumSetting("Hands Mode", ShaderHolder.Shader.Default);
    public final EnumSetting<ShaderHolder.Shader> chestMode = enumSetting("Chest Mode", ShaderHolder.Shader.Default);

    public final IntSetting maxRange = intSetting("Max Range", 64, 16, 256, 1, () -> players.getValue() || crystals.getValue() || chests.getValue() || friends.getValue() || creatures.getValue() || monsters.getValue() || ambients.getValue() || others.getValue());
    public final DoubleSetting factor = doubleSetting("Gradient Factor", 2.0, 0.0, 20.0, 0.1, () -> mode.is(ShaderHolder.Shader.Gradient) || handsMode.is(ShaderHolder.Shader.Gradient) || chestMode.is(ShaderHolder.Shader.Gradient));
    public final DoubleSetting gradient = doubleSetting("Gradient", 2.0, 0.0, 20.0, 0.1, () -> mode.is(ShaderHolder.Shader.Gradient) || handsMode.is(ShaderHolder.Shader.Gradient) || chestMode.is(ShaderHolder.Shader.Gradient));
    public final IntSetting alpha2 = intSetting("Gradient Alpha", 170, 0, 255, 1, () -> mode.is(ShaderHolder.Shader.Gradient) || handsMode.is(ShaderHolder.Shader.Gradient) || chestMode.is(ShaderHolder.Shader.Gradient));
    public final IntSetting lineWidth = intSetting("Line Width", 2, 0, 500, 1);
    public final IntSetting quality = intSetting("Quality", 3, 0, 6, 1);
    public final IntSetting octaves = intSetting("Smoke Octaves", 10, 5, 30, 1);
    public final IntSetting fillAlpha = intSetting("Fill Alpha", 170, 0, 255, 1);
    public final BoolSetting smokeGlow = boolSetting("Smoke Glow", true);

    public final ColorSetting outlineColor = colorSetting("Outline", new Color(255, 255, 255, 136));
    public final ColorSetting smokeOutlineColor1 = colorSetting("Smoke Outline", new Color(255, 0, 0, 136), () -> mode.is(ShaderHolder.Shader.Smoke) || handsMode.is(ShaderHolder.Shader.Smoke) || chestMode.is(ShaderHolder.Shader.Smoke));
    public final ColorSetting smokeOutlineColor2 = colorSetting("Smoke Outline 2", new Color(255, 0, 0, 136), () -> mode.is(ShaderHolder.Shader.Smoke) || handsMode.is(ShaderHolder.Shader.Smoke) || chestMode.is(ShaderHolder.Shader.Smoke));
    public final ColorSetting fillColor1 = colorSetting("Fill", new Color(255, 255, 255, 136));
    public final ColorSetting fillColor2 = colorSetting("Smoke Fill", new Color(255, 255, 255, 136));
    public final ColorSetting fillColor3 = colorSetting("Smoke Fill 2", new Color(255, 255, 255, 136));

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

}
