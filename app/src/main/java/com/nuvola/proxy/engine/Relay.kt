package com.nuvola.proxy.engine

import android.util.Log
import com.nuvola.proxy.data.LogManager
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class Relay(private val cfg: Config, private val crypto: Crypto, private val transport: Transport, private val deviceId: String) {
    companion object { private const val TAG = "Relay" }
    private val conns = ConcurrentHashMap<String, Conn>()
    private val counter = AtomicLong(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var watermark: String? = null
    @Volatile private var running = false
    var statsUp = 0L; var statsDown = 0L; var statsPuts = 0L; var statsGets = 0L

    fun start() {
        running = true
        LogManager.add("Relay iniciado en ${cfg.listen}")
        scope.launch { pollLoop() }
        scope.launch { acceptLoop() }
    }

    fun stop() {
        running = false; scope.cancel()
        LogManager.add("Relay detenido")
    }

    private suspend fun acceptLoop() {
        val server = ServerSocket(cfg.listenPort)
        while (running) {
            val client = server.accept()
            LogManager.add("Conexión SOCKS5 entrante desde ${client.inetAddress.hostAddress}")
            scope.launch { handleSocks(client) }
        }
    }

    private suspend fun handleSocks(sock: Socket) {
        try {
            val ins = sock.getInputStream()
            if (ins.read() != 5) return
            val n = ins.read(); repeat(n) { ins.read() }
            sock.getOutputStream().write(byteArrayOf(5, 0))
            
            val hdr = ByteArray(4).also { readFully(ins, it) }
            val host = if (hdr[3].toInt() == 1) {
                val a = ByteArray(4).also { readFully(ins, it) }
                "${a[0].toInt() and 0xFF}.${a[1].toInt() and 0xFF}.${a[2].toInt() and 0xFF}.${a[3].toInt() and 0xFF}"
            } else {
                val len = ins.read(); String(ByteArray(len).also { readFully(ins, it) })
            }
            val port = ((ins.read() and 0xFF) shl 8) or (ins.read() and 0xFF)
            LogManager.add("Destino: $host:$port")

            val connId = (counter.incrementAndGet() and 0xFFFF).toString(16).padStart(4, '0') + (0..0xFFFF).random().toString(16).padStart(4, '0')
            val conn = Conn(connId, sock)
            conns[connId] = conn
            sock.getOutputStream().write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            send(conn, Proto.T_OPEN, Compress.compress(Proto.packOpen(connId, host, port)))
            scope.launch { readLocal(conn) }
        } catch (e: Exception) { 
            LogManager.add("Error SOCKS5: ${e.message}")
        }
    }

    private suspend fun readLocal(conn: Conn) {
        val buf = ByteArray(cfg.chunkSize)
        try {
            while (running) {
                val n = conn.sock.getInputStream().read(buf)
                if (n <= 0) break
                send(conn, Proto.T_DATA, Compress.compress(buf.copyOf(n)))
                statsUp += n
            }
        } catch (_: Exception) {} finally { 
            send(conn, Proto.T_FIN, Compress.compress(Proto.packFin(conn.id)))
            LogManager.add("Conexión ${conn.id} cerrada")
        }
    }

    private suspend fun send(conn: Conn, type: Int, body: ByteArray) {
        val seq = conn.seq++
        val key = Proto.connKey("c", conn.id, seq)
        val blob = crypto.seal(seq, key, type, body)
        try {
            transport.put(key, blob); statsPuts++
        } catch (e: Exception) {
            LogManager.add("Fallo PUT a S3: ${e.message}")
        }
    }

    private suspend fun pollLoop() {
        while (running) {
            try {
                val keys = transport.list("${cfg.chan}/s/", watermark)
                for (k in keys) {
                    val blob = transport.get(k) ?: continue
                    val (type, body) = crypto.unseal(blob, k)
                    val raw = Compress.decompress(body)
                    val connId = Proto.connIdOf(k)
                    if (type == Proto.T_DATA) conns[connId]?.sock?.getOutputStream()?.write(raw)
                    if (type == Proto.T_FIN) conns.remove(connId)?.sock?.close()
                    transport.delete(k); watermark = k; statsGets++
                }
            } catch (_: Exception) {}
            delay(cfg.pollIntervalMs.toLong())
        }
    }

    private fun readFully(ins: InputStream, buf: ByteArray) {
        var off = 0; while (off < buf.size) { val n = ins.read(buf, off, buf.size - off); if (n <= 0) throw Exception("EOF"); off += n }
    }
    class Conn(val id: String, val sock: Socket) { var seq = 0L }
}
