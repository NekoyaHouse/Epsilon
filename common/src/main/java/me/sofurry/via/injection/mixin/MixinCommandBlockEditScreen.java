package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandBlockEditScreen.class)
public abstract class MixinCommandBlockEditScreen {

    @Shadow
    private CycleButton<CommandBlockEntity.Mode> modeButton;

    @Shadow
    private CycleButton<Boolean> conditionalButton;

    @Shadow
    private CycleButton<Boolean> autoexecButton;

    @Shadow
    public abstract void updateGui();

    @Inject(method = "init", at = @At("TAIL"))
    private void removeWidgets(CallbackInfo ci) {
        if (ClientSetting.INSTANCE.hideModernCommandBlockScreenFeatures.isEnabled()) {
            modeButton.visible = false;
            conditionalButton.visible = false;
            autoexecButton.visible = false;

            updateGui();
        }
    }

}
