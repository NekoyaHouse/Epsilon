package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.SkeletonRenderer;

import java.awt.*;

public class Skeleton extends Module {

    public static final Skeleton INSTANCE = new Skeleton();

    private final BoolSetting friends = boolSetting("Friends", true);
    private final BoolSetting self = boolSetting("Self", false);
    private final BoolSetting invisibles = boolSetting("Invisibles", true);
    private final DoubleSetting distance = doubleSetting("Distance", 128.0, 8.0, 256.0, 8.0);
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 2, 0.5, 5.0, 0.5);
    private final ColorSetting color = colorSetting("Color", Color.WHITE);
    private final ColorSetting friendColor = colorSetting("Friend Color", new Color(0x30FF00), friends::getValue);

    private Skeleton() {
        super("Skeleton", Category.RENDER);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        for (var player : mc.level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (player.isInvisible() && !invisibles.getValue()) continue;
            if (player.position().distanceToSqr(mc.gameRenderer.mainCamera().position()) > distance.getValue() * distance.getValue())
                continue;
            if (player == mc.player && !self.getValue()) continue;
            if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;

            boolean friend = player != mc.player && (FriendManager.INSTANCE.isFriend(player) || TargetManager.INSTANCE.isSameTeam(player));
            if (friend && !friends.getValue()) continue;

            SkeletonRenderer.render(player, friend ? friendColor.getValue() : color.getValue(), lineWidth.getValue().floatValue());
        }
    }

}
