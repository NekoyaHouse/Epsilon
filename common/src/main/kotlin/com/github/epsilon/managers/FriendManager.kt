package com.github.epsilon.managers

import net.minecraft.world.entity.player.Player
import java.util.HashSet

object FriendManager {

    private val friends = HashSet<String>()

    fun isFriend(player: Player): Boolean {
        return friends.contains(player.getGameProfile().name())
    }

    fun isFriend(name: String): Boolean {
        return friends.contains(name)
    }

    fun addFriend(name: String) {
        friends.add(name)
    }

    fun removeFriend(name: String) {
        friends.remove(name)
    }

    fun clearFriends() {
        friends.clear()
    }

    fun getFriends(): List<String> {
        return friends.stream().toList()
    }

}
