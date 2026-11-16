package com.nuvola.proxy.engine

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Crypto(private val secretHex: String) {
    private val secretBytes: ByteArray = hexToBytes(secretHex)

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    fun seal(key: String, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        mac.update(key.toByteArray(StandardCharsets.UTF_8))
        mac.update(0)
        mac.update(data)
        val tag = mac.doFinal()

        val out = ByteArray(4 + Protocol.TAG_SIZE + data.size)
        System.arraycopy(Protocol.ENVELOPE_MAGIC, 0, out, 0, 4)
        System.arraycopy(tag, 0, out, 4, Protocol.TAG_SIZE)
        System.arraycopy(data, 0, out, 4 + Protocol.TAG_SIZE, data.size)
        return out
    }

    fun open(key: String, envelope: ByteArray): ByteArray? {
        if (envelope.size < 4 + Protocol.TAG_SIZE) return null
        if (!envelope.copyOfRange(0, 4).contentEquals(Protocol.ENVELOPE_MAGIC)) return null
        
        val tag = envelope.copyOfRange(4, 4 + Protocol.TAG_SIZE)
        val data = envelope.copyOfRange(4 + Protocol.TAG_SIZE, envelope.size)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        mac.update(key.toByteArray(StandardCharsets.UTF_8))
        mac.update(0)
        mac.update(data)
        val expected = mac.doFinal()

        return if (java.security.MessageDigest.isEqual(tag, expected)) data else null
    }
}
