package com.nuvola.proxy.engine
import java.util.zip.Deflater
import java.util.zip.Inflater

object Compress {
    fun compress(body: ByteArray): ByteArray {
        if (body.size < 256) return byteArrayOf(0x00) + body
        val deflater = Deflater(6)
        try {
            deflater.setInput(body); deflater.finish()
            val buf = ByteArray(body.size + 64)
            val n = deflater.deflate(buf)
            if (n <= 0 || n >= body.size) return byteArrayOf(0x00) + body
            return byteArrayOf(0x01) + buf.copyOf(n)
        } finally { deflater.end() }
    }
    fun decompress(blob: ByteArray): ByteArray {
        if (blob.isEmpty()) return blob
        val flag = blob[0].toInt() and 0xFF
        if (flag == 0x00) return blob.copyOfRange(1, blob.size)
        if (flag == 0x01) {
            val inflater = Inflater()
            try {
                inflater.setInput(blob, 1, blob.size - 1)
                val out = ByteArray(16 * 1024 * 1024); val n = inflater.inflate(out)
                return out.copyOf(n)
            } finally { inflater.end() }
        }
        throw Exception("flag de compresion desconocido")
    }
}
