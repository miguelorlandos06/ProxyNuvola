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
    var bucket by remember { mutableStateOf(config.bucket) }
    var chan by remember { mutableStateOf(config.chan) }
    var psk by remember { mutableStateOf(config.psk) }
    var chunkSize by remember { mutableStateOf(config.chunkSize.toString()) }
    var windowSize by remember { mutableStateOf(config.windowSize.toString()) }
    var pollInterval by remember { mutableStateOf(config.pollIntervalMs.toString()) }
    var httpTimeout by remember { mutableStateOf(config.httpTimeout.toString()) }
    var httpRetries by remember { mutableStateOf(config.httpRetries.toString()) }
    var connectTimeout by remember { mutableStateOf(config.connectTimeout.toString()) }
    var maxSessions by remember { mutableStateOf(config.maxSessions.toString()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes Avanzados") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Conexión S3", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = s3Base, onValueChange = { s3Base = it }, label = { Text("S3 Base URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bucket, onValueChange = { bucket = it }, label = { Text("Bucket") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = chan, onValueChange = { chan = it }, label = { Text("Canal (16 hex)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = psk, onValueChange = { psk = it }, label = { Text("PSK") }, modifier = Modifier.fillMaxWidth())
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Rendimiento", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = chunkSize, onValueChange = { chunkSize = it }, label = { Text("CHUNK_SIZE (bytes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = windowSize, onValueChange = { windowSize = it }, label = { Text("WINDOW_SIZE") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = pollInterval, onValueChange = { pollInterval = it }, label = { Text("POLL_INTERVAL (ms)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Timeouts y Red", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = httpTimeout, onValueChange = { httpTimeout = it }, label = { Text("HTTP_TIMEOUT (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = httpRetries, onValueChange = { httpRetries = it }, label = { Text("HTTP_RETRIES") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = connectTimeout, onValueChange = { connectTimeout = it }, label = { Text("CONNECT_TIMEOUT (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = maxSessions, onValueChange = { maxSessions = it }, label = { Text("MAX_SESSIONS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(Config(s3Base=s3Base, bucket=bucket, chan=chan, psk=psk, chunkSize=chunkSize.toIntOrNull()?:262144, windowSize=windowSize.toIntOrNull()?:8, pollIntervalMs=pollInterval.toIntOrNull()?:50, httpTimeout=httpTimeout.toIntOrNull()?:20, httpRetries=httpRetries.toIntOrNull()?:3, connectTimeout=connectTimeout.toIntOrNull()?:30, maxSessions=maxSessions.toIntOrNull()?:64)) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Guardar Configuración") }
        }
    }
}
