package com.nuvola.proxy.data

import android.content.Context
import android.content.SharedPreferences
import com.nuvola.proxy.engine.Config

class ConfigStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("proxy_prefs", Context.MODE_PRIVATE)
    fun load(): Config = Config(
        s3Base = prefs.getString("s3_base", "https://s3.todus.cu/public/dk/proxy_lab") ?: "",
        psk = prefs.getString("psk", "REEMPLAZAR_POR_64_CARACTERES_HEX") ?: "",
        pollIntervalMs = prefs.getInt("poll_interval", 500)
    )
    fun save(c: Config) = prefs.edit().apply {
        putString("s3_base", c.s3Base)
        putString("psk", c.psk)
        putInt("poll_interval", c.pollIntervalMs)
    }.apply()
}
