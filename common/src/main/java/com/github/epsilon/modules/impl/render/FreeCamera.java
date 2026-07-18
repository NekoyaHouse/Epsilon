package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

public class FreeCamera extends Module {

    public static final FreeCamera INSTANCE = new FreeCamera();

    private FreeCamera() {
        super("Free Camera", Category.RENDER);
    }

    private final DoubleSetting speed = doubleSetting("Speed", 1.0, 0.0, 10.0, 0.1, v -> speedValue = v);
    private final DoubleSetting speedScrollSensitivity = doubleSetting("Speed Scroll Sensitivity", 0.0, 0.0, 2.0, 0.1);
    private final BoolSetting staySneaking = boolSetting("Stay Sneaking", true);
    private final BoolSetting toggleOnDamage = boolSetting("Toggle On Damage", false);
    private final BoolSetting toggleOnDeath = boolSetting("Toggle On Death", false);
    private final BoolSetting toggleOnLog = boolSetting("Toggle On Log", true);
    private final BoolSetting reloadChunks = boolSetting("Reload Chunks", true);
    private final BoolSetting renderHands = boolSetting("Show Hands", true);
    private final BoolSetting rotate = boolSetting("Rotate", false);
    private final BoolSetting staticView = boolSetting("Static", true);

    public final Vector3d pos = new Vector3d();
    public final Vector3d prevPos = new Vector3d();

    private CameraType perspective;
    private double speedValue;

    public float yaw, pitch;
    public float lastYaw, lastPitch;

    private double fovScale;
    private boolean bobView;

    private boolean forward, backward, right, left, up, down, isSneaking;

    private Rot2f rotation;

    @Override
    protected void onEnable() {
        fovScale = mc.options.fovEffectScale().get();
        bobView = mc.options.bobView().get();
        if (staticView.getValue()) {
            mc.options.fovEffectScale().set((double) 0);
            mc.options.bobView().set(false);
        }
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();

        perspective = mc.options.getCameraType();
        speedValue = speed.getValue();

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
        pos.set(cameraPos.x, cameraPos.y, cameraPos.z);
        prevPos.set(pos);

        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            yaw += 180;
            pitch *= -1;
        }

        lastYaw = yaw;
        lastPitch = pitch;

        isSneaking = mc.options.keyShift.isDown();

        forward = KeybindUtils.isPressed(mc.options.keyUp);
        backward = KeybindUtils.isPressed(mc.options.keyDown);
        right = KeybindUtils.isPressed(mc.options.keyRight);
        left = KeybindUtils.isPressed(mc.options.keyLeft);
        up = KeybindUtils.isPressed(mc.options.keyJump);
        down = KeybindUtils.isPressed(mc.options.keyShift);

        unpress();

