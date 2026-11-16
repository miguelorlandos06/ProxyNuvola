package com.nuvola.proxy.engine

data class Config(
    val s3Base: String = "https://s3.todus.cu/public/dk/proxy_lab",
    val psk: String = "REEMPLAZAR_POR_64_CARACTERES_HEX",
    val listen: String = "127.0.0.1:1080",
    val chunkSize: Int = 262144,
    val pollIntervalMs: Int = 500,
    val httpTimeout: Int = 20
) {
    val listenPort: Int get() = listen.substringAfterLast(':').toIntOrNull() ?: 1080
}
