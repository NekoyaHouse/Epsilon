package me.sofurry.via.injection.mixin.features.movement.sprinting_and_sneaking;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.authlib.GameProfile;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.Protocol1_21_5To1_21_6;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {

    @Shadow
    public ClientInput input;

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    protected int sprintTriggerTime;

    @Shadow
    private boolean crouching;

    @Unique
    private boolean viaVersion$lastSneaking;

    public MixinLocalPlayer(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Shadow
    private static Vec2 modifyInputSpeedForSquareMovement(final Vec2 vec) {
        return null;
    }

    @Shadow
    protected abstract boolean shouldStopRunSprinting();

    @Shadow
    public abstract boolean isMovingSlowly();

    @Shadow
    protected abstract boolean vehicleCanSprint(final Entity vehicle);

    @Shadow
    public abstract void applyInput();

    @Shadow
    protected abstract boolean canStartSprinting();

    @Shadow
    protected abstract Vec2 modifyInput(final Vec2 input);

    @Shadow
    public abstract boolean isShiftKeyDown();

    @Shadow
    public abstract boolean isUnderWater();

    @Shadow
    public abstract boolean isUsingItem();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initLastSneaking(Minecraft minecraft, ClientLevel level, ClientPacketListener connection, StatsCounter stats, ClientRecipeBook recipeBook, Input lastSentInput, boolean wasSprinting, ChatAbilities chatAbilities, CallbackInfo ci) {
        viaVersion$lastSneaking = lastSentInput.shift();
    }

    @Inject(method = "isSprintingPossible", at = @At("HEAD"), cancellable = true)
    private void isSprintingPossible1_21_10(boolean allowedInShallowWater, CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_9)) {
            cir.setReturnValue(!this.isMobilityRestricted() && this.viaVersion$hasEnoughFoodToSprint1_19_1()
                    && (!this.isPassenger() || this.vehicleCanSprint(this.getVehicle())) && (allowedInShallowWater || !this.isInShallowWater()));
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.AFTER))
    private void sendSneakingPacket(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().betweenInclusive(ProtocolVersion.v1_21_2, ProtocolVersion.v1_21_5)) {
            this.viaVersion$sendSneakingPacket();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0))
    private void skipVVProtocol(ClientPacketListener instance, Packet<?> packet) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5) && packet instanceof ServerboundPlayerInputPacket(
                Input i
        )) {
            // Directly send the player input packet to bypass the code in the 1.21.5->1.21.6 protocol.
            // This allows mods to directly send raw packets which will then be remapped by VV instead of us.
            this.viaVersion$sendInputPacket(i);
        } else {
            instance.send(packet);
        }
    }

    @Redirect(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;modifyInput(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/world/phys/Vec2;"))
    private Vec2 moveMovementSpeedFactors(LocalPlayer instance, Vec2 input) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return input;
        } else {
            return this.modifyInput(input);
        }
    }

    @Redirect(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;modifyInputSpeedForSquareMovement(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/world/phys/Vec2;"))
    private Vec2 moveMovementSpeedFactors(Vec2 vec) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return vec;
        } else {
            return modifyInputSpeedForSquareMovement(vec);
        }
    }

    @Redirect(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec2;scale(F)Lnet/minecraft/world/phys/Vec2;", ordinal = 0))
    private Vec2 moveMovementSpeedFactors(Vec2 instance, float s) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return instance;
        } else {
            return instance.scale(s);
        }
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/Tutorial;onInput(Lnet/minecraft/client/player/ClientInput;)V", shift = At.Shift.AFTER))
    private void moveMovementSpeedFactors(CallbackInfo ci) {
        //... and also add this hotfix back
        if (ProtocolTranslator.getTargetVersion().equals(ProtocolVersion.v1_21_4) && this.shouldStopRunSprinting()) {
            this.setSprinting(false);
        }
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            this.input.moveVector = this.modifyInput(this.input.moveVector);
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;backward()Z"))
    private boolean dontResetDoubleTapTicks(Input instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_4) && instance.backward();
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;shouldStopRunSprinting()Z"))
    private boolean changeStopSprintingConditions(LocalPlayer instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return this.viaVersion$shouldCancelSprinting() || this.horizontalCollision && !this.minorHorizontalCollision || !this.viaVersion$canWaterSprint();
        } else {
            return this.shouldStopRunSprinting();
        }
    }

    @Inject(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendIsSprintingIfNeeded()V", shift = At.Shift.AFTER))
    private void sendSneakingAfterSprinting(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            this.viaVersion$sendSneakingPacket();
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void storeSprintingSneakingState(CallbackInfo ci, @Share("sneakSprint") LocalBooleanRef ref) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            ref.set(!this.input.keyPresses.shift() && !this.viaVersion$isWalking1_21_4());
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;canStartSprinting()Z"))
    private boolean changeCanStartSprintingConditions(LocalPlayer instance, @Share("sneakSprint") LocalBooleanRef ref) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            final boolean canStartSprinting = this.canStartSprinting();
            final boolean onGround = this.isPassenger() ? this.getVehicle().onGround() : this.onGround();
            if ((onGround || this.isUnderWater()) && ref.get() && canStartSprinting) {
                if (this.sprintTriggerTime <= 0 && !this.minecraft.options.keySprint.isDown()) {
                    this.sprintTriggerTime = this.minecraft.options.sprintWindow().get();
                } else {
                    this.setSprinting(true);
                }
            }

            if ((this.viaVersion$canWaterSprint()) && canStartSprinting && this.minecraft.options.keySprint.isDown()) {
                this.setSprinting(true);
            }
            return false;
        } else {
            return this.canStartSprinting();
        }
    }

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void changeCanStartSprintingConditions(CallbackInfoReturnable<Boolean> cir) {
        final ProtocolVersion version = ProtocolTranslator.getTargetVersion();
        if (version.olderThanOrEqualTo(ProtocolVersion.v1_21_7)) {
            cir.setReturnValue(!this.isSprinting()
                    && (version.olderThanOrEqualTo(ProtocolVersion.v1_21_4) ? this.viaVersion$isWalking1_21_4() : this.input.hasForwardImpulse())
                    && this.viaVersion$hasEnoughFoodToSprint1_19_1()
                    && !this.isUsingItem()
                    && !this.isMobilityRestricted()
                    && (!(version.newerThan(ProtocolVersion.v1_19_3) && this.isPassenger()) || this.vehicleCanSprint(this.getVehicle()))
                    && (!(version.newerThan(ProtocolVersion.v1_19_3) && this.isFallFlying()) || this.isUnderWater())
                    && (!(this.isMovingSlowly() && version.equals(ProtocolVersion.v1_21_4)) || (this.isUnderWater() && version.equals(ProtocolVersion.v1_21_4)))
                    && (!version.olderThanOrEqualTo(ProtocolVersion.v1_21_4) && (!this.isInWater() || this.isUnderWater()) || version.olderThanOrEqualTo(ProtocolVersion.v1_21_4)));
        }
    }

    @Inject(method = "shouldStopSwimSprinting", at = @At("HEAD"), cancellable = true)
    private void changeStopSwimSprintingConditions(CallbackInfoReturnable<Boolean> cir) {
        // Not needed, but for consistency and in case a mod uses this method
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            cir.setReturnValue(!this.onGround() && !this.input.keyPresses.shift() && this.viaVersion$shouldCancelSprinting() || !this.isInWater());
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_7)) {
            cir.setReturnValue(this.isMobilityRestricted() || this.isPassenger() && !this.vehicleCanSprint(this.getVehicle())
                    || !this.isInWater() || !this.input.hasForwardImpulse() && !this.onGround() && !this.input.keyPresses.shift() || !this.viaVersion$hasEnoughFoodToSprint1_19_1());
        }
    }

    @Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
    private void changeStopSprintingConditions(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            final boolean ridingCamel = getVehicle() != null && getVehicle().getType() == EntityTypes.CAMEL;
            cir.setReturnValue(this.isFallFlying() || this.isMobilityRestricted() || this.isMovingSlowly() || this.isPassenger() && !ridingCamel || this.isUsingItem() && !this.isPassenger() && !this.isUnderWater());
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_7)) {
            cir.setReturnValue(this.isMobilityRestricted() || this.isPassenger() && !this.vehicleCanSprint(this.getVehicle()) || !this.input.hasForwardImpulse() || !this.viaVersion$hasEnoughFoodToSprint1_19_1() || this.horizontalCollision && !this.minorHorizontalCollision || this.isInWater() && !this.isUnderWater());
        }
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendIsSprintingIfNeeded()V"))
    private boolean removeSprintingPacket(LocalPlayer instance) {
        return ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_19_3);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V"))
    private void removeSneakingConditions(CallbackInfo ci) { // Allows sneaking while flying, inside blocks and vehicles
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            this.crouching = this.isShiftKeyDown() && !this.isSleeping();
        }
    }

    @Unique
    private void viaVersion$sendSneakingPacket() {
        final boolean sneaking = this.isShiftKeyDown();
        if (sneaking == this.viaVersion$lastSneaking) {
            return;
        }

        final PacketWrapper sneakingPacket = PacketWrapper.create(ServerboundPackets1_21_5.PLAYER_COMMAND, ProtocolTranslator.getPlayNetworkUserConnection());
        sneakingPacket.write(Types.VAR_INT, getId());
        sneakingPacket.write(Types.VAR_INT, sneaking ? 0 : 1);
        sneakingPacket.write(Types.VAR_INT, 0); // No data
        sneakingPacket.scheduleSendToServer(Protocol1_21_5To1_21_6.class);
        this.viaVersion$lastSneaking = sneaking;
    }

    @Unique
    private void viaVersion$sendInputPacket(final Input playerInput) {
        byte flags = 0;
        flags = (byte) (flags | (playerInput.forward() ? 0x1 : 0));
        flags = (byte) (flags | (playerInput.backward() ? 0x2 : 0));
        flags = (byte) (flags | (playerInput.left() ? 0x4 : 0));
        flags = (byte) (flags | (playerInput.right() ? 0x8 : 0));
        flags = (byte) (flags | (playerInput.jump() ? 0x10 : 0));
        flags = (byte) (flags | (playerInput.shift() ? 0x20 : 0));
        flags = (byte) (flags | (playerInput.sprint() ? 0x40 : 0));

        final PacketWrapper inputPacket = PacketWrapper.create(ServerboundPackets1_21_5.PLAYER_INPUT, ProtocolTranslator.getPlayNetworkUserConnection());
        inputPacket.write(Types.BYTE, flags);
        inputPacket.scheduleSendToServer(Protocol1_21_5To1_21_6.class);
    }

    @Unique
    private boolean viaVersion$shouldCancelSprinting() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_1)) {
            return !(this.input.moveVector.y >= 0.8F) || !this.viaVersion$hasEnoughFoodToSprint1_19_1(); // Disables sprint sneaking
        } else {
            return !this.input.hasForwardImpulse() || !this.viaVersion$hasEnoughFoodToSprint1_19_1();
        }
    }

    @Unique
    private boolean viaVersion$hasEnoughFoodToSprint1_19_1() {
        return (ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_19_1) && this.isPassenger()) || this.hasEnoughFoodToDoExhaustiveManoeuvres();
    }

    @Unique
    private boolean viaVersion$isWalking1_21_4() {
        final boolean submergedInWater = ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_14_1) && isUnderWater();
        return submergedInWater ? this.input.hasForwardImpulse() : this.input.moveVector.y >= 0.8;
    }

    @Unique
    private boolean viaVersion$canWaterSprint() {
        return ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || (!this.isInWater() || this.isUnderWater());
    }

}
