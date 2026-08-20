package me.sofurry.via.protocoltranslator.impl.command;

import com.viaversion.viaversion.api.command.ViaCommandSender;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class ViaVersionCommandSender implements ViaCommandSender {

    private final SharedSuggestionProvider source;

    public ViaVersionCommandSender(final SharedSuggestionProvider source) {
        this.source = source;
    }

    @Override
    public boolean hasPermission(String s) {
        return true;
    }

    @Override
    public void sendMessage(String s) {
        Minecraft.getInstance().showDebugChat(Component.nullToEmpty(s.replace("/viaversion", "/viafabricplus"))); // ViaVersion doesn't support changing the root command name, so we have to do it ourselves
    }

    @Override
    public UUID getUUID() {
        return Minecraft.getInstance().player.getUUID();
    }

    @Override
    public String getName() {
        return Minecraft.getInstance().player.getName().getString();
    }

}
