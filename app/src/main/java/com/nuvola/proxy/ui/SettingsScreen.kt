package com.nuvola.proxy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nuvola.proxy.engine.Config

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(config: Config, onSave: (Config) -> Unit, onBack: () -> Unit) {
    var s3Base by remember { mutableStateOf(config.s3Base) }
    var psk by remember { mutableStateOf(config.psk) }
    var pollInterval by remember { mutableStateOf(config.pollIntervalMs.toString()) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Ajustes Go Protocol") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Conexión", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = s3Base, onValueChange = { s3Base = it }, label = { Text("S3 Base URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = psk, onValueChange = { psk = it }, label = { Text("PROXY_SHARED_SECRET (64 hex)") }, modifier = Modifier.fillMaxWidth())
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedTextField(value = pollInterval, onValueChange = { pollInterval = it }, label = { Text("POLL_INTERVAL (ms)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                onSave(config.copy(s3Base = s3Base, psk = psk, pollIntervalMs = pollInterval.toIntOrNull() ?: 500))
            }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Guardar Configuración")
            }
        }
    }
}
