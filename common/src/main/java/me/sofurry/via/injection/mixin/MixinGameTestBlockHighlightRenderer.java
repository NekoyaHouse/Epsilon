package me.sofurry.via.injection.mixin;

import me.sofurry.via.injection.access.networking.packet_handling.IGameTestBlockHighlightRenderer;
import net.minecraft.client.renderer.debug.GameTestBlockHighlightRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(GameTestBlockHighlightRenderer.class)
public abstract class MixinGameTestBlockHighlightRenderer implements IGameTestBlockHighlightRenderer {

    @Shadow
    @Final
    private Map<BlockPos, GameTestBlockHighlightRenderer.Marker> markers;

    @Override
    public void viaVersion$addMarker(final BlockPos pos, final int color, final String message, final int duration) {
        this.markers.put(pos, new GameTestBlockHighlightRenderer.Marker(color, message, Util.getMillis() + duration));
    }

}
