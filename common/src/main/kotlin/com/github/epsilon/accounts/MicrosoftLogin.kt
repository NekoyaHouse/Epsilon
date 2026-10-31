package com.github.epsilon.accounts

import com.github.epsilon.Constants
import com.github.epsilon.utils.network.Http
import com.google.gson.annotations.SerializedName
import com.mojang.blaze3d.Blaze3D
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.function.Consumer

object MicrosoftLogin {

    class LoginData {

        var mcToken: String? = null
        var newRefreshToken: String? = null
        var uuid: String? = null
        var username: String? = null

        constructor()

        constructor(mcToken: String, newRefreshToken: String, uuid: String, username: String) {
            this.mcToken = mcToken
            this.newRefreshToken = newRefreshToken
            this.uuid = uuid
            this.username = username
        }

        fun isGood(): Boolean {
            return mcToken != null
        }
    }

    private const val CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb"
    private const val PORT = 9675

    @Volatile
    private var server: HttpServer? = null

    @Volatile
    private var callback: Consumer<String>? = null

    fun getRefreshToken(callback: Consumer<String>): String {
        MicrosoftLogin.callback = callback

        startServer()
        val url = "https://login.live.com/oauth20_authorize.srf?client_id=" + CLIENT_ID +
                "&response_type=code&redirect_uri=http://127.0.0.1:" + PORT +
                "&scope=XboxLive.signin%20offline_access&prompt=select_account"
        Blaze3D.openUri(URI.create(url))

        return url
    }

    fun login(refreshToken: String): LoginData {
        var currentRefreshToken = refreshToken

        // Refresh access token
        val res: AuthTokenResponse? = Http.post("https://login.live.com/oauth20_token.srf")
            .bodyForm("client_id=" + CLIENT_ID + "&refresh_token=" + currentRefreshToken + "&grant_type=refresh_token&redirect_uri=http://127.0.0.1:" + PORT)
            .sendJson(AuthTokenResponse::class.java)

        if (res == null) return LoginData()

        val accessToken = res.access_token
        currentRefreshToken = res.refresh_token ?: currentRefreshToken

        // XBL
        val xblRes: XblXstsResponse? = Http.post("https://user.auth.xboxlive.com/user/authenticate")
            .bodyJson("{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + accessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}")
            .sendJson(XblXstsResponse::class.java)

        if (xblRes == null) return LoginData()

        // XSTS
        val xstsRes: XblXstsResponse? = Http.post("https://xsts.auth.xboxlive.com/xsts/authorize")
            .bodyJson("{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblRes.Token + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}")
            .sendJson(XblXstsResponse::class.java)

        if (xstsRes == null) return LoginData()

        // Minecraft
        val mcRes: McResponse? = Http.post("https://api.minecraftservices.com/authentication/login_with_xbox")
            .bodyJson("{\"identityToken\":\"XBL3.0 x=" + xblRes.displayClaims!!.xui!![0]!!.uhs + ";" + xstsRes.Token + "\"}")
            .sendJson(McResponse::class.java)

        if (mcRes == null) return LoginData()

        // Check game ownership
        val gameOwnershipRes: GameOwnershipResponse? =
            Http.get("https://api.minecraftservices.com/entitlements/mcstore")
                .bearer(mcRes.access_token ?: "")
                .sendJson(GameOwnershipResponse::class.java)

        if (gameOwnershipRes == null || !gameOwnershipRes.hasGameOwnership()) return LoginData()

        // Profile
        val profileRes: ProfileResponse? = Http.get("https://api.minecraftservices.com/minecraft/profile")
            .bearer(mcRes.access_token ?: "")
            .sendJson(ProfileResponse::class.java)

        if (profileRes == null) return LoginData()

        return LoginData(mcRes.access_token ?: "", currentRefreshToken, profileRes.id ?: "", profileRes.name ?: "")
    }

    private fun startServer() {
        if (server != null) return

        try {
            val newServer = HttpServer.create(InetSocketAddress("127.0.0.1", PORT), 0)

            newServer.createContext("/") { exchange -> handleRequest(exchange) }
            newServer.executor = Executors.newVirtualThreadPerTaskExecutor()
            newServer.start()
            server = newServer
        } catch (e: IOException) {
            Constants.LOGGER.error("Error starting Microsoft login server", e)
            stopServer()
        }
    }

    fun stopServer() {
        val currentServer = server ?: return

        currentServer.stop(0)
        server = null

        callback = null
    }

    /**
     * 触发登录回调；`null` 表示认证失败。
     *
     * Java 的 `Consumer<String>` 允许传入 null，Kotlin 侧需要显式放宽为可空，保持原语义。
     */
    @Suppress("UNCHECKED_CAST")
    private fun fireCallback(token: String?) {
        val current = callback ?: return
        (current as Consumer<String?>).accept(token)
    }

