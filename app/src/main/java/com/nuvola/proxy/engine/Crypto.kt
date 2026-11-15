package com.nuvola.proxy.engine

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Crypto(psk: String, chan: String) {
    companion object {
        val MAGIC = byteArrayOf('T'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())
        const val NONCE_LEN = 12
        const val TAG_LEN = 16
        const val HDR_LEN = 3 + NONCE_LEN
        const val MIN_BLOB = HDR_LEN + TAG_LEN + 1

        fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
            val prk = mac.doFinal(ikm)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = ByteArray(length)
            var t = ByteArray(0); var pos = 0; var counter = 1
            while (pos < length) {
                mac.reset(); mac.update(t); mac.update(info); mac.update(counter.toByte())
                t = mac.doFinal()
                val toCopy = minOf(t.size, length - pos)
                System.arraycopy(t, 0, out, pos, toCopy); pos += toCopy; counter++
            }
            return out
        }
    }

    private val key: ByteArray
    private val aes: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val random = SecureRandom()

    init {
        val salt = ("todus-relay|" + chan).toByteArray(Charsets.US_ASCII)
        key = hkdfSha256(psk.toByteArray(), salt, "todus-relay/v1/aesgcm".toByteArray(), 32)
    }

    fun seal(seq: Long, path: String, mtype: Int, body: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LEN)
        val rnd = ByteArray(4); random.nextBytes(rnd)
        System.arraycopy(rnd, 0, nonce, 0, 4)
        ByteBuffer.wrap(nonce, 4, 8).putLong(seq)
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN * 8, nonce))
        aes.updateAAD(path.toByteArray())
        val pt = ByteArray(1 + body.size)
        pt[0] = (mtype and 0xFF).toByte()
        System.arraycopy(body, 0, pt, 1, body.size)
        val ct = aes.doFinal(pt)
        val out = ByteArray(HDR_LEN + ct.size)
        System.arraycopy(MAGIC, 0, out, 0, 3)
        System.arraycopy(nonce, 0, out, 3, NONCE_LEN)
        System.arraycopy(ct, 0, out, HDR_LEN, ct.size)
        return out
    }

    fun unseal(blob: ByteArray, path: String): Pair<Int, ByteArray> {
        if (blob.size < MIN_BLOB || !blob.copyOfRange(0,3).contentEquals(MAGIC)) throw Exception("blob invalido")
        val nonce = blob.copyOfRange(3, HDR_LEN)
        val ct = blob.copyOfRange(HDR_LEN, blob.size)
        aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN * 8, nonce))
        aes.updateAAD(path.toByteArray())
        val pt = aes.doFinal(ct)
        return (pt[0].toInt() and 0xFF) to pt.copyOfRange(1, pt.size)
    }
}
