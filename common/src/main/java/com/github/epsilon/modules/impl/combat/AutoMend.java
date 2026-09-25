package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.orchestration.*;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public class AutoMend extends Module {

    public static final AutoMend INSTANCE = new AutoMend();

    private AutoMend() {
        super("Auto Mend", Category.COMBAT);
        setDispatchMode(ModuleDispatchMode.MANAGED);
        part(new ObservePart());
        part(new CommitPart());
    }

    /**
     * OBSERVE：只查找经验瓶所在槽位，不切换物品栏、不使用物品。
     */
    private final class ObservePart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("observe.bottle"))
                    .phase(Phase.OBSERVE)
                    .handler(AutoMend.this::observeBottle);
        }
    }

    /**
     * COMMIT：换手持瓶、使用经验瓶与回切槽位都是外部副作用。
     */
    private final class CommitPart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("commit.use_bottle"))
                    .phase(Phase.COMMIT)
                    .handler(AutoMend.this::commitUseBottle);
        }
    }

    private enum SwitchMode {
        Normal,
        Silent
    }

    private final EnumSetting<SwitchMode> switchMode = enumSetting("Switch Mode", SwitchMode.Normal);
    private final BoolSetting swingHand = boolSetting("Swing Hand", false);

    private boolean shouldSwapBack;
    /** observe.bottle 产出的槽位事实，只在同一 tick 的 commit.use_bottle 中消费。 */
    private FindItemResult bottle;

    @Override
    protected void onEnable() {
        shouldSwapBack = false;
    }

    @Override
    protected void onDisable() {
        if (shouldSwapBack) {
            InvUtils.swapBack();
        }
    }
    private void observeBottle(PlayerTickEvent.Pre event) {
        bottle = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
    }

    private void commitUseBottle(PlayerTickEvent.Pre event) {
        FindItemResult result = bottle;
        if (result == null || !result.found()) return;

        RotationManager.INSTANCE.setRotations(new Rot2f(mc.player.getYRot(), 90), 180, Priority.High);

        InvUtils.swap(result.slot(), true);

        InteractionHand hand = result.getHand();
        mc.gameMode.useItem(mc.player, hand);
        if (swingHand.getValue()) {
            PlayerUtils.swingHand(hand);
        }

        if (switchMode.is(SwitchMode.Silent)) {
            InvUtils.swapBack();
        } else {
            shouldSwapBack = true;
        }
    }

}
