package com.nuvola.proxy.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nuvola.proxy.engine.Config

@Composable
fun JsonDialog(
    config: Config,
    isExport: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    val context = LocalContext.current
    var text by remember { 
        mutableStateOf(if (isExport) configToJson(config) else "") 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExport) "Exportar Configuración" else "Importar Configuración") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                readOnly = isExport
            )
        },
        confirmButton = {
            if (isExport) {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Config", text))
                    onDismiss()
                }) { Text("Copiar") }
            } else {
                Button(onClick = {
                    onImport(text)
                    onDismiss()
                }) { Text("Importar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// Función auxiliar para convertir Config a JSON simple
fun configToJson(c: Config): String {
    return """
        {
          "s3Base": "${c.s3Base}",
          "bucket": "${c.bucket}",
          "chan": "${c.chan}",
          "psk": "${c.psk}",
          "chunkSize": ${c.chunkSize},
          "windowSize": ${c.windowSize},
          "pollIntervalMs": ${c.pollIntervalMs},
          "httpTimeout": ${c.httpTimeout},
          "maxSessions": ${c.maxSessions}
        }
    """.trimIndent()
}
