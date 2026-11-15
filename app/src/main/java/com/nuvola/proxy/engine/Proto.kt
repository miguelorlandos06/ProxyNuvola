package com.nuvola.proxy.engine

object Proto {
    const val T_OPEN = 1; const val T_DATA = 2; const val T_FIN = 3

    // Formato de key (compatible con server.js):
    //   "<chan>/<side>/<connId>/<seq padded 12>"
    // El lado "c" es client→server, "s" es server→client.
    fun key(chan: String, side: String, conn: String, seq: Long) =
        "$chan/$side/$conn/${seq.toString().padStart(12, '0')}"

    fun connIdOf(key: String): String {
        // "<chan>/<side>/<connId>/<seq>" -> índice 2
        val parts = key.split("/")
        return if (parts.size >= 4) parts[2] else ""
    }

    fun packOpen(id: String, host: String, port: Int): ByteArray {
        val o = org.json.JSONObject()
        o.put("id", id); o.put("host", host); o.put("port", port)
        return o.toString().toByteArray(Charsets.UTF_8)
    }
    fun packFin(id: String, err: String = ""): ByteArray {
        val o = org.json.JSONObject()
        o.put("id", id); o.put("err", err)
        return o.toString().toByteArray(Charsets.UTF_8)
    }
    fun unpackJson(body: ByteArray): Map<String, String> {
        val s = body.toString(Charsets.UTF_8)
        val o = org.json.JSONObject(s)
        val m = HashMap<String, String>()
        o.keys().forEach { k -> m[k] = o.optString(k) }
        return m
    }
}
