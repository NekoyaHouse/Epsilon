package me.sofurry.via.protocoltranslator.impl.command.classic;

import com.viaversion.viaversion.api.command.ViaCommandSender;
import me.sofurry.via.protocoltranslator.impl.command.ViaVersionSubCommand;
import net.minecraft.ChatFormatting;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.alpha.a1_0_16_2toa1_0_17_1_0_17_4.storage.TimeLockStorage;

public final class SetTimeCommand implements ViaVersionSubCommand {

    @Override
    public String name() {
        return "settime";
    }

    @Override
    public String description() {
        return "Changes the time (Only for <= " + LegacyProtocolVersion.a1_0_16toa1_0_16_2.getName() + ")";
    }

    @Override
    public String usage() {
        return name() + " " + "<Time (Long)>";
    }

    @Override
    public boolean execute(ViaCommandSender sender, String[] args) {
        if (getUser() == null || !getUser().has(TimeLockStorage.class)) {
            sendMessage(sender, ChatFormatting.RED + "Only for <= " + LegacyProtocolVersion.a1_0_16toa1_0_16_2.getName());
            return true;
        }
        try {
            if (args.length == 1) {
                final long time = Long.parseLong(args[0]) % 24_000L;
                getUser().get(TimeLockStorage.class).setTime(time);
                sendMessage(sender, ChatFormatting.GREEN + "Time has been set to " + ChatFormatting.GOLD + time);
            } else {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return true;
    }

}
