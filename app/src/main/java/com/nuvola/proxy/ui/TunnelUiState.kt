package com.nuvola.proxy.ui

sealed interface TunnelStatus {
    data object Disconnected : TunnelStatus
    data object Connecting : TunnelStatus
    data class Connected(val since: Long) : TunnelStatus
    data class Error(val msg: String) : TunnelStatus
}

data class TunnelUiState(
    val status: TunnelStatus = TunnelStatus.Disconnected,
    val upBytes: Long = 0,
    val downBytes: Long = 0,
    val conns: Int = 0,
    val puts: Long = 0,
    val gets: Long = 0
)
