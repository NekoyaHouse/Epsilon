package me.sofurry.via.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viaversion.api.minecraft.signature.SignableCommandArgumentsProvider;
import com.viaversion.viaversion.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.SignableCommand;

import java.util.Collections;
import java.util.List;

public class ViaVersionCommandArgumentsProvider extends SignableCommandArgumentsProvider {

    @Override
    public List<Pair<String, String>> getSignableArguments(String command) {
        final ClientPacketListener network = Minecraft.getInstance().getConnection();

        if (network != null) {
            return SignableCommand.of(
                            network.getCommands().parse(command, network.getSuggestionsProvider())).
                    arguments().stream().
                    map(function -> new Pair<>(function.name(), function.value())).
                    toList();
        } else {
            return Collections.emptyList();
        }
    }

}
