package com.nuvola.proxy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nuvola.proxy.data.ConfigStore
import com.nuvola.proxy.data.LogManager
import com.nuvola.proxy.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class TunnelViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TunnelUiState())
    val state: StateFlow<TunnelUiState> = _state.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var relay: Relay? = null
    private var monitorJob: Job? = null
    private var connecting = false
    private val configStore = ConfigStore(app)

    fun connect() {
        if (connecting || relay != null) return
        connecting = true
        _state.value = TunnelUiState(status = TunnelStatus.Connecting)
        monitorJob = scope.launch {
            try {
                val cfg = configStore.load()
                val crypto = Crypto(cfg.psk, cfg.chan)
                val transport = Transport(cfg)
                val androidId = android.provider.Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                val devHash = MessageDigest.getInstance("SHA-256")
                    .digest(androidId.toByteArray())
                    .take(4)
                    .joinToString("") { "%02x".format(it) }
                val r = Relay(cfg, crypto, transport, devHash)
                r.start(); relay = r
                _state.value = TunnelUiState(status = TunnelStatus.Connected(System.currentTimeMillis()))
                while (isActive) {
                    delay(1000)
                    _state.value = _state.value.copy(
                        upBytes = r.statsUp, downBytes = r.statsDown,
                        puts = r.statsPuts, gets = r.statsGets,
                        conns = r.activeConnCount
                    )
                }
            } catch (e: Exception) {
                LogManager.add("connect: ${e.message}")
                _state.value = TunnelUiState(status = TunnelStatus.Error(e.message ?: "Error"))
            } finally {
                connecting = false
            }
        }
    }

    fun disconnect() {
        monitorJob?.cancel(); monitorJob = null
        relay?.stop(); relay = null
        _state.value = TunnelUiState()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        scope.cancel()
    }
}
