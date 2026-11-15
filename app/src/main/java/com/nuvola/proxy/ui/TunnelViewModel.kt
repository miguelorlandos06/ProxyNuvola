package com.nuvola.proxy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nuvola.proxy.data.ConfigStore
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
    private val configStore = ConfigStore(app)

    fun connect() {
        _state.value = _state.value.copy(status = TunnelStatus.Connecting)
        scope.launch {
            try {
                val cfg = configStore.load()
                val crypto = Crypto(cfg.psk, cfg.chan)
                val transport = Transport(cfg)
                val androidId = android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
                val devHash = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray()).take(2).joinToString("") { "%02x".format(it) }
                val r = Relay(cfg, crypto, transport, devHash)
                r.start(); relay = r
                _state.value = _state.value.copy(status = TunnelStatus.Connected(System.currentTimeMillis()))
                while (isActive) {
                    delay(1000)
                    _state.value = _state.value.copy(upBytes = r.statsUp, downBytes = r.statsDown, puts = r.statsPuts, gets = r.statsGets, conns = r.activeConnCount)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = TunnelStatus.Error(e.message ?: "Error"))
            }
        }
    }

    fun disconnect() { relay?.stop(); relay = null; _state.value = TunnelUiState() }
    override fun onCleared() { super.onCleared(); disconnect() }
}
