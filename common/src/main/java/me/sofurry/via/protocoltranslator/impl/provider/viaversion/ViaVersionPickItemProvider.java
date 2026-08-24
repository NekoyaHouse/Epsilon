package me.sofurry.via.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider;
import me.sofurry.via.util.NotificationUtil;

public class ViaVersionPickItemProvider extends PickItemProvider {

    @Override
    public void pickItemFromBlock(UserConnection connection, BlockPosition blockPosition, boolean includeData) {
        NotificationUtil.warnIncompatibilityPacket("1.21.4", "PICK_ITEM_FROM_BLOCK", "Minecraft#pickBlock");
    }

    @Override
    public void pickItemFromEntity(UserConnection connection, int entityId, boolean includeData) {
        NotificationUtil.warnIncompatibilityPacket("1.21.4", "PICK_ITEM_FROM_ENTITY", "Minecraft#pickBlock");
    }

}
