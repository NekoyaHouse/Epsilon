package com.github.epsilon.neoforge.mixins.sodium

import com.github.epsilon.modules.impl.render.Xray
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Pseudo
@Mixin(targets = ["net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext"], remap = false)
open class MixinSodiumAbstractBlockRenderContext {

    @field:Shadow
    protected var state: BlockState? = null

    @Inject(method = ["isFaceCulled"], at = At("HEAD"), cancellable = true)
    private fun hookIsFaceCulled(face: Direction, cir: CallbackInfoReturnable<Boolean>) {
        if (Xray.isEnabled && Xray.wallHack) {
            cir.setReturnValue(!Xray.isCheckableOre(state!!.block))
        }
    }

}
