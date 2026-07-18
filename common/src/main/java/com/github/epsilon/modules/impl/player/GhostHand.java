package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.StartUseItemEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.utils.rotation.RotationUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class GhostHand extends Module {

    public static final GhostHand INSTANCE = new GhostHand();

    private GhostHand() {
        super("Ghost Hand", Category.PLAYER);
    }

    private final Set<BlockPos> posList = new ObjectOpenHashSet<>();

    @EventHandler
    private void onStartUseItem(StartUseItemEvent event) {
        if (!mc.options.keyUse.isDown()) return;

        HitResult normalHit = mc.player.pick(mc.player.blockInteractionRange(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), false);
        if (mc.level.getBlockState(BlockPos.containing(normalHit.getLocation())).hasBlockEntity()) {
            return;
        }

        Vec3 direction = new Vec3(0, 0, 0.1)
                .xRot(-(float) Math.toRadians(mc.player.getXRot()))
                .yRot(-(float) Math.toRadians(mc.player.getYRot()));

        posList.clear();

        for (int i = 1; i < mc.player.blockInteractionRange() * 10; i++) {
            BlockPos pos = BlockPos.containing(mc.player.getEyePosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)).add(direction.scale(i)));

            if (posList.contains(pos)) continue;
            posList.add(pos);

            if (mc.level.getBlockState(pos).hasBlockEntity()) {
                for (InteractionHand hand : InteractionHand.values()) {
                    InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(pos.getCenter(), RotationUtils.getDirection(pos), pos, true));
                    if (result instanceof InteractionResult.Success || result instanceof InteractionResult.Fail) {
                        mc.player.swing(hand);
                        event.cancel();
                        return;
                    }
                }
            }
        }
    }

}
