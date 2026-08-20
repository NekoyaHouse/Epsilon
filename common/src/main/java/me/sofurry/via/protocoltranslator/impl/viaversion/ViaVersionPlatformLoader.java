package me.sofurry.via.protocoltranslator.impl.viaversion;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.minecraft.signature.SignableCommandArgumentsProvider;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.platform.providers.ViaProviders;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.protocols.v1_12_2to1_13.provider.PlayerLookTargetProvider;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.provider.PlayerAbilitiesProvider;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.provider.AckSequenceProvider;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.CompressionProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.HandItemProvider;
import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.api.events.LoadingCycleCallback;
import me.sofurry.via.protocoltranslator.impl.provider.viabedrock.ViaVersionNettyPipelineProvider;
import me.sofurry.via.protocoltranslator.impl.provider.vialegacy.*;
import me.sofurry.via.protocoltranslator.impl.provider.viaversion.*;
import net.raphimc.viabedrock.protocol.provider.NettyPipelineProvider;
import net.raphimc.vialegacy.protocol.alpha.a1_2_3_5_1_2_6tob1_0_1_1_1.provider.AlphaInventoryProvider;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.provider.ClassicWorldHeightProvider;
import net.raphimc.vialegacy.protocol.release.r1_2_4_5tor1_3_1_2.provider.OldAuthProvider;
import net.raphimc.vialegacy.protocol.release.r1_6_4tor1_7_2_5.provider.EncryptionProvider;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.provider.GameProfileFetcher;

public final class ViaVersionPlatformLoader implements ViaPlatformLoader {

    @Override
    public void load() {
        final ViaProviders providers = Via.getManager().getProviders();

        providers.use(VersionProvider.class, new ViaVersionBaseVersionProvider());
        providers.use(HandItemProvider.class, new ViaVersionHandItemProvider());
        providers.use(PlayerLookTargetProvider.class, new ViaVersionPlayerLookTargetProvider());
        providers.use(PlayerAbilitiesProvider.class, new ViaVersionPlayerAbilitiesProvider());
        providers.use(SignableCommandArgumentsProvider.class, new ViaVersionCommandArgumentsProvider());
        providers.use(AckSequenceProvider.class, new ViaVersionAckSequenceProvider());
        providers.use(PickItemProvider.class, new ViaVersionPickItemProvider());
        providers.use(CompressionProvider.class, new ViaVersionCompressionProvider());

        providers.use(OldAuthProvider.class, new ViaVersionOldAuthProvider());
        providers.use(ClassicWorldHeightProvider.class, new ViaVersionClassicWorldHeightProvider());
        providers.use(EncryptionProvider.class, new ViaVersionEncryptionProvider());
        providers.use(GameProfileFetcher.class, new ViaVersionGameProfileFetcher());
        if (ClientSetting.INSTANCE.emulateInventoryActionsInAlphaVersions.getValue()) {
            providers.use(AlphaInventoryProvider.class, new ViaVersionAlphaInventoryProvider());
        }

        providers.use(NettyPipelineProvider.class, new ViaVersionNettyPipelineProvider());

        ViaVersionImpl.LOADING_CYCLE.invoker().onLoadCycle(LoadingCycleCallback.LoadingCycle.POST_VIAVERSION_LOAD);
    }

    @Override
    public void unload() {
    }

}
