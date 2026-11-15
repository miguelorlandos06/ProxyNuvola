package com.nuvola.proxy.engine

object Proto {
    const val T_OPEN = 1; const val T_DATA = 2; const val T_FIN = 3
    fun connKey(side: String, conn: String, seq: Long) = "$side/$conn/${seq.toString().padStart(12, '0')}"
    fun connIdOf(key: String): String {
        val parts = key.split("/")
        return if (parts.size >= 2) parts[parts.size - 2] else ""
    }
    fun packOpen(id: String, host: String, port: Int) = """{"id":"$id","host":"$host","port":$port}""".toByteArray()
    fun packFin(id: String, err: String = "") = """{"id":"$id","err":"$err"}""".toByteArray()
    fun unpackJson(body: ByteArray): Map<String, String> {
        val s = body.toString(Charsets.UTF_8)
        val out = HashMap<String, String>()
        Regex("\"(id|host|err|port)\":\"?([^\",}]+)\"?").findAll(s).forEach { out[it.groupValues[1]] = it.groupValues[2] }
        return out
    }
}
