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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSessions = ConcurrentHashMap<String, Session>()
    
    // Stats como propiedades atómicas
    val statsUp get() = statsUpL.get(); val statsDown get() = statsDownL.get()
    val statsPuts get() = 0L; val statsGets get() = 0L // No usados en esta versión
    val activeConnCount get() = activeSessions.size
    private val statsUpL = AtomicLong(0)
    private val statsDownL = AtomicLong(0)

    @Volatile private var running = false
    @Volatile private var server: ServerSocket? = null

    fun start() {
        running = true
        LogManager.add("Relay Go-Protocol iniciado en ${cfg.listen}")
        scope.launch { acceptLoop() }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        scope.cancel()
        LogManager.add("Relay detenido")
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

            val sessionId = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            LogManager.add("Nueva sesión Go $sessionId -> $host:$port")

            sock.getOutputStream().write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            
            // 1. Enviar target JSON
            val targetJson = """{"version":2,"mode":"tcp","host":"$host","port":$port}"""
            val targetKey = Protocol.targetKey(sessionId)
            transport.put(targetKey, crypto.seal(targetKey, targetJson.toByteArray()))

            // 2. Leer cola actual, añadir sesión y reescribir
            val queueKey = Protocol.queueKey(0)
            val queueRaw = transport.get(queueKey)
            val queueTxt = if (queueRaw != null) String(crypto.open(queueKey, queueRaw) ?: byteArrayOf(), Charsets.UTF_8) else ""
            val newQueue = (queueTxt + "\n" + sessionId).trim()
            transport.put(queueKey, crypto.seal(queueKey, newQueue.toByteArray()))

            val session = Session(sessionId, sock)
            activeSessions[sessionId] = session
            scope.launch { sessionLoop(session) }

        } catch (e: Exception) {
            LogManager.add("Error SOCKS5: ${e.message}")
        }
    }

    private suspend fun sessionLoop(session: Session) {
        val uplinkJob = scope.launch { uplink(session) }
        downlink(session)
        uplinkJob.cancel()
    }

    private suspend fun uplink(session: Session) {
        val buf = ByteArray(cfg.chunkSize)
        var seq = 0L
        try {
            while (running) {
                val n = session.sock.getInputStream().read(buf)
                if (n <= 0) break
                val frame = Protocol.encodeFrame(seq, buf.copyOf(n))
                val key = Protocol.dataKey("c2s", session.id, seq)
                transport.put(key, crypto.seal(key, frame))
                seq++
                statsUpL.addAndGet(n.toLong())
            }
        } catch (_: Exception) {} finally {
            val finKey = Protocol.finKey("c2s", session.id)
            transport.put(finKey, crypto.seal(finKey, Protocol.encodeCounter(seq)))
        }
    }

    private suspend fun downlink(session: Session) {
        var seq = 0L
        var lastAck = 0L
        var sinceAck = 0
        while (running && activeSessions.containsKey(session.id)) {
            val finKey = Protocol.finKey("s2c", session.id)
            val finRaw = transport.get(finKey)
            if (finRaw != null) {
                val finSeq = Protocol.decodeCounter(crypto.open(finKey, finRaw))
                if (seq >= finSeq) break
            }
            
            val key = Protocol.dataKey("s2c", session.id, seq)
            val dataRaw = transport.get(key)
            if (dataRaw != null) {
                val decrypted = crypto.open(key, dataRaw)
                if (decrypted != null) {
                    val payload = Protocol.decodeFrame(decrypted, seq)
                    if (payload != null) {
                        session.sock.getOutputStream().write(payload)
                        statsDownL.addAndGet(payload.size.toLong())
                    }
                }
                seq++
                sinceAck++
                
                if (sinceAck >= 4) { // ACK_EVERY
                    val ackKey = Protocol.ackKey("c2s", session.id)
                    transport.put(ackKey, crypto.seal(ackKey, Protocol.encodeCounter(seq)))
                    lastAck = seq
                    sinceAck = 0
                }
                continue
            }
            
            delay(cfg.pollIntervalMs.toLong())
        }
        activeSessions.remove(session.id)
        try { session.sock.close() } catch (_: Exception) {}
        LogManager.add("Sesión ${session.id} cerrada")
    }

    private fun readFully(ins: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = ins.read(buf, off, buf.size - off)
            if (n <= 0) throw Exception("EOF")
            off += n
        }
    }

    class Session(val id: String, val sock: Socket)
}
