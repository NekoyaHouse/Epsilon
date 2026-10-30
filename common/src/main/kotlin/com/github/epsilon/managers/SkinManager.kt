package com.github.epsilon.managers

import com.github.epsilon.Constants
import com.github.epsilon.accounts.TexturesJson
import com.github.epsilon.accounts.UuidToProfileResponse
import com.github.epsilon.graphics.PlayerHeadTexture
import com.github.epsilon.utils.network.Http
import com.google.gson.Gson
import java.io.IOException
import java.util.Base64
import java.util.UUID

object SkinManager {

    var STEVE_HEAD: PlayerHeadTexture = PlayerHeadTexture()

    fun fetchHead(id: UUID?): ByteArray? {
        if (id == null) return null

        val url = getSkinUrl(id)
        if (url == null) return null

        try {
            return PlayerHeadTexture.downloadHead(url)
        } catch (e: IOException) {
            Constants.LOGGER.error("Could not fetch player head for {}.", id, e)
            return null
        }
    }

    fun getSkinUrl(id: UUID): String? {
        val res2 = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/$id")
            .exceptionHandler { e -> Constants.LOGGER.error("无法连接到 Mojang 会话服务器.", e) }
            .sendJson<UuidToProfileResponse>(UuidToProfileResponse::class.java)
        if (res2 == null) return null

        val base64Textures = res2.getPropertyValue("textures")
        if (base64Textures == null) return null

        val texturesJson = Gson().fromJson(
            String(Base64.getDecoder().decode(base64Textures)), TexturesJson::class.java
        )
        val textures = texturesJson.textures ?: return null
        val skin = textures.SKIN ?: return null

        return skin.url
    }

}
