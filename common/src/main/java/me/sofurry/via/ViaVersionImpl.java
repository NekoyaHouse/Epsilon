package me.sofurry.via;

import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;
import me.sofurry.via.api.ViaVersionBase;
import me.sofurry.via.api.events.ChangeProtocolVersionCallback;
import me.sofurry.via.api.events.LoadingCycleCallback;
import me.sofurry.via.features.FeaturesLoading;
import me.sofurry.via.features.item.filter_creative_tabs.VersionedRegistries;
import me.sofurry.via.features.item.negative_item_count.NegativeItemUtil;
import me.sofurry.via.features.limitation.max_chat_length.MaxChatLength;
import me.sofurry.via.injection.access.core.IConnection;
import me.sofurry.via.injection.access.core.IServerData;
import me.sofurry.via.platform.ViaVersionPlatform;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.protocoltranslator.translator.ItemTranslator;
import me.sofurry.via.screen.impl.ProtocolSelectionScreen;
import me.sofurry.via.util.ClassLoaderPriorityUtil;
import me.sofurry.via.util.ViaVersionEvent;
import me.sofurry.via.util.network.SyncTasks;
import me.sofurry.via.visuals.ViaVersionVisuals;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class ViaVersionImpl implements ViaVersionBase {

    public static final ViaVersionEvent<LoadingCycleCallback> LOADING_CYCLE = new ViaVersionEvent<>(listeners -> state -> {
        for (final LoadingCycleCallback listener : listeners) {
            listener.onLoadCycle(state);
        }
    });

    public static final ViaVersionEvent<ChangeProtocolVersionCallback> CHANGE_PROTOCOL_VERSION = new ViaVersionEvent<>(listeners -> (oldVersion, newVersion) -> {
        for (final ChangeProtocolVersionCallback listener : listeners) {
            listener.onChangeProtocolVersion(oldVersion, newVersion);
        }
    });

    public static final ViaVersionImpl INSTANCE = new ViaVersionImpl();

    private final Logger logger = LogManager.getLogger("ViaVersion");
    private Path path;

    private String version;
    private String implVersion;
    private CompletableFuture<Void> loadingFuture;
    private boolean initialized;

    public synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        final ViaVersionPlatform.Provider platform = ViaVersionPlatform.get();
        ViaVersion.init(INSTANCE);

        path = platform.getConfigDirectory().resolve("viafabricplus");
        version = platform.getVersion();
        implVersion = platform.getImplVersion();

        platform.invokeLoadEntrypoints(INSTANCE);
        ViaVersionVisuals.INSTANCE.onPlatformLoad(INSTANCE);

        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            logger.error("Failed to create ViaVersion directory", e);
        }

        ClassLoaderPriorityUtil.loadOverridingJars(path, logger);
        SyncTasks.init();
        platform.registerSyncPayload();
        FeaturesLoading.init();

        this.loadingFuture = ProtocolTranslator.init(path);
        LOADING_CYCLE.register(cycle -> {
            if (cycle == LoadingCycleCallback.LoadingCycle.POST_GAME_LOAD) {
                this.loadingFuture.join();
                FeaturesLoading.postInit();
                ConfigManager.INSTANCE.onViaVersionReady();
            }
        });
        LOADING_CYCLE.invoker().onLoadCycle(LoadingCycleCallback.LoadingCycle.FINAL_LOAD);
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public String getImplVersion() {
        return this.implVersion;
    }

    @Override
    public Path getPath() {
        return this.path;
    }

    @Override
    public ProtocolVersion getTargetVersion() {
        return ProtocolTranslator.getTargetVersion();
    }

    @Override
    public void setTargetVersion(ProtocolVersion newVersion) {
        ProtocolTranslator.setTargetVersion(newVersion);
    }

    @Override
    public ProtocolVersion getTargetVersion(Channel channel) {
        return ProtocolTranslator.getTargetVersion(channel);
    }

    @Override
    public ProtocolVersion getTargetVersion(Connection connection) {
        return ((IConnection) connection).viaVersion$getTargetVersion();
    }

    @Override
    public void setTargetVersion(ProtocolVersion newVersion, boolean revertOnDisconnect) {
        ProtocolTranslator.setTargetVersion(newVersion, revertOnDisconnect);
    }

    @Override
    public UserConnection getPlayNetworkUserConnection() {
        return ProtocolTranslator.getPlayNetworkUserConnection();
    }

    @Override
    public UserConnection getUserConnection(Connection connection) {
        return ((IConnection) connection).viaVersion$getUserConnection();
    }

    @Override
    public ProtocolVersion getServerVersion(ServerData serverInfo) {
        return ((IServerData) serverInfo).viaVersion$forcedVersion();
    }

    @Override
    public void registerOnChangeProtocolVersionCallback(ChangeProtocolVersionCallback callback) {
        CHANGE_PROTOCOL_VERSION.register(callback);
    }

    @Override
    public void registerLoadingCycleCallback(LoadingCycleCallback callback) {
        LOADING_CYCLE.register(callback);
    }

    @Override
    public int getMaxChatLength(ProtocolVersion version) {
        return MaxChatLength.getChatLength();
    }

    @Override
    public void openProtocolSelectionScreen(Screen parent) {
        ProtocolSelectionScreen.INSTANCE.open(parent);
    }

    @Override
    public void openSettingsScreen(Screen parent) {
        ClientSetting.INSTANCE.openViaSettingsScreen();
    }

    @Override
    public Item translateItem(ItemStack stack, ProtocolVersion targetVersion) {
        return ItemTranslator.mcToVia(stack, targetVersion);
    }

    @Override
    public ItemStack translateItem(Item item, ProtocolVersion sourceVersion) {
        return ItemTranslator.viaToMc(item, sourceVersion);
    }

    @Override
    public boolean itemExists(net.minecraft.world.item.Item item, ProtocolVersion version) {
        return VersionedRegistries.containsItem(item, version);
    }

    @Override
    public boolean enchantmentExists(ResourceKey<Enchantment> enchantment, ProtocolVersion version) {
        return VersionedRegistries.containsEnchantment(enchantment, version);
    }

    @Override
    public boolean effectExists(Holder<MobEffect> effect, ProtocolVersion version) {
        return VersionedRegistries.containsEffect(effect, version);
    }

    @Override
    public boolean bannerPatternExists(ResourceKey<BannerPattern> pattern, ProtocolVersion version) {
        return VersionedRegistries.containsBannerPattern(pattern, version);
    }

    @Override
    public boolean itemExistsInConnection(net.minecraft.world.item.Item item) {
        return VersionedRegistries.keepItem(item);
    }

    @Override
    public boolean itemExistsInConnection(ItemStack stack) {
        return VersionedRegistries.keepItem(stack);
    }

    @Override
    public int getStackCount(ItemStack stack) {
        return NegativeItemUtil.getCount(stack);
    }

    public Logger getLogger() {
        return this.logger;
    }

}
