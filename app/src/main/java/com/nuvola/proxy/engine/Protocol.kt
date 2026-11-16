package com.nuvola.proxy.engine

import java.nio.ByteBuffer
import java.util.zip.CRC32

object Protocol {
    val FRAME_MAGIC = byteArrayOf('T'.code.toByte(), 'D'.code.toByte(), 'V'.code.toByte(), '2'.code.toByte())
    val ENVELOPE_MAGIC = byteArrayOf('T'.code.toByte(), 'D'.code.toByte(), 'S'.code.toByte(), '2'.code.toByte())
    const val FRAME_VERSION: Byte = 2

    const val FRAME_HEADER_SIZE = 21
    const val TAG_SIZE = 32

    fun encodeFrame(seq: Long, payload: ByteArray): ByteArray {
        val out = ByteArray(FRAME_HEADER_SIZE + payload.size)
        System.arraycopy(FRAME_MAGIC, 0, out, 0, 4)
        out[4] = FRAME_VERSION
        ByteBuffer.wrap(out, 5, 8).putLong(seq)
        ByteBuffer.wrap(out, 13, 4).putInt(payload.size)
        val crc = CRC32()
        crc.update(payload)
        ByteBuffer.wrap(out, 17, 4).putInt(crc.value.toInt())
        System.arraycopy(payload, 0, out, FRAME_HEADER_SIZE, payload.size)
        return out
    }

    fun decodeFrame(raw: ByteArray, expectedSeq: Long): ByteArray? {
        if (raw.size < FRAME_HEADER_SIZE) return null
        if (!raw.copyOfRange(0, 4).contentEquals(FRAME_MAGIC) || raw[4] != FRAME_VERSION) return null
        
        val seq = ByteBuffer.wrap(raw, 5, 8).long
        if (seq != expectedSeq) return null
        
        val length = ByteBuffer.wrap(raw, 13, 4).int
        val checksum = ByteBuffer.wrap(raw, 17, 4).int
        val payload = raw.copyOfRange(FRAME_HEADER_SIZE, raw.size)
        
        if (payload.size != length) return null
        val crc = CRC32()
        crc.update(payload)
        if (crc.value.toInt() != checksum) return null
        return payload
    }

    fun encodeCounter(value: Long): ByteArray = value.toString().toByteArray(Charsets.UTF_8)
    
    fun decodeCounter(raw: ByteArray?): Long {
        if (raw == null || raw.isEmpty()) return 0L
        return String(raw, Charsets.UTF_8).trim().toLongOrNull() ?: 0L
    }

    // Keys matching Go server
    fun queueKey(slot: Int): String = if (slot <= 0) "_v2_queue.txt" else "_v2_queue_$slot.txt"
    fun targetKey(id: String): String = "_v2_target_$id.json"
    fun statusKey(id: String): String = "_v2_status_$id.json"
    fun closeKey(id: String): String = "_v2_close_$id.txt"
    fun dataKey(dir: String, id: String, seq: Long): String = String.format("_v2_%s_%s_%016d.bin", dir, id, seq)
    fun udpDataKey(dir: String, id: String, seq: Long): String = String.format("_v2_udp_%s_%s_%016d.bin", dir, id, seq)
    fun ackKey(dir: String, id: String): String = "_v2_ack_${dir}_$id.txt"
    fun finKey(dir: String, id: String): String = "_v2_fin_${dir}_$id.txt"
}
