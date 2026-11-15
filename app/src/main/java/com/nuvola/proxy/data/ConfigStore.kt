package com.nuvola.proxy.data

import android.content.Context
import android.content.SharedPreferences
import com.nuvola.proxy.engine.Config

class ConfigStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("proxy_prefs", Context.MODE_PRIVATE)
    fun load(): Config = Config(
        s3Base = prefs.getString("s3_base", "https://s3.todus.cu") ?: "",
        bucket = prefs.getString("bucket", "stream") ?: "",
        chan = prefs.getString("chan", "eded311cd9ad19d0") ?: "",
        psk = prefs.getString("psk", "YyHO6lYLUpfUDaTClxYe2cTmEKv1Cd3vX-B1oFFf8wU") ?: "",
        chunkSize = prefs.getInt("chunk_size", 262144),
        windowSize = prefs.getInt("window_size", 8),
        ackEvery = prefs.getInt("ack_every", 4),
        pollIntervalMs = prefs.getInt("poll_interval", 50),
        httpTimeout = prefs.getInt("http_timeout", 20),
        httpRetries = prefs.getInt("http_retries", 3),
        connectTimeout = prefs.getInt("connect_timeout", 30),
        maxSessions = prefs.getInt("max_sessions", 64)
    )
    fun save(c: Config) = prefs.edit().apply {
        putString("s3_base", c.s3Base); putString("bucket", c.bucket)
        putString("chan", c.chan); putString("psk", c.psk)
        putInt("chunk_size", c.chunkSize); putInt("window_size", c.windowSize)
        putInt("ack_every", c.ackEvery); putInt("poll_interval", c.pollIntervalMs)
        putInt("http_timeout", c.httpTimeout); putInt("http_retries", c.httpRetries)
        putInt("connect_timeout", c.connectTimeout); putInt("max_sessions", c.maxSessions)
    }.apply()
}
