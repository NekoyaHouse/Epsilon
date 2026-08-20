package me.sofurry.via.visuals.features.r1_7_tab_list_style;

import me.sofurry.via.visuals.injection.access.r1_7_tab_list_tyle.IPlayerInfo;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Comparator;

public final class PlayerInfoIndexComparator implements Comparator<PlayerInfo> {

    public static final PlayerInfoIndexComparator INSTANCE = new PlayerInfoIndexComparator();

    private PlayerInfoIndexComparator() {
    }

    @Override
    public int compare(final PlayerInfo first, final PlayerInfo second) {
        final int firstIndex = ((IPlayerInfo) first).viaVersionVisuals$getIndex();
        final int secondIndex = ((IPlayerInfo) second).viaVersionVisuals$getIndex();
        return Integer.compare(firstIndex, secondIndex);
    }

}
