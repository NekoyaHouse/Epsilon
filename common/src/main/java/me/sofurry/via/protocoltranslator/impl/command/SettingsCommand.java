package me.sofurry.via.protocoltranslator.impl.command;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.command.ViaCommandSender;

public class SettingsCommand implements ViaVersionSubCommand {

    @Override
    public String name() {
        return "settings";
    }

    @Override
    public String description() {
        return "Opens ViaVersion's configuration/options screen";
    }

    @Override
    public boolean execute(ViaCommandSender sender, String[] args) {
        ClientSetting.INSTANCE.openViaSettingsScreen();
        return true;
    }

}
