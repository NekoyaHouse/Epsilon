package com.github.epsilon.kernel.hardware

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object HardwareIDUtil {
    fun rawHWID(): ByteArray {
        val main = (System.getenv("PROCESS_IDENTIFIER")
                + System.getenv("PROCESSOR_LEVEL")
                + System.getenv("PROCESSOR_REVISION")
                + System.getenv("PROCESSOR_ARCHITECTURE")
                + System.getenv("PROCESSOR_ARCHITEW6432")
                + System.getenv("NUMBER_OF_PROCESSORS")
                + System.getenv("COMPUTERNAME"))
        val bytes = main.toByteArray(StandardCharsets.UTF_8)
        val messageDigest = MessageDigest.getInstance("MD5")
        return messageDigest.digest(bytes)
    }

    fun encrypt(strToEncrypt: String, secret: String): String {
        try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey(secret))
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: Exception) {
            println("Error while encrypting: $e")
        }
        throw SecurityException("Error while encrypting: $strToEncrypt")
    }


    fun getKey(myKey: String): SecretKeySpec {
        val sha: MessageDigest
        try {
            var key = myKey.toByteArray(StandardCharsets.UTF_8)
            sha = MessageDigest.getInstance("SHA-1")
            key = sha.digest(key)
            key = key.copyOf(16)
            return SecretKeySpec(key, "AES")
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        throw SecurityException("Error while encrypting")
    }

    fun getHWID(): String {
        return try {
            val hwidmain = (System.getenv("PROCESSOR_IDENTIFIER")
                    + System.getenv("PROCESSOR_LEVEL")
                    + System.getenv("PROCESSOR_REVISION")
                    + System.getenv("PROCESSOR_ARCHITECTURE")
                    + System.getenv("PROCESSOR_ARCHITEW6432")
                    + System.getenv("NUMBER_OF_PROCESSORS")
                    + System.getenv("COMPUTERNAME")
                    + System.getProperty("user.name"))
            val md = MessageDigest.getInstance("MD5")
            md.update(hwidmain.toByteArray())
            val hexString = StringBuffer()
            val byteData = md.digest()
            for (aByteData in byteData) {
                val hex = Integer.toHexString(0xff and aByteData.toInt())
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "Error:$e"
        }
    }

    fun base64(str: String): String {
        val bytes = str.toByteArray()

        //Base64 加密
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun getEncryptedHWID(key: String): String {
        return base64(encrypt(getHWID(), key))
    }

}