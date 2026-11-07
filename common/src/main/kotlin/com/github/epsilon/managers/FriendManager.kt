package com.github.epsilon.managers

import com.github.epsilon.kernel.state.*
import net.minecraft.world.entity.player.Player

object FriendManager {

    private var friends: Set<String> by mutableStateOf(emptySet())

    fun isFriend(player: Player): Boolean {
        return friends.contains(player.gameProfile.name())
    }

    fun isFriend(name: String): Boolean {
        return friends.contains(name)
    }

    fun addFriend(name: String) {
        friends = friends + name
    }

    fun removeFriend(name: String) {
        friends = friends - name
    }

    fun clearFriends() {
        friends = emptySet()
    }

    fun getFriends(): List<String> {
        return friends.stream().toList()
    }

}
