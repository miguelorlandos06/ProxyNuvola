package com.nuvola.proxy.engine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class Transport(private val cfg: Config) {
    private val base = cfg.s3Base.trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(cfg.httpTimeout.toLong(), TimeUnit.SECONDS)
        .readTimeout(cfg.httpTimeout.toLong(), TimeUnit.SECONDS).build()

    fun put(key: String, data: ByteArray) {
        val req = Request.Builder().url("$base/${cfg.bucket}/$key").put(RequestBody.create(null, data)).build()
        client.newCall(req).execute().use { if (!it.isSuccessful) throw IOException("PUT ${it.code}") }
    }
    fun get(key: String): ByteArray? {
        val req = Request.Builder().url("$base/${cfg.bucket}/$key").build()
        client.newCall(req).execute().use {
            if (it.code == 200) return it.body?.bytes()
            if (it.code == 404) return null
            throw IOException("GET ${it.code}")
        }
    }
    fun list(prefix: String, startAfter: String?): List<String> {
        val sb = StringBuilder("?list-type=2&prefix=$prefix")
        if (startAfter != null) sb.append("&start-after=$startAfter")
        val req = Request.Builder().url("$base/${cfg.bucket}/$sb").build()
        client.newCall(req).execute().use { resp ->
            val xml = resp.body?.string() ?: ""
            return Regex("<Key>(.*?)</Key>").findAll(xml).map { it.groupValues[1] }.toList()
        }
    }
    fun delete(key: String) {
        val req = Request.Builder().url("$base/${cfg.bucket}/$key").delete().build()
        client.newCall(req).execute().close()
    }
}
