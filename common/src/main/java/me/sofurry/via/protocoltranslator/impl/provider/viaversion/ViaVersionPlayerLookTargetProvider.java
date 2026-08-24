package me.sofurry.via.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.protocols.v1_12_2to1_13.provider.PlayerLookTargetProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

public class ViaVersionPlayerLookTargetProvider extends PlayerLookTargetProvider {

    @Override
    public BlockPosition getPlayerLookTarget(UserConnection info) {
        if (Minecraft.getInstance().hitResult instanceof BlockHitResult blockHitResult) {
            final BlockPos pos = blockHitResult.getBlockPos();
            return new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        } else {
            return null;
        }
    }

}
