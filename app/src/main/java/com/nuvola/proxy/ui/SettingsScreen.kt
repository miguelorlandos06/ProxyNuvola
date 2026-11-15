package com.nuvola.proxy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nuvola.proxy.engine.Config
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(config: Config, onSave: (Config) -> Unit, onBack: () -> Unit) {
    var s3Base by remember { mutableStateOf(config.s3Base) }
    var bucket by remember { mutableStateOf(config.bucket) }
    var chan by remember { mutableStateOf(config.chan) }
    var psk by remember { mutableStateOf(config.psk) }
    var chunkSize by remember { mutableStateOf(config.chunkSize.toString()) }
    var pollInterval by remember { mutableStateOf(config.pollIntervalMs.toString()) }
    var httpTimeout by remember { mutableStateOf(config.httpTimeout.toString()) }

    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    if (showExport) JsonDialog(config = config, isExport = true, onDismiss = { showExport = false }, onImport = {})
    if (showImport) JsonDialog(config = config, isExport = false,
        onDismiss = { showImport = false },
        onImport = { json ->
            try {
                val o = JSONObject(json)
                onSave(config.copy(
                    s3Base = o.optString("s3Base", config.s3Base),
                    bucket = o.optString("bucket", config.bucket),
                    chan = o.optString("chan", config.chan),
                    psk = o.optString("psk", config.psk),
                    chunkSize = o.optInt("chunkSize", config.chunkSize),
                    windowSize = o.optInt("windowSize", config.windowSize),
                    ackEvery = o.optInt("ackEvery", config.ackEvery),
                    pollIntervalMs = o.optInt("pollIntervalMs", config.pollIntervalMs),
                    httpTimeout = o.optInt("httpTimeout", config.httpTimeout),
                    httpRetries = o.optInt("httpRetries", config.httpRetries),
                    connectTimeout = o.optInt("connectTimeout", config.connectTimeout),
                    maxSessions = o.optInt("maxSessions", config.maxSessions)
                ))
            } catch (_: Exception) {}
            showImport = false
        })

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Ajustes Avanzados") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } },
            actions = {
                IconButton(onClick = { showExport = true }) { Icon(Icons.Default.Upload, contentDescription = "Exportar") }
                IconButton(onClick = { showImport = true }) { Icon(Icons.Default.Download, contentDescription = "Importar") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Conexión S3", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = s3Base, onValueChange = { s3Base = it }, label = { Text("S3 Base URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bucket, onValueChange = { bucket = it }, label = { Text("Bucket") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = chan, onValueChange = { chan = it }, label = { Text("Canal (16 hex)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = psk, onValueChange = { psk = it }, label = { Text("PSK") }, modifier = Modifier.fillMaxWidth())

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Rendimiento", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = chunkSize, onValueChange = { chunkSize = it }, label = { Text("CHUNK_SIZE (bytes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = pollInterval, onValueChange = { pollInterval = it }, label = { Text("POLL_INTERVAL (ms)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Timeouts", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = httpTimeout, onValueChange = { httpTimeout = it }, label = { Text("HTTP_TIMEOUT (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                onSave(config.copy(
                    s3Base = s3Base, bucket = bucket, chan = chan, psk = psk,
                    chunkSize = chunkSize.toIntOrNull() ?: config.chunkSize,
                    pollIntervalMs = pollInterval.toIntOrNull() ?: config.pollIntervalMs,
                    httpTimeout = httpTimeout.toIntOrNull() ?: config.httpTimeout
                ))
            }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Guardar Configuración")
            }
        }
    }
}
