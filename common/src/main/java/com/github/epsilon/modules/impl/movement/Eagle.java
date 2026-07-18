package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.math.MathUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Eagle extends Module {

    public static final Eagle INSTANCE = new Eagle();

    private Eagle() {
        super("Eagle", Category.MOVEMENT);
    }

    private final IntSetting minDelay = intSetting("Min Delay", 2, 0, 10, 1);
    private final IntSetting maxDelay = intSetting("Max Delay", 3, 0, 10, 1);
    private final BoolSetting directionCheck = boolSetting("Direction Check", true);
    private final BoolSetting jumpCheck = boolSetting("Jump Check", true);
    private final BoolSetting pitchCheck = boolSetting("Pitch Check", true);
    private final BoolSetting blocksOnly = boolSetting("Blocks Only", true);
    private final BoolSetting sneakingOnly = boolSetting("Sneaking Only", false);

    private int sneakDelay;

    @Override
    public String getInfo() {
        return minDelay.getValue().equals(maxDelay.getValue())
                ? minDelay.getValue().toString()
                : minDelay.getValue() + "-" + maxDelay.getValue();
    }

    @Override
    protected void onDisable() {
        sneakDelay = 0;
    }

    @Override
    protected void resetCustomState() {
        sneakDelay = 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (sneakDelay > 0) {
            sneakDelay--;
        }

        if (sneakDelay == 0 && isOverEdge()) {
            sneakDelay = MathUtils.getRandom(minDelay.getValue(), maxDelay.getValue());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onMoveInput(KeyboardInputEvent event) {
        if (mc.screen != null) return;

        boolean physicallySneaking = KeybindUtils.isPressed(mc.options.keyShift);
        if (sneakingOnly.getValue() && physicallySneaking && shouldSneak()) {
            event.setSneak(false);
        }

        if (!event.isSneak() && shouldSneak() && (sneakDelay > 0 || isOverEdge())) {
            event.setSneak(true);
        }
    }

    private boolean shouldSneak() {
        if (directionCheck.getValue() && mc.options.keyUp.isDown()) return false;
        if (jumpCheck.getValue() && mc.options.keyJump.isDown()) return false;
        if (pitchCheck.getValue() && mc.player.getXRot() < 69.0f) return false;
        if (sneakingOnly.getValue() && !KeybindUtils.isPressed(mc.options.keyShift)) return false;
        return (!blocksOnly.getValue() || isHoldingBlock()) && mc.player.onGround();
    }

    private boolean isOverEdge() {
        Vec3 predictedMovement = predictMovement();
        Vec3 movement = mc.player.getDeltaMovement().add(predictedMovement.x, 0.0, predictedMovement.z);
        return mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(movement.x, -1.0, movement.z));
    }

    private Vec3 predictMovement() {
        float strafeInput = getInput(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown()) * 0.98F;
        float forwardInput = getInput(mc.options.keyUp.isDown(), mc.options.keyDown.isDown()) * 0.98F;
        float inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput;
        if (inputMagnitude < 1.0E-4F) return Vec3.ZERO;

        inputMagnitude = Math.max(Mth.sqrt(inputMagnitude), 1.0F);
        float inputScale = getAllowedHorizontalDistance() / inputMagnitude;
        strafeInput *= inputScale;
        forwardInput *= inputScale;

        float sinYaw = Mth.sin(mc.player.getYRot() * (float) (Math.PI / 180.0));
        float cosYaw = Mth.cos(mc.player.getYRot() * (float) (Math.PI / 180.0));
        return new Vec3(strafeInput * cosYaw - forwardInput * sinYaw, 0.0, forwardInput * cosYaw + strafeInput * sinYaw);
    }

    private float getAllowedHorizontalDistance() {
        BlockPos posBelow = mc.player.getBlockPosBelowThatAffectsMyMovement();
        float blockFriction = mc.level.getBlockState(posBelow).getBlock().getFriction();
        return mc.player.getSpeed() * (0.21600002F / (blockFriction * blockFriction * blockFriction));
    }

    private float getInput(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }

    private boolean isHoldingBlock() {
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;

        Block block = blockItem.getBlock();
        BlockState state = block.defaultBlockState();
        if (block instanceof EntityBlock || state.getMenuProvider(mc.level, BlockPos.ZERO) != null) return false;
        if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false;

        return !(block instanceof FallingBlock)
                && !(block instanceof PumpkinBlock)
                && !(block instanceof CarvedPumpkinBlock)
                && !(block instanceof SlimeBlock)
                && !(block instanceof TntBlock);
    }

}
