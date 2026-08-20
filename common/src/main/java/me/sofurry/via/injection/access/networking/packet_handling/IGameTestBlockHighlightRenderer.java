package me.sofurry.via.injection.access.networking.packet_handling;

import net.minecraft.core.BlockPos;

public interface IGameTestBlockHighlightRenderer {

    void viaVersion$addMarker(BlockPos pos, int color, String message, int duration);

}
