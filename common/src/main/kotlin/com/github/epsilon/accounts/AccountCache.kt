package com.github.epsilon.accounts

import com.github.epsilon.Constants.mc
import com.github.epsilon.graphics.PlayerHeadTexture
import com.github.epsilon.managers.ExecutorManager
import com.github.epsilon.managers.SkinManager
import com.google.gson.JsonObject
import com.mojang.util.UndashedUuid

class AccountCache {

    var username: String = ""
    var uuid: String = ""

    private var headTexture: PlayerHeadTexture? = null

    @Volatile
    private var loadingHead: Boolean = false

    fun getHeadTexture(): PlayerHeadTexture {
        val texture = headTexture
        return texture ?: SkinManager.STEVE_HEAD
    }

    fun loadHead() {
        loadHead(null)
    }

    fun loadHead(callback: Runnable?) {
        if (headTexture != null || uuid.isBlank()) {
            if (callback != null) mc.execute(callback)
            return
        }

        if (loadingHead) return

        loadingHead = true

        ExecutorManager.execute {
            val head = SkinManager.fetchHead(UndashedUuid.fromStringLenient(uuid))
            mc.execute {
                if (head != null) headTexture = PlayerHeadTexture(head)
                loadingHead = false
                callback?.run()
            }
        }
    }

    fun write(obj: JsonObject) {
        obj.addProperty("username", username)
        obj.addProperty("uuid", uuid)
    }

    fun read(obj: JsonObject) {
        username = obj.get("username").asString
        uuid = obj.get("uuid").asString
        loadHead()
    }

}
