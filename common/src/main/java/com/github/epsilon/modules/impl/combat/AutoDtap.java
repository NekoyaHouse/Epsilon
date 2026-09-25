package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.orchestration.*;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class AutoDtap extends Module {

    public static final AutoDtap INSTANCE = new AutoDtap();

    private AutoDtap() {
        super("Auto Dtap", Category.COMBAT);
        setDispatchMode(ModuleDispatchMode.MANAGED);
        part(new ObservePart());
        part(new CommitPart());
    }

    /**
     * OBSERVE：只记录右键按下这一事实，事件本身不被修改。
     */
    private final class ObservePart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(MousePressEvent.class, NodeKey.of("observe.right_click"))
                    .phase(Phase.OBSERVE)

                    .handler(AutoDtap.this::onMouse);
        }
    }

    /**
     * COMMIT：状态机会切换物品栏（{@code InvUtils.swap}）并直接调用 {@code gameMode.useItemOn}，
     * 读取与提交在同一步骤内交织，没有可独立拆出的只读阶段。
     */
    private final class CommitPart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("commit.dtap_sequence"))
                    .phase(Phase.COMMIT)

                    .handler(AutoDtap.this::onTick);
        }
    }

    private final BoolSetting swapBack = boolSetting("SwapBack", true);

    private final Random random = new Random();

    private int stepDelay = 0;
    private int step = 0;
    private int originalSlot = -1;
    private boolean rightClicked = false;

    @Override
    protected void onEnable() {
        stepDelay = 0;
        step = 0;
        originalSlot = -1;
        rightClicked = false;
    }

    @Override
    protected void onDisable() {
        resetState();
    }
    private void onMouse(MousePressEvent event) {
        if (event.getButton() == InputConstants.MOUSE_BUTTON_RIGHT && event.getAction() == InputConstants.PRESS) {
            rightClicked = true;
        }
    }
    private void onTick(PlayerTickEvent.Pre event) {
        if (step != 0) {
            while (mc.options.keyUse.consumeClick()) {
            }
        }

        // 处理延迟
        if (stepDelay > 0) {
            stepDelay--;
            return;
        }

        // 状态机处理
        switch (step) {
            case 0:
                if (rightClicked) {
                    rightClicked = false;

                    ItemStack main = mc.player.getMainHandItem();
                    if (!main.is(ItemTags.SWORDS)) return;

                    HitResult hit = mc.hitResult;
                    if (!(hit instanceof BlockHitResult blockHit)) return;
                    if (blockHit.getType() != HitResult.Type.BLOCK) return;

                    BlockPos pos = blockHit.getBlockPos();
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) return;

                    boolean isObsidian = state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);

                    // 寻找水晶
                    int endCrystalSlot = InvUtils.findInHotbar(Items.END_CRYSTAL).slot();
                    if (endCrystalSlot == -1) return;

                    originalSlot = mc.player.getInventory().getSelectedSlot();

                    if (isObsidian) {
                        // 如果已经是黑曜石，直接切水晶并放�?
                        InvUtils.swap(endCrystalSlot, false);

                        BlockHitResult topHit = new BlockHitResult(
                                blockHit.getLocation(),
                                Direction.UP,
                                pos,
                                false
                        );
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, topHit);
                        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);

                        stepDelay = 1 + random.nextInt(2);
                        step = 2; // 跳到恢复阶段
                    } else {
                        int obsidianSlot = InvUtils.findInHotbar(Items.OBSIDIAN).slot();
                        if (obsidianSlot == -1) return;

                        InvUtils.swap(obsidianSlot, false);
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
                        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);

                        InvUtils.swap(endCrystalSlot, false);

                        stepDelay = 1 + random.nextInt(2);
                        step = 1;
                    }
                }
                break;

            case 1:
                HitResult hit1 = mc.hitResult;
                if (!(hit1 instanceof BlockHitResult baseHit)) {
                    resetState();
                    return;
                }

                BlockPos base = baseHit.getBlockPos();
                BlockHitResult topHit = new BlockHitResult(
                        baseHit.getLocation(),
                        Direction.UP,
                        base,
                        false
                );
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, topHit);
                PlayerUtils.swingHand(InteractionHand.MAIN_HAND);

                stepDelay = 1 + random.nextInt(2);
                step = 2;
                break;

            case 2:
                if (swapBack.getValue() && originalSlot != -1) {
                    InvUtils.swap(originalSlot, false);
                }
                resetState();
                break;
        }
    }

    private void resetState() {
        step = 0;
        stepDelay = 0;
        originalSlot = -1;
        rightClicked = false;
    }

}
