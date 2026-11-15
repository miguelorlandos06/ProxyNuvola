package com.nuvola.proxy.engine

data class Config(
    val s3Base: String = "https://s3.todus.cu",
    val bucket: String = "stickers",
    val chan: String = "eded311cd9ad19d0",
    val psk: String = "YyHO6lYLUpfUDaTClxYe2cTmEKv1Cd3vX-B1oFFf8wU",
    val listen: String = "127.0.0.1:1080",
    val chunkSize: Int = 262144,
    val windowSize: Int = 8,
    val ackEvery: Int = 4,
    val pollIntervalMs: Int = 50,
    val httpTimeout: Int = 20,
    val httpRetries: Int = 3,
    val connectTimeout: Int = 30,
    val maxSessions: Int = 64
) {
    val listenPort: Int get() = listen.substringAfterLast(':').toIntOrNull() ?: 1080
}
