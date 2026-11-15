package com.nuvola.proxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvola.proxy.data.ConfigStore
import com.nuvola.proxy.ui.HomeScreen
import com.nuvola.proxy.ui.SettingsScreen
import com.nuvola.proxy.ui.TunnelViewModel

class MainActivity : ComponentActivity() {
    private val vm: TunnelViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configStore = ConfigStore(this)
        var showSettings by mutableStateOf(false)
        setContent {
            MaterialTheme {
                Surface {
                    if (showSettings) {
                        SettingsScreen(config = configStore.load(), onSave = { configStore.save(it); showSettings = false }, onBack = { showSettings = false })
                    } else {
                        val state by vm.state.collectAsStateWithLifecycle()
                        HomeScreen(state = state, onConnect = vm::connect, onDisconnect = vm::disconnect, onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }
}
