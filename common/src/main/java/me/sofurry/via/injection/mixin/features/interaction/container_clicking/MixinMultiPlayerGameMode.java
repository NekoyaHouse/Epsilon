package me.sofurry.via.injection.mixin.features.interaction.container_clicking;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ServerboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.Protocol1_16_4To1_17;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.sofurry.via.injection.access.interaction.container_clicking.IAbstractContainerMenu;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.protocoltranslator.translator.ItemTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@SuppressWarnings("DataFlowIssue")
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private ItemStack viaVersion$oldCursorStack;

    @Unique
    private List<ItemStack> viaVersion$oldItems;

    @ModifyVariable(method = "handleContainerInput", at = @At(value = "STORE"), name = "itemsBeforeClick")
    private List<ItemStack> captureOldItems(List<ItemStack> itemsBeforeClick) {
        viaVersion$oldCursorStack = minecraft.player.containerMenu.getCarried().copy();
        return this.viaVersion$oldItems = itemsBeforeClick;
    }

    @WrapWithCondition(method = "handleContainerInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean handleWindowClick(ClientPacketListener instance, Packet<?> packet) {
        final ServerboundContainerClickPacket clickSlotPacket = (ServerboundContainerClickPacket) packet;

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_4)) {
            // Contains item before modification and not the actual item
            viaVersion$clickSlot1_16_5(clickSlotPacket);
            return false;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            // Contains the actual item and not only the item hash
            viaVersion$clickSlot1_21_4(clickSlotPacket);
            return false;
        }

        return true;
    }

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void removeClickActions(int containerId, int slotNum, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_5tob1_5_2) && !containerInput.equals(ContainerInput.PICKUP)) {
            ci.cancel();
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_4_6tor1_4_7) && !containerInput.equals(ContainerInput.PICKUP) && !containerInput.equals(ContainerInput.QUICK_MOVE) && !containerInput.equals(ContainerInput.SWAP) && !containerInput.equals(ContainerInput.CLONE)) {
            ci.cancel();
        }
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2) && containerInput == ContainerInput.SWAP && buttonNum == 40) { // Pressing 'F' in inventory
            ci.cancel();
        }
    }

    @Unique
    private void viaVersion$clickSlot1_21_4(final ServerboundContainerClickPacket packet) {
        final PacketWrapper containerClick = PacketWrapper.create(ServerboundPackets1_21_4.CONTAINER_CLICK, ProtocolTranslator.getPlayNetworkUserConnection());
        containerClick.write(Types.VAR_INT, packet.containerId());
        containerClick.write(Types.VAR_INT, packet.stateId());
        containerClick.write(Types.SHORT, packet.slotNum());
        containerClick.write(Types.BYTE, packet.buttonNum());
        containerClick.write(Types.VAR_INT, packet.containerInput().id());

        final Int2ObjectMap<HashedStack> modifiedStacks = packet.changedSlots();
        containerClick.write(Types.VAR_INT, modifiedStacks.size());
        for (Int2ObjectMap.Entry<HashedStack> entry : modifiedStacks.int2ObjectEntrySet()) {
            final ItemStack itemStack = minecraft.player.containerMenu.slots.get(entry.getIntKey()).getItem();
            containerClick.write(Types.SHORT, (short) entry.getIntKey());
            containerClick.write(VersionedTypes.V1_21_4.item, ItemTranslator.mcToVia(itemStack, ProtocolVersion.v1_21_4));
        }

        final ItemStack cursorStack = minecraft.player.containerMenu.getCarried();
        containerClick.write(VersionedTypes.V1_21_4.item, ItemTranslator.mcToVia(cursorStack, ProtocolVersion.v1_21_4));
        containerClick.scheduleSendToServer(Protocol1_21_4To1_21_5.class);
    }

    @Unique
    private void viaVersion$clickSlot1_16_5(final ServerboundContainerClickPacket packet) {
        ItemStack slotItemBeforeModification;
        if (this.viaVersion$shouldBeEmpty(packet.containerInput(), packet.slotNum())) {
            slotItemBeforeModification = ItemStack.EMPTY;
        } else if (packet.slotNum() < 0 || packet.slotNum() >= viaVersion$oldItems.size()) {
            slotItemBeforeModification = viaVersion$oldCursorStack;
        } else {
            slotItemBeforeModification = viaVersion$oldItems.get(packet.slotNum());
        }

        final PacketWrapper containerClick = PacketWrapper.create(ServerboundPackets1_16_2.CONTAINER_CLICK, ProtocolTranslator.getPlayNetworkUserConnection());
        containerClick.write(Types.BYTE, (byte) packet.containerId());
        containerClick.write(Types.SHORT, packet.slotNum());
        containerClick.write(Types.BYTE, packet.buttonNum());
        containerClick.write(Types.SHORT, ((IAbstractContainerMenu) minecraft.player.containerMenu).viaVersion$incrementAndGetActionId());
        containerClick.write(Types.VAR_INT, packet.containerInput().ordinal());
        containerClick.write(Types.ITEM1_13_2, ItemTranslator.mcToVia(slotItemBeforeModification, ProtocolVersion.v1_16_4));
        containerClick.scheduleSendToServer(Protocol1_16_4To1_17.class);

        viaVersion$oldCursorStack = null;
        viaVersion$oldItems = null;
    }

    @Unique
    private boolean viaVersion$shouldBeEmpty(final ContainerInput type, final int slot) {
        // quick craft always uses empty stack for verification
        if (type == ContainerInput.QUICK_CRAFT) return true;

        // Special case: throw always uses empty stack for verification
        if (type == ContainerInput.THROW) return true;

        // quick move always uses empty stack for verification since 1.12
        if (type == ContainerInput.QUICK_MOVE && ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_11_1))
            return true;

        // pickup with slot -999 (outside window) to throw items always uses empty stack for verification
        return type == ContainerInput.PICKUP && slot == -999;
    }

}
