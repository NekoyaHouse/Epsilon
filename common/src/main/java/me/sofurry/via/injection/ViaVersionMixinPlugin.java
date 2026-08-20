package me.sofurry.via.injection;

import me.sofurry.via.features.movement.constants.LithiumWorkaround;
import me.sofurry.via.features.movement.elytra.FabricAPIWorkaround;
import me.sofurry.via.platform.ViaVersionPlatform;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class ViaVersionMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXINS_PACKAGE = "me.sofurry.via.injection.mixin.";

    public static boolean IPNEXT_PRESENT;
    public static boolean MORE_CULLING_PRESENT;
    public static boolean LITHIUM_PRESENT;
    public static boolean MOONRISE_PRESENT;
    public static boolean LEGENDARYTOOLTIPS_PRESENT;
    public static boolean LEGACY_PRESENT;

    @Override
    public void onLoad(String mixinPackage) {
        IPNEXT_PRESENT = ViaVersionPlatform.isModLoaded("inventoryprofilesnext");
        MORE_CULLING_PRESENT = ViaVersionPlatform.isModLoaded("moreculling");
        LITHIUM_PRESENT = ViaVersionPlatform.isModLoaded("lithium");
        MOONRISE_PRESENT = ViaVersionPlatform.isModLoaded("moonrise");
        LEGENDARYTOOLTIPS_PRESENT = ViaVersionPlatform.isModLoaded("legendarytooltips");
        LEGACY_PRESENT = ViaVersionPlatform.isModLoaded("legacy");

        if (ViaVersionPlatform.get().isFabric()) {
            FabricAPIWorkaround.init();
        }
        LithiumWorkaround.init();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return switch (mixinClassName) {
            case MIXINS_PACKAGE + "compat.ipnext.MixinAutoRefillHandler_ItemSlotMonitor" -> IPNEXT_PRESENT;
            case MIXINS_PACKAGE + "compat.lithium.MixinEntity" -> LITHIUM_PRESENT && !MOONRISE_PRESENT;
            case MIXINS_PACKAGE + "features.item.attack_damage.MixinItemStack" -> !LEGENDARYTOOLTIPS_PRESENT;
            case MIXINS_PACKAGE + "features.item.negative_item_count.MixinGuiGraphics" -> !LEGACY_PRESENT;
            default -> true;
        };
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

}
