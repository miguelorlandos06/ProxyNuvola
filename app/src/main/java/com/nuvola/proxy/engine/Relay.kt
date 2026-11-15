package com.nuvola.proxy.engine

import com.nuvola.proxy.data.LogManager
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class Relay(
    private val cfg: Config,
    private val crypto: Crypto,
    private val transport: Transport,
    private val deviceId: String
) {
    private val conns = ConcurrentHashMap<String, Conn>()
    val activeConnCount: Int get() = conns.size
    private val counter = AtomicLong(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var watermark: String? = null
    @Volatile private var running = false
    @Volatile private var server: ServerSocket? = null

    val statsUp get() = statsUpL.get(); val statsDown get() = statsDownL.get()
    val statsPuts get() = statsPutsL.get(); val statsGets get() = statsGetsL.get()
    private val statsUpL = AtomicLong(0)
    private val statsDownL = AtomicLong(0)
    private val statsPutsL = AtomicLong(0)
    private val statsGetsL = AtomicLong(0)

    fun start() {
        running = true
        LogManager.add("Relay iniciado en ${cfg.listen} (dev=$deviceId)")
        scope.launch { registerChannel() }
        scope.launch { pollLoop() }
        scope.launch { acceptLoop() }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        server = null
        scope.cancel()
        LogManager.add("Relay detenido")
    }

    private suspend fun registerChannel() {
        try {
            val json = """{"canales":["${cfg.chan}"]}"""
            transport.put("registro/activos.json", json.toByteArray())
            LogManager.add("Canal ${cfg.chan} registrado en registro/activos.json")
        } catch (e: Exception) {
            LogManager.add("Fallo registrando canal: ${e.message}")
        }
    }

    private suspend fun acceptLoop() {
        try {
            server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(cfg.listenPort))
            }
            val srv = server!!
            while (running) {
                val client = try { srv.accept() } catch (_: Exception) { if (running) continue else break }
                LogManager.add("SOCKS5 entrante desde ${client.inetAddress.hostAddress}")
                scope.launch { handleSocks(client) }
            }
        } catch (e: Exception) {
            LogManager.add("acceptLoop: ${e.message}")
        }
    }

    private suspend fun handleSocks(sock: Socket) {
        try {
            val ins = sock.getInputStream()
            if (ins.read() != 5) return
            val n = ins.read(); repeat(n) { ins.read() }
            sock.getOutputStream().write(byteArrayOf(5, 0))

            val hdr = ByteArray(4).also { readFully(ins, it) }
            if (hdr[1].toInt() != 1) {
                sock.getOutputStream().write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0))
                sock.close(); return
            }
            val host = when (hdr[3].toInt()) {
                1 -> { val a = ByteArray(4).also { readFully(ins, it) };
                    "${a[0].toInt() and 0xFF}.${a[1].toInt() and 0xFF}.${a[2].toInt() and 0xFF}.${a[3].toInt() and 0xFF}" }
                4 -> { val a = ByteArray(16).also { readFully(ins, it) };
                    (0 until 8).joinToString(":") { i -> "%02x%02x".format(a[i*2], a[i*2+1]) } }
                else -> { val len = ins.read(); String(ByteArray(len).also { readFully(ins, it) }) }
            }
            val port = ((ins.read() and 0xFF) shl 8) or (ins.read() and 0xFF)
            LogManager.add("Destino: $host:$port")

            val connId = (counter.incrementAndGet() and 0xFFFF).toString(16).padStart(4, '0') +
                         (0..0xFFFF).random().toString(16).padStart(4, '0')
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
                statsUpL.addAndGet(n.toLong())
            }
        } catch (_: Exception) {} finally {
            send(conn, Proto.T_FIN, Compress.compress(Proto.packFin(conn.id)))
            LogManager.add("Conexión ${conn.id} cerrada")
        }
    }

    // Keys compatibles con server.js:
    // - app escribe en "<chan>/c/<connId>/<seq>"
    // - server lee de "<chan>/c/" y usa k.split("/")[2] como connId
    // - server escribe en "<chan>/s/<connId>/<seq>"
    // - app lista "<chan>/s/" y extrae connId con Proto.connIdOf(k)
    private fun upKey(id: String, seq: Long) =
        "${cfg.chan}/c/$id/${seq.toString().padStart(12, '0')}"
    private fun downPrefix() = "${cfg.chan}/s/"

    private suspend fun send(conn: Conn, type: Int, body: ByteArray) {
        val seq = conn.seq++
        val key = upKey(conn.id, seq)
        val blob = crypto.seal(seq, key, type, body)
        try {
            transport.put(key, blob); statsPutsL.incrementAndGet()
        } catch (e: Exception) {
            LogManager.add("Fallo PUT a S3: ${e.message}")
        }
    }

    private suspend fun pollLoop() {
        var backoff = 0L
        while (running) {
            try {
                val keys = transport.list(downPrefix(), watermark)
                for (k in keys) {
                    if (!running) break
                    val blob = transport.get(k)
                    if (blob == null) { watermark = k; continue }
                    val (type, body) = crypto.unseal(blob, k)
                    val raw = Compress.decompress(body)
                    val connId = Proto.connIdOf(k)
                    when (type) {
                        Proto.T_DATA -> conns[connId]?.sock?.getOutputStream()?.write(raw)
                        Proto.T_FIN  -> conns.remove(connId)?.sock?.close()
                    }
                    transport.delete(k)
                    watermark = k
                    statsGetsL.incrementAndGet()
                }
                backoff = 0
            } catch (e: Exception) {
                backoff = (backoff * 2).coerceAtMost(5000)
                LogManager.add("pollLoop: ${e.message} (retry ${backoff}ms)")
            }
            delay((cfg.pollIntervalMs + backoff).toLong())
        }
    }

    private fun readFully(ins: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = ins.read(buf, off, buf.size - off)
            if (n <= 0) throw Exception("EOF")
            off += n
        }
    }

    class Conn(val id: String, val sock: Socket) { var seq = 0L }
}
