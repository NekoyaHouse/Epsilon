package com.github.epsilon.events.impl;
import net.minecraft.world.item.ItemStack;
import java.util.List;
/** Posted on the client thread AFTER vanilla applies an authoritative server update. */
public record ContainerContentEvent(int containerId, int stateId, boolean full, int slot,
                                    List<ItemStack> items, ItemStack carried) {}
