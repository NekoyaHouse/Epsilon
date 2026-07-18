package com.github.epsilon.utils.player;

import com.github.epsilon.managers.Managers;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.github.epsilon.Constants.mc;

public class InteractionUtils {

    public static InteractionResult airPlace(BlockPos pos, boolean rotate, InteractionHand hand, boolean packet, boolean grimBypass) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
            return InteractionResult.FAIL;
        }

        boolean bypass = hand == InteractionHand.MAIN_HAND && grimBypass;
        InteractionHand placeHand = hand;
        if (bypass) {
            swapWithOffhand();
            placeHand = InteractionHand.OFF_HAND;
        }

        Direction side = RotationUtils.getDirection(pos);
        Vec3 directionVec = Vec3.atCenterOf(pos).relative(side, 0.5);

        if (rotate) {
            Managers.ROTATION.setRotations(RotationUtils.calculate(directionVec), 180, Priority.High);
        }

        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        InteractionResult interactionResult;
        if (packet) {
            try (BlockStatePredictionHandler prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
                mc.getConnection().send(new ServerboundUseItemOnPacket(placeHand, result, prediction.currentSequence()));
            }
            interactionResult = InteractionResult.SUCCESS;
        } else {
            interactionResult = mc.gameMode.useItemOn(mc.player, placeHand, result);
        }

        if (bypass) {
            swapWithOffhand();
        }

        return interactionResult;
    }

    private static void swapWithOffhand() {
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN
        ));
    }
}
