package com.nuvola.proxy.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.nuvola.proxy.data.LogManager
import com.nuvola.proxy.service.RelayService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TunnelViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TunnelUiState())
    val state: StateFlow<TunnelUiState> = _state.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var connecting = false

    fun connect() {
        if (connecting || RelayService.isRunning) return
        connecting = true
        _state.value = TunnelUiState(status = TunnelStatus.Connecting)
        val ctx = getApplication<Application>()
        val i = Intent(ctx, RelayService::class.java).setAction(RelayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)

        monitorJob = scope.launch {
            try {
                var waited = 0
                while (isActive && !RelayService.isRunning && RelayService.lastError == null && waited < 150) {
                    delay(100); waited++
                }
                val err = RelayService.lastError
                if (err != null) {
                    LogManager.add("connect: $err")
                    _state.value = TunnelUiState(status = TunnelStatus.Error(err))
                    return@launch
                }
                if (!RelayService.isRunning) {
                    _state.value = TunnelUiState(status = TunnelStatus.Error("Timeout arrancando servicio"))
                    return@launch
                }
                _state.value = TunnelUiState(status = TunnelStatus.Connected(System.currentTimeMillis()))
                while (isActive) {
                    delay(1000)
                    val r = RelayService.lastRelay
                    if (r == null) {
                        _state.value = TunnelUiState()
                        break
                    }
                    _state.value = _state.value.copy(
                        upBytes = r.statsUp, downBytes = r.statsDown,
                        puts = r.statsPuts, gets = r.statsGets,
                        conns = r.activeConnCount
                    )
                }
            } finally {
                connecting = false
            }
        }
    }

    fun disconnect() {
        monitorJob?.cancel(); monitorJob = null
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, RelayService::class.java).setAction(RelayService.ACTION_STOP))
        _state.value = TunnelUiState()
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel(); monitorJob = null
        scope.cancel()
    }
}
