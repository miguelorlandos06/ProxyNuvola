package com.nuvola.proxy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nuvola.proxy.MainActivity
import com.nuvola.proxy.R
import com.nuvola.proxy.data.ConfigStore
import com.nuvola.proxy.data.LogManager
import com.nuvola.proxy.engine.Crypto
import com.nuvola.proxy.engine.Relay
import com.nuvola.proxy.engine.Transport
import kotlinx.coroutines.*
import java.security.MessageDigest

class RelayService : Service() {
    companion object {
        const val CHANNEL_ID = "proxy_nuvola"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.nuvola.proxy.START"
        const val ACTION_STOP  = "com.nuvola.proxy.STOP"

        @Volatile var isRunning = false
            private set
        @Volatile var lastError: String? = null
            private set
        @Volatile var lastRelay: Relay? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var relay: Relay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRelay(); return START_NOT_STICKY }
            else -> startRelay()
        }
        return START_STICKY
    }

    private fun startRelay() {
        if (relay != null) return
        startForegroundCompat(buildNotification("Conectando…"))
        scope.launch {
            try {
                val cfg = ConfigStore(applicationContext).load()
                val crypto = Crypto(cfg.psk, cfg.chan)
                val transport = Transport(cfg)
                val androidId = android.provider.Settings.Secure.getString(
                    contentResolver, android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                val devHash = MessageDigest.getInstance("SHA-256")
                    .digest(androidId.toByteArray()).take(4)
                    .joinToString("") { "%02x".format(it) }
                val r = Relay(cfg, crypto, transport, devHash)
                r.start()
                relay = r; lastRelay = r; isRunning = true; lastError = null
                updateNotification("Relay activo en ${cfg.listen}")
                LogManager.add("Servicio relay iniciado")
            } catch (e: Exception) {
                lastError = e.message
                LogManager.add("Servicio relay error: ${e.message}")
                updateNotification("Error: ${e.message}")
                stopRelay()
            }
        }
    }

    private fun stopRelay() {
        scope.launch {
            try { relay?.stop() } catch (_: Exception) {}
            relay = null; lastRelay = null; isRunning = false
            LogManager.add("Servicio relay detenido")
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        try { relay?.stop() } catch (_: Exception) {}
        relay = null; lastRelay = null; isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, "ProxyNuvola", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Estado del relay SOCKS5"
                setShowBadge(false)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RelayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ProxyNuvola")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Detener", stop)
            .build()
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(text))
    }
}
