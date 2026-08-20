package me.sofurry.via.protocoltranslator.impl.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.viaversion.viaversion.commands.ViaCommandHandler;
import me.sofurry.via.protocoltranslator.impl.command.classic.ListExtensionsCommand;
import me.sofurry.via.protocoltranslator.impl.command.classic.SetTimeCommand;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.concurrent.CompletableFuture;

public final class ViaVersionCommandHandler extends ViaCommandHandler {

    public ViaVersionCommandHandler() {
        super(false);

        this.removeSubCommand("list");
        this.removeSubCommand("player");
        this.removeSubCommand("pps");

        this.registerSubCommand(new ListExtensionsCommand());
        this.registerSubCommand(new SetTimeCommand());
        this.registerSubCommand(new SettingsCommand());
    }

    public <S extends SharedSuggestionProvider> void register(final CommandDispatcher<S> dispatcher) {
        final RequiredArgumentBuilder<S, String> executor = RequiredArgumentBuilder.<S, String>argument("args", StringArgumentType.greedyString())
                .executes(this::execute)
                .suggests(this::suggestion);
        dispatcher.register(LiteralArgumentBuilder.<S>literal("viafabricplus").then(executor).executes(this::execute));
    }

    private <S extends SharedSuggestionProvider> int execute(final CommandContext<S> ctx) {
        String[] args = new String[0];
        try {
            args = StringArgumentType.getString(ctx, "args").split(" ");
        } catch (IllegalArgumentException ignored) {
        }
        onCommand(new ViaVersionCommandSender(ctx.getSource()), args);
        return 1;
    }

    private <S extends SharedSuggestionProvider> CompletableFuture<Suggestions> suggestion(final CommandContext<S> ctx, final SuggestionsBuilder builder) {
        String[] args;
        try {
            args = StringArgumentType.getString(ctx, "args").split(" ", -1);
        } catch (IllegalArgumentException ignored) {
            args = new String[]{""};
        }
        final String[] pref = args.clone();
        pref[pref.length - 1] = "";

        final String prefix = String.join(" ", pref);
        onTabComplete(new ViaVersionCommandSender(ctx.getSource()), args).stream().map(it -> {
            final SuggestionsBuilder b = new SuggestionsBuilder(builder.getInput(), prefix.length() + builder.getStart());
            b.suggest(it);
            return b;
        }).forEach(builder::add);
        return builder.buildFuture();
    }

}
