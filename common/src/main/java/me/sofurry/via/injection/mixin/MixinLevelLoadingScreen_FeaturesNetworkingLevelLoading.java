package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.networking.downloading_terrain.ILevelLoadingScreen;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen_FeaturesNetworkingLevelLoading extends Screen implements ILevelLoadingScreen {

    @Unique
    private long viaVersion$loadStartTime;

    @Unique
    private int viaVersion$tickCounter;

    @Unique
    private boolean viaVersion$ready;

    @Unique
    private boolean viaVersion$closeOnNextTick;

    public MixinLevelLoadingScreen_FeaturesNetworkingLevelLoading(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void viaVersion$initFields(final CallbackInfo ci) {
        this.viaVersion$loadStartTime = Util.getMillis();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void modifyCloseCondition(CallbackInfo ci) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().isLocalServer()) {
            // When joining the singleplayer, we set the target version to the native version when the integrated server is started,
            // However, this is already too late, and the screen was already opened (and ticked), causing NPEs due to the network handler being null
            return;
        }

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            ci.cancel();

            if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18)) {
                if (this.viaVersion$ready) {
                    this.onClose();
                }

                if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_1)) {
                    this.viaVersion$tickCounter++;
                    if (this.viaVersion$tickCounter % 20 == 0) {
                        this.minecraft.getConnection().send(new ServerboundKeepAlivePacket(0));
                    }
                }
            } else {
                if (System.currentTimeMillis() > this.viaVersion$loadStartTime + 30000L) {
                    this.onClose();
                } else {
                    if (this.viaVersion$closeOnNextTick) {
                        if (this.minecraft.player == null) return;

                        final BlockPos blockPos = this.minecraft.player.blockPosition();
                        final boolean isOutOfHeightLimit = this.minecraft.level != null && this.minecraft.level.isOutsideBuildHeight(blockPos.getY());
                        if (isOutOfHeightLimit || this.minecraft.levelRenderer.isSectionCompiledAndVisible(blockPos) || this.minecraft.player.isSpectator() || !this.minecraft.player.isAlive()) {
                            this.onClose();
                        }
                    } else {
                        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_1)) {
                            this.viaVersion$closeOnNextTick = this.viaVersion$ready || System.currentTimeMillis() > this.viaVersion$loadStartTime + 2000;
                        } else {
                            this.viaVersion$closeOnNextTick = this.viaVersion$ready;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void viaVersion$setReady() {
        this.viaVersion$ready = true;
    }

}
