package com.github.epsilon.neoforge.mixins;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class NeoForgeMixinPlugin implements IMixinConfigPlugin {

    private static final String SODIUM_MIXIN_PACKAGE = "com.github.epsilon.neoforge.mixins.sodium.";
    private static final String IRIS_MIXIN_PACKAGE = "com.github.epsilon.neoforge.mixins.iris.";

    private final boolean sodiumLoaded = isModLoaded("sodium");
    private final boolean irisLoaded = isModLoaded("iris");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(SODIUM_MIXIN_PACKAGE)) {
            return this.sodiumLoaded;
        }
        if (mixinClassName.startsWith(IRIS_MIXIN_PACKAGE)) {
            return this.irisLoaded;
        }
        return true;
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

    private static boolean isModLoaded(String modId) {
        var loader = FMLLoader.getCurrentOrNull();
        if (loader == null) {
            return false;
        }

        try {
            return loader.getLoadingModList().getModFileById(modId) != null;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

}