    private fun handleRequest(req: HttpExchange) {
        if (req.requestMethod == "GET") {
            // Login
            val query = parseURL(req.requestURI.rawQuery)

            var ok = false
            for (pair in query) {
                if (pair.first == "code") {
                    handleCode(pair.second)

                    ok = true
                    break
                }
            }

            if (!ok) {
                writeText(req, "Cannot authenticate.")
                fireCallback(null)
            } else {
                writeText(req, "You may now close this page.")
            }
        }

        stopServer()
    }

    private fun handleCode(code: String) {
        val res: AuthTokenResponse? = Http.post("https://login.live.com/oauth20_token.srf")
            .bodyForm("client_id=" + CLIENT_ID + "&code=" + code + "&grant_type=authorization_code&redirect_uri=http://127.0.0.1:" + PORT)
            .sendJson(AuthTokenResponse::class.java)

        if (res == null) fireCallback(null) else fireCallback(res.refresh_token)
    }

    private fun writeText(req: HttpExchange, text: String) {
        val responseBody = text.toByteArray(StandardCharsets.UTF_8)
        req.sendResponseHeaders(200, responseBody.size.toLong())
        req.responseBody.use { out ->
            out.write(responseBody)
        }
    }

    // reimplementation of apache https URLEncodedUtils#parse
    private fun parseURL(string: String?): List<com.mojang.datafixers.util.Pair<String, String>> {
        val query = ArrayList<com.mojang.datafixers.util.Pair<String, String>>()
        val buf = (string ?: "").toCharArray()
        var i = 0
        while (i < buf.size) {
            val name = StringBuilder()
            val value = StringBuilder()

            while (i < buf.size) {
                if (buf[i] == '&' || buf[i] == ';' || buf[i] == '=') break
                else name.append(buf[i])
                i++
            }

            if (i < buf.size) {
                val ch = buf[i]
                i += 1

                if (ch == '=') {
                    while (i < buf.size) {
                        if (buf[i] == '&' || buf[i] == ';') {
                            i += 1
                            break
                        } else {
                            value.append(buf[i])
                        }
                        i++
                    }
                }
            }

            if (name.isNotEmpty()) {
                query.add(
                    com.mojang.datafixers.util.Pair.of(
                        urlDecode(name.toString()),
                        urlDecode(value.toString()) ?: ""
                    )
                )
            }
        }

        return query
    }

    // near 1:1 copy of apache https URLEncodedUtils#urlDecode
    // not really necessary for this particular use case but doesn't hurt to be thorough
    private fun urlDecode(s: String?): String? {
        if (s == null) return null

        val bb = ByteBuffer.allocate(s.length)
        val cb = CharBuffer.wrap(s)
        while (cb.hasRemaining()) {
            val c = cb.get()
            if (c == '%' && cb.remaining() >= 2) {
                val uc = cb.get()
                val lc = cb.get()
                val u = Character.digit(uc, 16)
                val l = Character.digit(lc, 16)
                if (u != -1 && l != -1) {
                    bb.put(((u shl 4) + l).toByte())
                } else {
                    bb.put('%'.code.toByte())
                    bb.put(uc.code.toByte())
                    bb.put(lc.code.toByte())
                }
            } else if (c == '+') {
                bb.put(' '.code.toByte())
            } else {
                bb.put(c.code.toByte())
            }
        }
        bb.flip()
        return StandardCharsets.UTF_8.decode(bb).toString()
    }

    private class AuthTokenResponse {
        @field:SerializedName("access_token")
        var access_token: String? = null

        @field:SerializedName("refresh_token")
        var refresh_token: String? = null
    }

    private class XblXstsResponse {
        @field:SerializedName("Token")
        var Token: String? = null

        @field:SerializedName("DisplayClaims")
        var displayClaims: DisplayClaims? = null

        class DisplayClaims {
            @field:SerializedName("xui")
            var xui: Array<Claim?>? = null

            class Claim {
                @field:SerializedName("uhs")
                var uhs: String? = null
            }
        }
    }

    private class McResponse {
        @field:SerializedName("access_token")
        var access_token: String? = null
    }

    private class GameOwnershipResponse {
        @field:SerializedName("items")
        var items: Array<Item?>? = null

        class Item {
            @field:SerializedName("name")
            var name: String? = null
        }

        fun hasGameOwnership(): Boolean {
            var hasProduct = false
            var hasGame = false

            for (item in items ?: return false) {
                if ("product_minecraft" == item?.name) hasProduct = true
                else if ("game_minecraft" == item?.name) hasGame = true
            }

            return hasProduct && hasGame
        }
    }

    private class ProfileResponse {
        @field:SerializedName("id")
        var id: String? = null

        @field:SerializedName("name")
        var name: String? = null
    }

}
