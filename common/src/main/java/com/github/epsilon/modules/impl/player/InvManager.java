package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.modules.impl.movement.NoSlowdown;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;
import com.github.epsilon.utils.player.ClickSlotUtils;
import com.github.epsilon.utils.player.InvHelper;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class InvManager extends Module {

    public static final InvManager INSTANCE = new InvManager();

    private InvManager() {
        super("Inv Manager", Category.PLAYER);
    }

    private enum Mode {
        Inventory,
        Silent
    }

    private enum OffhandItemMode {
        None,
        GoldenApple,
        Projectile,
        FishingRod,
        Block
    }

    private enum BowPriorityMode {
        Crossbow,
        PowerBow,
        PunchBow
    }

    private enum ActionState {
        IDLE,
        WAITING_FOR_SPRINT_STOP,
        READY_TO_EXECUTE,
        WAITING_FOR_INVENTORY_CLOSE
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Silent);
    private final BoolSetting pauseOnEat = boolSetting("Pause On Eat", true);
    private final BoolSetting pauseOnKillAura = boolSetting("Pause On KillAura", true);
    private final BoolSetting pauseOnScaffold = boolSetting("Pause On Scaffold", true);
    private final IntSetting minDelay = intSetting("Min Delay", 95, 0, 1000, 50);
    private final IntSetting maxDelay = intSetting("Max Delay", 135, 0, 1000, 50);
    private final EnumSetting<OffhandItemMode> offhandItem = enumSetting("Offhand Item", OffhandItemMode.None);
    private final BoolSetting autoArmor = boolSetting("Auto Armor", true);
    private final BoolSetting switchSword = boolSetting("Switch Sword", true);
    private final IntSetting swordSlot = intSetting("Sword Slot", 1, 1, 9, 1, switchSword::getValue);
    private final BoolSetting switchBlock = boolSetting("Switch Block", true, () -> !offhandItem.is(OffhandItemMode.Block));
    private final IntSetting blockSlot = intSetting("Block Slot", 2, 1, 9, 1, () -> switchBlock.getValue() && !offhandItem.is(OffhandItemMode.Block));
    public final IntSetting maxBlockSize = intSetting("Max Block Size", 256, 64, 512, 64, switchBlock::getValue);
    private final BoolSetting switchPickaxe = boolSetting("Switch Pickaxe", true);
    private final IntSetting pickaxeSlot = intSetting("Pickaxe Slot", 3, 1, 9, 1, switchPickaxe::getValue);
    private final BoolSetting switchAxe = boolSetting("Switch Axe", true);
    private final IntSetting axeSlot = intSetting("Axe Slot", 4, 1, 9, 1, switchAxe::getValue);
    private final BoolSetting switchBow = boolSetting("Switch Bow or Crossbow", true);
    private final IntSetting bowSlot = intSetting("Bow Slot", 5, 1, 9, 1, switchBow::getValue);
    private final EnumSetting<BowPriorityMode> preferBow = enumSetting("Bow Priority", BowPriorityMode.Crossbow, switchBow::getValue);
    public final IntSetting maxArrowSize = intSetting("Max Arrow Size", 256, 64, 512, 64, switchBow::getValue);
    private final BoolSetting switchWaterBucket = boolSetting("Switch Water Bucket", true);
    private final IntSetting waterBucketSlot = intSetting("Water Bucket Slot", 6, 1, 9, 1, switchWaterBucket::getValue);
    private final BoolSetting switchEnderPearl = boolSetting("Switch Ender Pearl", true);
    private final IntSetting enderPearlSlot = intSetting("Ender Pearl Slot", 7, 1, 9, 1, switchEnderPearl::getValue);
    private final BoolSetting switchFireball = boolSetting("Switch Fireball", true);
    private final IntSetting fireballSlot = intSetting("Fireball Slot", 8, 1, 9, 1, switchFireball::getValue);
    private final BoolSetting switchGoldenApple = boolSetting("Switch Golden Apple", true, () -> !offhandItem.is(OffhandItemMode.GoldenApple));
    private final IntSetting goldenAppleSlot = intSetting("Golden Apple Slot", 9, 1, 9, 1, () -> switchGoldenApple.getValue() && !offhandItem.is(OffhandItemMode.GoldenApple));
    private final BoolSetting throwItems = boolSetting("Throw Items", true);
    public final IntSetting waterBucketCount = intSetting("Keep Water Buckets", 1, 0, 5, 1, throwItems::getValue);
    public final IntSetting lavaBucketCount = intSetting("Keep Lava Buckets", 1, 0, 5, 1, throwItems::getValue);
    public final BoolSetting keepProjectile = boolSetting("Keep Eggs & Snowballs", true);
    private final BoolSetting switchProjectile = boolSetting("Switch Eggs & Snowballs", false, () -> keepProjectile.getValue() && !offhandItem.is(OffhandItemMode.Projectile));
    private final IntSetting projectileSlot = intSetting("Eggs & Snowballs Slot", 9, 1, 9, 1, () -> switchProjectile.getValue() && keepProjectile.getValue() && !offhandItem.is(OffhandItemMode.Projectile));
    public final IntSetting maxProjectileSize = intSetting("Max Eggs & Snowballs Size", 64, 16, 256, 16, keepProjectile::getValue);
    private final BoolSetting switchRod = boolSetting("Switch Rod", false, () -> !offhandItem.is(OffhandItemMode.FishingRod));
    private final IntSetting rodSlot = intSetting("Rod Slot", 9, 1, 9, 1, () -> switchRod.getValue() && !offhandItem.is(OffhandItemMode.FishingRod));

    private int noMoveTicks = 0;
    private boolean clickOffHand = false;
    private boolean inventoryOpen = false;

    private ActionState actionState = ActionState.IDLE;
    private boolean actionConsumed;
    private int delayMs;

    private final TimerUtils timer = new TimerUtils();

    @Override
    protected void onEnable() {
        clickOffHand = false;
        inventoryOpen = false;
        actionState = ActionState.IDLE;
        actionConsumed = false;
        delayMs = MathUtils.getRandom(minDelay.getValue(), maxDelay.getValue());
        timer.reset();
    }

    @Override
    protected void onDisable() {
        boolean closeSilentInventory = inventoryOpen && mode.is(Mode.Silent) && mc.getConnection() != null && mc.player != null;
        clickOffHand = false;
        inventoryOpen = false;
        actionState = ActionState.IDLE;
        actionConsumed = false;
        delayMs = 0;
        if (closeSilentInventory) {
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
        }
    }

    private boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (InvHelper.isGodItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;
        if (InvHelper.isArmor(stack)) {
            float protection = InvHelper.getProtection(stack);
            if (InvHelper.getCurrentArmorScore(InvHelper.getArmorSlot(stack)) >= protection) return false;
            float bestArmor = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack));
            return !(protection < bestArmor);
        }
        if (InvHelper.isSword(stack)) return InvHelper.getBestSword() == stack;
        if (InvHelper.isPickaxe(stack)) return InvHelper.getBestPickaxe() == stack;
        if (stack.getItem() instanceof AxeItem && !InvHelper.isSharpnessAxe(stack))
            return InvHelper.getBestAxe() == stack;
        if (stack.getItem() instanceof ShovelItem) return InvHelper.getBestShovel() == stack;
        if (stack.getItem() instanceof CrossbowItem) return InvHelper.getBestCrossbow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.isPunchBow(stack))
            return InvHelper.getBestPunchBow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.isPowerBow(stack))
            return InvHelper.getBestPowerBow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.getItemCount(Items.BOW) > 1) return false;
        if (stack.getItem() == Items.WATER_BUCKET && InvHelper.getItemCount(Items.WATER_BUCKET) > waterBucketCount.getValue())
            return false;
        if (stack.getItem() == Items.LAVA_BUCKET && InvHelper.getItemCount(Items.LAVA_BUCKET) > lavaBucketCount.getValue())
            return false;
        if (stack.getItem() instanceof FishingRodItem && InvHelper.getItemCount(Items.FISHING_ROD) > 1)
            return false;
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !keepProjectile.getValue())
            return false;
        if (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return true;
        return !(stack.getItem() instanceof StandingAndWallBlockItem) && InvHelper.isCommonItemUseful(stack);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundContainerClosePacket) {
            this.inventoryOpen = false;
            if (actionState == ActionState.WAITING_FOR_INVENTORY_CLOSE) {
                actionState = ActionState.IDLE;
            }
        }
        if (this.inventoryOpen && mode.is(Mode.Silent)) {
            if (
                    event.getPacket() instanceof ServerboundMovePlayerPacket
                            || event.getPacket() instanceof ServerboundUseItemOnPacket
                            || event.getPacket() instanceof ServerboundUseItemPacket
                            || event.getPacket() instanceof ServerboundInteractPacket
                            || event.getPacket() instanceof ServerboundAttackPacket
                            || event.getPacket() instanceof ServerboundPlayerActionPacket
            ) {
                this.inventoryOpen = false;
                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
            }
        }
    }

    @EventHandler
    private void onTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        actionConsumed = false;

        if (InvHelper.shouldDisableFeatures()) {
            cancelPendingAction();
            return;
        }
        if (NoSlowdown.INSTANCE.isWorking()) {
            cancelPendingAction();
            return;
        }

        if (mc.player.isMoving()) {
            this.noMoveTicks = 0;
        } else {
            this.noMoveTicks++;
        }

        if (pauseOnEat.getValue() && PlayerUtils.isEating()) {
            cancelPendingAction();
            return;
        }
        if (pauseOnKillAura.getValue() && KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.target != null) {
            cancelPendingAction();
            return;
        }
        if (pauseOnScaffold.getValue() && Scaffold.INSTANCE.isEnabled()) {
            cancelPendingAction();
            return;
        }

        boolean allowMove = mode.is(Mode.Silent);
        if (Stealer.INSTANCE.isWorking() || (mode.is(Mode.Inventory) ? !(mc.gui.screen() instanceof InventoryScreen) : (!allowMove && this.noMoveTicks <= 1))) {
            this.clickOffHand = false;
            cancelPendingAction();
            return;
        }
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> container && container.getMenu().containerId != mc.player.inventoryMenu.containerId) {
            cancelPendingAction();
            return;
        }

        if (this.autoArmor.getValue()) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (int i = 0; i < armorSlots.length; i++) {
                ItemStack stack = InvHelper.getArmorStack(armorSlots[i]);
                if (InvHelper.isArmor(stack)) {
                    if (!stack.isEmpty() && canExecuteInventoryAction() && InvHelper.getBestArmorScore(armorSlots[i]) > InvHelper.getProtection(stack)) {
                        dropAll(4 + (4 - i));
                    }
                }
            }
            for (int ix = 0; ix < mc.player.getInventory().getNonEquipmentItems().size(); ix++) {
                ItemStack stack = InvHelper.getInventoryStack(ix);
                if (!stack.isEmpty() && InvHelper.isArmor(stack)) {
                    float currentItemScore = InvHelper.getProtection(stack);
                    boolean isBestItem = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack)) == currentItemScore;
                    boolean isBetterItem = InvHelper.getCurrentArmorScore(InvHelper.getArmorSlot(stack)) < currentItemScore;
                    if (isBestItem && isBetterItem && canExecuteInventoryAction()) {
                        if (ix < 9) {
                            shiftClick(ix + 36);
                        } else {
                            shiftClick(ix);
                        }
                    }
                }
            }
        }

        if (this.clickOffHand && canExecuteInventoryAction()) {
            if (click(45)) {
                this.clickOffHand = false;
            }
        }

        if (this.offhandItem.is(OffhandItemMode.GoldenApple)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            Item offHandItem = offHand.getItem();

            int egapSlot = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gapSlot = InvHelper.getItemSlot(Items.GOLDEN_APPLE);

            if (offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                if (offHand.getCount() < offHand.getMaxStackSize() && egapSlot != -1) {
                    if (canExecuteInventoryAction()) {
                        boolean clicked;
                        if (egapSlot < 9) {
                            clicked = click(egapSlot + 36);
                        } else {
                            clicked = click(egapSlot);
                        }
                        if (clicked) {
                            this.clickOffHand = true;
                        }
                    }
                }
            } else if (offHandItem == Items.GOLDEN_APPLE) {
                if (egapSlot != -1) {
                    if (canExecuteInventoryAction()) {
                        this.swapOffHand(egapSlot);
                    }
                } else if (offHand.getCount() < offHand.getMaxStackSize() && gapSlot != -1) {
                    if (canExecuteInventoryAction()) {
                        int targetSlot = gapSlot;
                        boolean clicked;
                        if (targetSlot < 9) {
                            clicked = click(targetSlot + 36);
                        } else {
                            clicked = click(targetSlot);
                        }
                        if (clicked) {
                            this.clickOffHand = true;
                        }
                    }
                }
            } else {
                if (egapSlot != -1) {
                    if (canExecuteInventoryAction()) {
                        this.swapOffHand(egapSlot);
                    }
                } else if (gapSlot != -1) {
                    if (canExecuteInventoryAction()) {
                        this.swapOffHand(gapSlot);
                    }
                }
            }
        } else if (this.offhandItem.is(OffhandItemMode.Projectile)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            ItemStack bestProjectile = InvHelper.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InvHelper.getItemStackSlot(bestProjectile);
                boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL || offHand.getCount() < bestProjectile.getCount();
                if (shouldSwap && slot != -1 && canExecuteInventoryAction()) this.swapOffHand(slot);
            }
        } else if (this.offhandItem.is(OffhandItemMode.FishingRod)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            int slotx = InvHelper.getItemSlot(Items.FISHING_ROD);
            if (slotx != -1 && canExecuteInventoryAction() && offHand.getItem() != Items.FISHING_ROD)
                this.swapOffHand(slotx);
        } else if (this.offhandItem.is(OffhandItemMode.Block)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            ItemStack bestBlock = InvHelper.getBestBlock();
            if (bestBlock != null) {
                int slotx = InvHelper.getItemStackSlot(bestBlock);
                boolean shouldSwapx = !InvHelper.isValidStack(offHand) || offHand.getCount() < bestBlock.getCount();
                if (shouldSwapx && slotx != -1 && canExecuteInventoryAction()) this.swapOffHand(slotx);
            }
        }

        if (this.switchGoldenApple.getValue() && !this.offhandItem.is(OffhandItemMode.GoldenApple)) {
            int targetSlotIdx = this.goldenAppleSlot.getValue() - 1;
            int egapSlot = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gapSlot = InvHelper.getItemSlot(Items.GOLDEN_APPLE);
            int bestGapSlot = (egapSlot != -1) ? egapSlot : gapSlot;
            if (bestGapSlot != -1) {
                ItemStack currentInSlot = InvHelper.getInventoryStack(targetSlotIdx);
                ItemStack bestGapItem = InvHelper.getInventoryStack(bestGapSlot);
                if (currentInSlot.getItem() != bestGapItem.getItem() || (currentInSlot.getCount() < bestGapItem.getCount() && currentInSlot.getItem() == bestGapItem.getItem())) {
                    this.swapItem(targetSlotIdx, bestGapItem);
                }
            }
        }

        if (this.switchBlock.getValue()) {
            int blockSlotIndex = this.blockSlot.getValue() - 1;
            ItemStack currentBlock = InvHelper.getInventoryStack(blockSlotIndex);
            ItemStack bestBlock = InvHelper.getBestBlock();
            if (bestBlock != null && (bestBlock.getCount() > currentBlock.getCount() || !InvHelper.isValidStack(currentBlock)) && !this.offhandItem.is(OffhandItemMode.Block)) {
                this.swapItem(blockSlotIndex, bestBlock);
            }
            if ((float) InvHelper.getBlockCountInInventory() > this.maxBlockSize.getValue()) {
                this.throwItem(InvHelper.getWorstBlock());
            }
        }

        if (this.switchSword.getValue()) {
            int slotIndex = this.swordSlot.getValue() - 1;
            ItemStack currentSword = InvHelper.getInventoryStack(slotIndex);
            ItemStack bestSword = InvHelper.getBestSword();
            ItemStack bestShapeAxe = InvHelper.getBestShapeAxe();
            if (InvHelper.getAxeDamage(bestShapeAxe) > InvHelper.getSwordDamage(bestSword))
                bestSword = bestShapeAxe;
            if (bestSword != null) {
                float currentDamage = InvHelper.isSword(currentSword) ? InvHelper.getSwordDamage(currentSword) : InvHelper.getAxeDamage(currentSword);
                float bestWeaponDamage = InvHelper.isSword(bestSword) ? InvHelper.getSwordDamage(bestSword) : InvHelper.getAxeDamage(bestSword);
                if (bestWeaponDamage > currentDamage) this.swapItem(slotIndex, bestSword);
            }
        }

        if (this.switchPickaxe.getValue()) {
            int slotIndex = this.pickaxeSlot.getValue() - 1;
            ItemStack bestPickaxe = InvHelper.getBestPickaxe();
            ItemStack currentPickaxe = InvHelper.getInventoryStack(slotIndex);
            if (InvHelper.isPickaxe(bestPickaxe) && (InvHelper.getToolScore(bestPickaxe) > InvHelper.getToolScore(currentPickaxe) || !InvHelper.isPickaxe(currentPickaxe)))
                this.swapItem(slotIndex, bestPickaxe);
        }

        if (this.switchAxe.getValue()) {
            int slotIndex = this.axeSlot.getValue() - 1;
            ItemStack bestAxe = InvHelper.getBestAxe();
            ItemStack currentAxe = InvHelper.getInventoryStack(slotIndex);
            if (bestAxe != null && bestAxe.getItem() instanceof AxeItem && (InvHelper.getToolScore(bestAxe) > InvHelper.getToolScore(currentAxe) || !(currentAxe.getItem() instanceof AxeItem)))
                this.swapItem(slotIndex, bestAxe);
        }

        if (this.switchRod.getValue() && !this.offhandItem.is(OffhandItemMode.FishingRod)) {
            int slotIndex = this.rodSlot.getValue() - 1;
            ItemStack bestRod = InvHelper.getFishingRod();
            ItemStack currentRod = InvHelper.getInventoryStack(slotIndex);
            if (!(currentRod.getItem() instanceof FishingRodItem)) this.swapItem(slotIndex, bestRod);
        }

        if (this.switchBow.getValue()) {
            int slotIndex = this.bowSlot.getValue() - 1;
            ItemStack currentBow = InvHelper.getInventoryStack(slotIndex);
            ItemStack bestBow;
            float bestScore, currentScore;
            if (this.preferBow.is(BowPriorityMode.Crossbow)) {
                bestBow = InvHelper.getBestCrossbow();
                bestScore = InvHelper.getCrossbowScore(bestBow);
                currentScore = InvHelper.getCrossbowScore(currentBow);
            } else if (this.preferBow.is(BowPriorityMode.PowerBow)) {
                bestBow = InvHelper.getBestPowerBow();
                bestScore = InvHelper.getPowerBowScore(bestBow);
                currentScore = InvHelper.getPowerBowScore(currentBow);
            } else {
                bestBow = InvHelper.getBestPunchBow();
                bestScore = InvHelper.getPunchBowScore(bestBow);
                currentScore = InvHelper.getPunchBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestCrossbow();
                bestScore = InvHelper.getCrossbowScore(bestBow);
                currentScore = InvHelper.getCrossbowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestPowerBow();
                bestScore = InvHelper.getPowerBowScore(bestBow);
                currentScore = InvHelper.getPowerBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestPunchBow();
                bestScore = InvHelper.getPunchBowScore(bestBow);
                currentScore = InvHelper.getPunchBowScore(currentBow);
            }
            if (bestBow != null && bestScore > currentScore) this.swapItem(slotIndex, bestBow);
            if ((float) InvHelper.getItemCount(Items.ARROW) > this.maxArrowSize.getValue())
                this.throwItem(InvHelper.getWorstArrow());
        }

        if (this.switchEnderPearl.getValue())
            this.swapItem(this.enderPearlSlot.getValue() - 1, Items.ENDER_PEARL);
        if (this.switchWaterBucket.getValue())
            this.swapItem(this.waterBucketSlot.getValue() - 1, Items.WATER_BUCKET);
        if (this.switchFireball.getValue())
            this.swapItem(this.fireballSlot.getValue() - 1, Items.FIRE_CHARGE);

        if (this.keepProjectile.getValue()) {
            if ((float) (InvHelper.getItemCount(Items.EGG) + InvHelper.getItemCount(Items.SNOWBALL)) > this.maxProjectileSize.getValue())
                this.throwItem(InvHelper.getWorstProjectile());
            if (this.switchProjectile.getValue() && !this.offhandItem.is(OffhandItemMode.Projectile)) {
                int pSlot = this.projectileSlot.getValue() - 1;
                if (InvHelper.getItemCount(Items.EGG) > 0) this.swapItem(pSlot, Items.EGG);
                else if (InvHelper.getItemCount(Items.SNOWBALL) > 0) this.swapItem(pSlot, Items.SNOWBALL);
            }
        }

        if (this.throwItems.getValue()) {
            List<Integer> slots = IntStream.range(0, mc.player.getInventory().getNonEquipmentItems().size()).boxed().collect(Collectors.toList());
            Collections.shuffle(slots);
            for (Integer slotIdx : slots) {
                ItemStack stack = InvHelper.getInventoryStack(slotIdx);
                if (!stack.isEmpty() && !this.isItemUseful(stack) && canExecuteInventoryAction()) {
                    if (this.throwItem(stack)) {
                        return;
                    }
                }
            }
        }

        if (actionState == ActionState.READY_TO_EXECUTE && !actionConsumed) {
            actionState = ActionState.IDLE;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSprintTick(ClientTickEvent.Pre event) {
        if (!nullCheck() && actionState == ActionState.WAITING_FOR_SPRINT_STOP) {
            mc.player.setSprinting(false);
            if (!mc.player.wasSprinting) {
                actionState = ActionState.READY_TO_EXECUTE;
            }
        }
    }

    private void swapOffHand(int slot) {
        if (slot < 9) {
            swap(slot + 36, 40);
        } else {
            swap(slot, 40);
        }
    }

    private boolean throwItem(ItemStack item) {
        if (InvHelper.isItemValid(item) && canExecuteInventoryAction()) {
            int itemSlot = InvHelper.getItemStackSlot(item);
            if (itemSlot != -1) {
                if (itemSlot < 9) {
                    return dropAll(itemSlot + 36);
                } else {
                    return dropAll(itemSlot);
                }
            }
        }
        return false;
    }

    private void swapItem(int targetSlot, ItemStack bestItem) {
        ItemStack currentSlot = InvHelper.getInventoryStack(targetSlot);
        if (InvHelper.isItemValid(currentSlot) && bestItem != currentSlot && canExecuteInventoryAction()) {
            int bestItemSlot = InvHelper.getItemStackSlot(bestItem);
            if (bestItemSlot != -1) {
                if (bestItemSlot < 9) {
                    swap(bestItemSlot + 36, targetSlot);
                } else {
                    swap(bestItemSlot, targetSlot);
                }
            }
        }
    }

    private void swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = InvHelper.getInventoryStack(targetSlot);
        if (InvHelper.isItemValid(currentSlot) && canExecuteInventoryAction()) {
            int bestItemSlot = InvHelper.getItemSlot(item);
            if (bestItemSlot != -1) {
                ItemStack bestItemStack = InvHelper.getInventoryStack(bestItemSlot);
                if (currentSlot.getItem() != item || currentSlot.getItem() == item && currentSlot.getCount() < bestItemStack.getCount()) {
                    if (bestItemSlot < 9) {
                        swap(bestItemSlot + 36, targetSlot);
                    } else {
                        swap(bestItemSlot, targetSlot);
                    }
                }
            }
        }
    }

    public boolean isSprintTransitionPending() {
        return mode.is(Mode.Silent) && actionState != ActionState.IDLE;
    }

    public boolean prepareInventoryAction() {
        if (!mode.is(Mode.Silent)) {
            return true;
        }
        if (actionState == ActionState.WAITING_FOR_SPRINT_STOP) {
            return false;
        }
        if (actionState == ActionState.READY_TO_EXECUTE) {
            return true;
        }

        if (mc.player.isSprinting() || mc.player.wasSprinting) {
            actionState = ActionState.WAITING_FOR_SPRINT_STOP;
            mc.player.setSprinting(false);
            return false;
        }

        return true;
    }

    private void completeInventoryAction() {
        timer.reset();
        delayMs = MathUtils.getRandom(minDelay.getValue(), maxDelay.getValue());
        this.inventoryOpen = true;
        actionConsumed = true;
        actionState = mode.is(Mode.Silent) ? ActionState.WAITING_FOR_INVENTORY_CLOSE : ActionState.IDLE;
    }

    private boolean canExecuteInventoryAction() {
        return !actionConsumed && timer.passedMillise(delayMs);
    }

    private void cancelPendingAction() {
        if (actionState != ActionState.IDLE && actionState != ActionState.WAITING_FOR_INVENTORY_CLOSE) {
            actionState = ActionState.IDLE;
        }
    }

    private boolean click(int slot) {
        if (!prepareInventoryAction()) return false;
        ClickSlotUtils.click(slot);
        completeInventoryAction();
        return true;
    }

    private boolean swap(int slot, int hotbarSlot) {
        if (!prepareInventoryAction()) return false;
        ClickSlotUtils.swap(slot, hotbarSlot);
        completeInventoryAction();
        return true;
    }

    private boolean shiftClick(int slot) {
        if (!prepareInventoryAction()) return false;
        ClickSlotUtils.shiftClick(slot);
        completeInventoryAction();
        return true;
    }

    private boolean dropAll(int slot) {
        if (!prepareInventoryAction()) return false;
        ClickSlotUtils.dropAll(slot);
        completeInventoryAction();
        return true;
    }

}
