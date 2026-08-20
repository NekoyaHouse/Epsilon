package me.sofurry.via.visuals.injection.mixin.screen_changes;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class MixinScreen {

    @Final
    @Shadow
    protected Minecraft minecraft;

    @Inject(method = "addRenderableWidget", at = @At("HEAD"), cancellable = true)
    private <T extends GuiEventListener & Renderable & NarratableEntry> void removeRecipeBook(T widget, CallbackInfoReturnable<T> cir) {
        if (widget instanceof ImageButton button && button.sprites == RecipeBookComponent.RECIPE_BUTTON_SPRITES) {
            final boolean furnace = ((Screen) (Object) this) instanceof FurnaceScreen;

            if (ClientSetting.INSTANCE.hideFurnaceRecipeBook.isEnabled() && furnace) {
                cir.setReturnValue(widget);
            } else if (ClientSetting.INSTANCE.hideCraftingRecipeBook.isEnabled() && !furnace) {
                cir.setReturnValue(widget);
            }
        }
    }

}
