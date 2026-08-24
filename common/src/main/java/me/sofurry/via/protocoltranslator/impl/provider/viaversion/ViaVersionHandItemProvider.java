package me.sofurry.via.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.HandItemProvider;
import me.sofurry.via.protocoltranslator.translator.ItemTranslator;
import net.minecraft.world.item.ItemStack;

public class ViaVersionHandItemProvider extends HandItemProvider {

    public static ItemStack lastUsedItem = null;

    @Override
    public Item getHandItem(UserConnection info) {
        if (lastUsedItem != null && !lastUsedItem.isEmpty()) {
            return ItemTranslator.mcToVia(lastUsedItem, ProtocolVersion.v1_8);
        } else {
            return null;
        }
    }

}
