package com.github.epsilon.managers.impl;

import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;

public class FriendManager {

    private final HashSet<String> friends = new HashSet<>();

    public boolean isFriend(Player player) {
        return friends.contains(player.getGameProfile().name());
    }

    public boolean isFriend(String name) {
        return friends.contains(name);
    }

    public void addFriend(String name) {
        friends.add(name);
    }

    public void removeFriend(String name) {
        friends.remove(name);
    }

    public void clearFriends() {
        friends.clear();
    }

    public List<String> getFriends() {
        return friends.stream().toList();
    }

}