        if (reloadChunks.getValue()) {
            mc.levelRenderer.allChanged();
        }
    }

    @Override
    protected void onDisable() {
        if (reloadChunks.getValue()) {
            mc.execute(mc.levelRenderer::allChanged);
        }

        mc.options.setCameraType(perspective);

        if (staticView.getValue()) {
            mc.options.fovEffectScale().set(fovScale);
            mc.options.bobView().set(bobView);
        }

        isSneaking = false;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        unpress();

        prevPos.set(pos);
        lastYaw = yaw;
        lastPitch = pitch;
    }

    private void unpress() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    @EventHandler
    private void onPostClientTick(ClientTickEvent.Post event) {
        if (nullCheck()) return;

        if (mc.getCameraEntity().isInWall()) mc.getCameraEntity().noPhysics = true;
        if (!perspective.isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);

        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw + 90);
        double velX = 0;
        double velY = 0;
        double velZ = 0;

        if (rotate.getValue()) {
            rotation = null;

            if (mc.hitResult instanceof EntityHitResult ehr) {
                rotation = RotationUtils.calculate(ehr.getEntity().blockPosition());
            } else if (mc.hitResult instanceof BlockHitResult bhr) {
                BlockPos crossHairPos = bhr.getBlockPos();
                if (!mc.level.getBlockState(crossHairPos).isAir()) {
                    rotation = RotationUtils.calculate(bhr.getLocation());
                }
            }

            if (rotation != null) {
                Managers.ROTATION.setRotations(rotation, 180, Priority.Highest);
            }
        }

        double s = 0.5;
        if (KeybindUtils.isPressed(mc.options.keySprint)) s = 1;

        boolean a = false;
        if (this.forward) {
            velX += forward.x * s * speedValue;
            velZ += forward.z * s * speedValue;
            a = true;
        }
        if (this.backward) {
            velX -= forward.x * s * speedValue;
            velZ -= forward.z * s * speedValue;
            a = true;
        }

        boolean b = false;
        if (this.right) {
            velX += right.x * s * speedValue;
            velZ += right.z * s * speedValue;
            b = true;
        }
        if (this.left) {
            velX -= right.x * s * speedValue;
            velZ -= right.z * s * speedValue;
            b = true;
        }

        if (a && b) {
            double diagonal = 1 / Math.sqrt(2);
            velX *= diagonal;
            velZ *= diagonal;
        }

        if (this.up) {
            velY += s * speedValue;
        }
        if (this.down) {
            velY -= s * speedValue;
        }

        prevPos.set(pos);
        pos.set(pos.x + velX, pos.y + velY, pos.z + velZ);
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (!mc.player.getAbilities().flying && staySneaking.getValue() && isSneaking) {
            event.setSneak(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKey(KeyPressEvent event) {
        if (onInput(event.getKey(), event.getAction()) && !KeybindUtils.isPressed(GLFW.GLFW_KEY_F3)) {
            event.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseClick(MousePressEvent event) {
        if (onInput(event.getButton(), event.getAction())) {
            event.cancel();
        }
    }

    private boolean onInput(int key, int action) {
        if (KeybindUtils.getKey(mc.options.keyUp) == key) {
            forward = action != GLFW.GLFW_RELEASE;
            mc.options.keyUp.setDown(false);
        } else if (KeybindUtils.getKey(mc.options.keyDown) == key) {
            backward = action != GLFW.GLFW_RELEASE;
            mc.options.keyDown.setDown(false);
        } else if (KeybindUtils.getKey(mc.options.keyRight) == key) {
            right = action != GLFW.GLFW_RELEASE;
            mc.options.keyRight.setDown(false);
        } else if (KeybindUtils.getKey(mc.options.keyLeft) == key) {
            left = action != GLFW.GLFW_RELEASE;
            mc.options.keyLeft.setDown(false);
        } else if (KeybindUtils.getKey(mc.options.keyJump) == key) {
            up = action != GLFW.GLFW_RELEASE;
            mc.options.keyJump.setDown(false);
        } else if (KeybindUtils.getKey(mc.options.keyShift) == key) {
            down = action != GLFW.GLFW_RELEASE;
            mc.options.keyShift.setDown(false);
        } else {
            return false;
        }

        return true;
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (speedScrollSensitivity.getValue() > 0 && mc.screen == null) {
            speedValue += event.getValue() * 0.25 * (speedScrollSensitivity.getValue() * speedValue);
            if (speedValue < 0.1) speedValue = 0.1;
            event.cancel();
        }
    }

    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        event.cancel();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (toggleOnLog.getValue()) {
            toggle();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket packet) {
            Entity entity = mc.level.getEntity(packet.playerId());
            if (entity == mc.player && toggleOnDeath.getValue()) {
                toggle();
                sendChatInfo("Toggled off because you died.");
            }
        } else if (event.getPacket() instanceof ClientboundSetHealthPacket packet) {
            if (mc.player.getHealth() - packet.getHealth() > 0 && toggleOnDamage.getValue()) {
                toggle();
                sendChatInfo("Toggled off because you took damage.");
            }
        } else if (event.getPacket() instanceof ClientboundRespawnPacket) {
            toggle();
            sendChatInfo("Toggled off because you changed dimensions.");
        }
    }

    public void changeLookDirection(double deltaX, double deltaY) {
        lastYaw = yaw;
        lastPitch = pitch;

        yaw += (float) deltaX;
        pitch += (float) deltaY;

        pitch = Mth.clamp(pitch, -90, 90);
    }

    public void sendChatInfo(String text) {
        ChatUtils.addChatMessage("[Free Camera] " + text);
    }

    public boolean renderHands() {
        return !isEnabled() || renderHands.getValue();
    }

    public double getX(float tickDelta) {
        return Mth.lerp(tickDelta, prevPos.x, pos.x);
    }

    public double getY(float tickDelta) {
        return Mth.lerp(tickDelta, prevPos.y, pos.y);
    }

    public double getZ(float tickDelta) {
        return Mth.lerp(tickDelta, prevPos.z, pos.z);
    }

    public double getYaw(float tickDelta) {
        return Mth.lerp(tickDelta, lastYaw, yaw);
    }

    public double getPitch(float tickDelta) {
        return Mth.lerp(tickDelta, lastPitch, pitch);
    }

}
