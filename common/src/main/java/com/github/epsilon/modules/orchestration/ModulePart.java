package com.github.epsilon.modules.orchestration;

public interface ModulePart {
    default void declare(ModuleDeclaration declaration) {}
    default void onEnable(ModuleContext context) {}
    default void onDisable(ModuleContext context) {}
    default void reset(ModuleContext context) {}
    default void save(StateWriter writer) {}
    default void load(StateReader reader) {}
}
