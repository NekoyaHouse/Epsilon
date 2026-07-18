package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

public class SafeCrystal extends Module {

    public static final SafeCrystal INSTANCE = new SafeCrystal();

    private SafeCrystal() {
        super("Safe Crystal", Category.COMBAT);
    }

    private final BoolSetting autoBreak = boolSetting("Auto Break", true);
    private final BoolSetting autoPlace = boolSetting("Auto Place", true);
    private final DoubleSetting antiSuicide = doubleSetting("Anti Suicide", 2.0, 1.0, 20.0, 0.5);

    private int attackCooldown = 0;
    private int placeCooldown = 0;

    private static final double MAX_ATTACK_DISTANCE = 3.6;

    @Override
    protected void onEnable() {
        attackCooldown = 0;
        placeCooldown = 0;
    }

    @Override
    protected void onDisable() {
        attackCooldown = 0;
        placeCooldown = 0;
    }

    @Override
    public String getInfo() {
        if (autoBreak.getValue() && autoPlace.getValue()) return "Break+Place";
        if (autoBreak.getValue()) return "Break";
        if (autoPlace.getValue()) return "Place";
        return "None";
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (autoBreak.getValue()) {
            handleAutoCrystalBreak();
        }

        if (autoPlace.getValue()) {
            handleAutoCrystalPlace();
        }
    }

    private void handleAutoCrystalBreak() {
        if (mc.player.getHealth() < antiSuicide.getValue().floatValue()) return;
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof EntityHitResult ehr)) return;

        if (!(ehr.getEntity() instanceof EndCrystal crystal)) return;

        if (mc.player.distanceTo(crystal) > MAX_ATTACK_DISTANCE) return;

        boolean playerHigherOrEqual = mc.player.getY() >= crystal.getY();

        if (playerHigherOrEqual) {
            Vec3 start = mc.player.getEyePosition();
            Vec3 end = crystal.position();

            BlockHitResult blockHit = mc.level.clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));

            if (blockHit.getType() == HitResult.Type.MISS) {
                return;
            }
        }

        mc.gameMode.attack(mc.player, crystal);
        mc.player.swing(InteractionHand.MAIN_HAND);

        attackCooldown = 1 + (int) (Math.random() * 2);
    }

    private void handleAutoCrystalPlace() {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos base = bhr.getBlockPos();

        if (!mc.level.getBlockState(base).is(Blocks.OBSIDIAN)
                && !mc.level.getBlockState(base).is(Blocks.BEDROCK)) {
            return;
        }

        BlockPos above = base.above();

        if (!mc.level.getBlockState(above).isAir()) return;

        AABB box = new AABB(
                above.getX(), above.getY(), above.getZ(),
                above.getX() + 1, above.getY() + 2, above.getZ() + 1
        );

        if (!mc.level.getEntities(null, box).isEmpty()) {
            return;
        }

        InteractionHand crystalHand = getCrystalHand();
        if (crystalHand == null) return;

        mc.gameMode.useItemOn(
                mc.player,
                crystalHand,
                bhr
        );

        mc.player.swing(crystalHand);

        placeCooldown = 1 + (int) (Math.random() * 2);
    }

    private InteractionHand getCrystalHand() {
        if (mc.player.getMainHandItem().is(Items.END_CRYSTAL))
            return InteractionHand.MAIN_HAND;

        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL))
            return InteractionHand.OFF_HAND;

        return null;
    }

}
