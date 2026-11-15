package com.nuvola.proxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: TunnelUiState, onConnect: () -> Unit, onDisconnect: () -> Unit, onOpenSettings: () -> Unit) {
    val isConnected = state.status is TunnelStatus.Connected
    val orbColor = when (state.status) {
        is TunnelStatus.Connected -> Color(0xFF4CAF50)
        is TunnelStatus.Connecting -> Color(0xFFFF9800)
        else -> Color(0xFF9E9E9E)
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ProxyNuvola", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Ajustes") }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(250.dp).background(Brush.radialGradient(colors = listOf(orbColor.copy(alpha = 0.8f), orbColor.copy(alpha = 0.1f)), center = Offset.Unspecified), shape = CircleShape))
            Spacer(Modifier.height(32.dp))
            val title = when (state.status) {
                is TunnelStatus.Connected -> "Proxy Listo"
                is TunnelStatus.Connecting -> "Iniciando..."
                else -> "Inicia tu conexión"
            }
            Text(title, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Text(if (isConnected) "127.0.0.1:1080 | ↑ ${state.upBytes} B ↓ ${state.downBytes} B" else "Toca el botón para empezar", fontSize = 14.sp, color = Color.Gray)
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (isConnected) onDisconnect() else onConnect() }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp)) {
                Text(if (isConnected) "DETENER" else "INICIAR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
