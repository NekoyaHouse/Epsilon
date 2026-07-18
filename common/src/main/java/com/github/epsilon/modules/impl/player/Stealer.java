package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.ClickSlotUtils;
import com.github.epsilon.utils.player.InvHelper;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Stealer extends Module {

    public static final Stealer INSTANCE = new Stealer();

    private Stealer() {
        super("Stealer", Category.PLAYER);
    }

    private final IntSetting minDelay = intSetting("Min Delay", 50, 0, 1000, 50);
    private final IntSetting delay = intSetting("Delay", 50, 0, 1000, 50);
    private final BoolSetting closeDelay = boolSetting("Close Delay", true);
    private final IntSetting cDelay = intSetting("Close Delay Value", 150, 0, 1000, 1, closeDelay::getValue);
    private final BoolSetting pickEnderChest = boolSetting("Ender Chest", false);

    private Screen lastTickScreen;

    private static final TimerUtils timer = new TimerUtils();
    private static final Random random = new Random();

    public boolean isWorking() {
        return !timer.hasDelayed(3);
    }

    public static boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else if (InvHelper.isGodItem(stack) || InvHelper.isSharpnessAxe(stack)) {
            return true;
        } else if (InvHelper.isArmor(stack)) {
            float protection = InvHelper.getProtection(stack);
            float bestArmor = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack));
            return !(protection <= bestArmor);
        } else if (InvHelper.isSword(stack)) {
            float damage = InvHelper.getSwordDamage(stack);
            float bestDamage = InvHelper.getBestSwordDamage();
            return !(damage <= bestDamage);
        } else if (InvHelper.isPickaxe(stack)) {
            float score = InvHelper.getToolScore(stack);
            float bestScore = InvHelper.getBestPickaxeScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof AxeItem) {
            float score = InvHelper.getToolScore(stack);
            float bestScore = InvHelper.getBestAxeScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof ShovelItem) {
            float score = InvHelper.getToolScore(stack);
            float bestScore = InvHelper.getBestShovelScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof CrossbowItem) {
            float score = InvHelper.getCrossbowScore(stack);
            float bestScore = InvHelper.getBestCrossbowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof BowItem && InvHelper.isPunchBow(stack)) {
            float score = InvHelper.getPunchBowScore(stack);
            float bestScore = InvHelper.getBestPunchBowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof BowItem && InvHelper.isPowerBow(stack)) {
            float score = InvHelper.getPowerBowScore(stack);
            float bestScore = InvHelper.getBestPowerBowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() == Items.COMPASS) {
            return !InvHelper.hasItem(stack.getItem());
        } else if (stack.getItem() == Items.WATER_BUCKET && InvHelper.getItemCount(Items.WATER_BUCKET) >= InvManager.INSTANCE.waterBucketCount.getValue()) {
            return false;
        } else if (stack.getItem() == Items.LAVA_BUCKET && InvHelper.getItemCount(Items.LAVA_BUCKET) >= InvManager.INSTANCE.lavaBucketCount.getValue()) {
            return false;
        } else if (stack.getItem() instanceof BlockItem
                && InvHelper.isValidStack(stack)
                && InvHelper.getBlockCountInInventory() + stack.getCount() >= InvManager.INSTANCE.maxBlockSize.getValue()) {
            return false;
        } else if (stack.getItem() == Items.ARROW && InvHelper.getItemCount(Items.ARROW) + stack.getCount() >= InvManager.INSTANCE.maxArrowSize.getValue()) {
            return false;
        } else if (stack.getItem() instanceof FishingRodItem && InvHelper.getItemCount(Items.FISHING_ROD) >= 1) {
            return false;
        } else if (stack.getItem() != Items.SNOWBALL && stack.getItem() != Items.EGG
                || InvHelper.getItemCount(Items.SNOWBALL) + InvHelper.getItemCount(Items.EGG) + stack.getCount() < InvManager.INSTANCE.maxProjectileSize.getValue()
                && InvManager.INSTANCE.keepProjectile.getValue()
        ) {
            return !(stack.getItem() instanceof StandingAndWallBlockItem) && InvHelper.isCommonItemUseful(stack);
        } else {
            return false;
        }
    }

    private static boolean isBestItemInChest(ChestMenu menu, ItemStack stack) {
        if (!InvHelper.isGodItem(stack) && !InvHelper.isSharpnessAxe(stack)) {
            for (int i = 0; i < menu.getRowCount() * 9; i++) {
                ItemStack checkStack = menu.getSlot(i).getItem();
                if (InvHelper.isArmor(stack) && InvHelper.isArmor(checkStack)) {
                    if (InvHelper.getArmorSlot(stack) == InvHelper.getArmorSlot(checkStack)
                            && InvHelper.getProtection(checkStack) > InvHelper.getProtection(stack)) {
                        return false;
                    }
                } else if (InvHelper.isSword(stack) && InvHelper.isSword(checkStack)) {
                    if (InvHelper.getSwordDamage(checkStack) > InvHelper.getSwordDamage(stack)) {
                        return false;
                    }
                } else if (InvHelper.isPickaxe(stack) && InvHelper.isPickaxe(checkStack)) {
                    if (InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof AxeItem && checkStack.getItem() instanceof AxeItem) {
                    if (InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof ShovelItem
                        && checkStack.getItem() instanceof ShovelItem
                        && InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        Screen currentScreen = mc.screen;
        if (currentScreen instanceof AbstractContainerScreen<?> container && container.getMenu() instanceof ChestMenu menu) {
            if (currentScreen != this.lastTickScreen) {
                timer.reset();
            } else {
                String chestTitle = container.getTitle().getString();
                String chest = Component.translatable("container.chest").getString();
                String largeChest = Component.translatable("container.chestDouble").getString();
                String enderChest = Component.translatable("container.enderchest").getString();
                if (chestTitle.equals(chest)
                        || chestTitle.equals(largeChest)
                        || chestTitle.equals("Chest")
                        || this.pickEnderChest.getValue() && chestTitle.equals(enderChest)
                ) {
                    int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
                    if (this.isChestEmpty(menu) && timer.passedMillise(nextDelay)) {
                        if (mc.player != null && closeDelay.getValue() && timer.passedMillise(cDelay.getValue())) {
                            mc.player.closeContainer();
                            timer.reset();
                        }

                    } else {
                        List<Integer> slots = IntStream.range(0, menu.getRowCount() * 9).boxed().collect(Collectors.toList());
                        Collections.shuffle(slots);

                        for (Integer pSlotId : slots) {
                            ItemStack stack = menu.getSlot(pSlotId).getItem();
                            if (isItemUseful(stack) && isBestItemInChest(menu, stack) && timer.passedMillise(nextDelay)) {
                                ClickSlotUtils.shiftClick(menu.containerId, pSlotId);
                                timer.reset();
                                break;
                            }
                        }
                    }
                }
            }
        }

        this.lastTickScreen = currentScreen;
    }

    private boolean isChestEmpty(ChestMenu menu) {
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack item = menu.getSlot(i).getItem();
            if (!item.isEmpty() && isItemUseful(item) && isBestItemInChest(menu, item)) {
                return false;
            }
        }

        return true;
    }

}
