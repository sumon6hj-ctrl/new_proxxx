package com.khanproxy

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private val AES_KEY = byteArrayOf(89,103,38,116,99,37,68,69,117,104,54,37,90,99,94,56)
    private val AES_IV = byteArrayOf(54,111,121,90,68,114,50,50,69,51,121,99,104,106,77,37)

    fun aesDecrypt(data: ByteArray): ByteArray {
        return try {
            if (data.isEmpty() || data.size % 16 != 0) return ByteArray(0)
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(AES_KEY, "AES"), IvParameterSpec(AES_IV))
            val dec = cipher.doFinal(data)
            pkcs7Unpad(dec)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val pad = data.last().toInt() and 0xFF
        if (pad in 1..16 && data.size >= pad) {
            var ok = true
            for (i in 1..pad) if ((data[data.size - i].toInt() and 0xFF) != pad) { ok = false; break }
            if (ok) return data.copyOf(data.size - pad)
        }
        return data
    }
}
