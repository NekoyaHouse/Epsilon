package com.github.epsilon.managers;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.managers.impl.FriendManager;
import com.github.epsilon.managers.impl.HealthManager;
import com.github.epsilon.managers.impl.NotificationManager;
import com.github.epsilon.managers.impl.TimerManager;
import com.github.epsilon.managers.impl.network.ClientboundPacketManager;
import com.github.epsilon.managers.impl.network.ServerboundPacketManager;
import com.github.epsilon.managers.impl.rotations.RotationManager;
import com.github.epsilon.managers.impl.rotations.SilentRotationManager;
import com.github.epsilon.managers.impl.rotations.SnapRotationManager;
import com.github.epsilon.managers.impl.sound.SoundManager;
import com.github.epsilon.managers.impl.target.TargetManager;
import com.github.epsilon.modules.impl.ClientSetting;

public class Managers {

    public static RotationManager ROTATION;
    public static TargetManager TARGET;
    public static HealthManager HEALTH;
    public static ServerboundPacketManager C2SPACKET;
    public static ClientboundPacketManager S2CPACKET;
    public static FriendManager FRIEND;
    public static SoundManager SOUND;
    public static NotificationManager NOTIFICATION;
    public static TimerManager TIMER;

    public static void initManagers() {
        switchRotationManager(ClientSetting.INSTANCE.rotationMode.getValue());
        TARGET = new TargetManager();
        HEALTH = new HealthManager();
        C2SPACKET = new ServerboundPacketManager();
        S2CPACKET = new ClientboundPacketManager();
        FRIEND = new FriendManager();
        SOUND = new SoundManager();
        NOTIFICATION = new NotificationManager();
        TIMER = new TimerManager();
    }

    public static void switchRotationManager(RotationManager.RotationMode mode) {
        RotationManager previous = ROTATION;
        RotationManager next = switch (mode) {
            case SNAP -> new SnapRotationManager();
            case SILENT -> new SilentRotationManager();
        };

        if (previous != null) {
            next.copyStateFrom(previous);
            EventBus.INSTANCE.unsubscribe(previous);
        }

        ROTATION = next;
        EventBus.INSTANCE.subscribe(next);
    }

}
