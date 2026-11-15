package com.nuvola.proxy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvola.proxy.data.ConfigStore
import com.nuvola.proxy.ui.HomeScreen
import com.nuvola.proxy.ui.SettingsScreen
import com.nuvola.proxy.ui.LogScreen
import com.nuvola.proxy.ui.TunnelViewModel

class MainActivity : ComponentActivity() {
    private val vm: TunnelViewModel by viewModels()
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeAskNotifications()
        val configStore = ConfigStore(this)
        var currentScreen by mutableStateOf("home")

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        "settings" -> SettingsScreen(
                            config = configStore.load(),
                            onSave = { configStore.save(it); currentScreen = "home" },
                            onBack = { currentScreen = "home" }
                        )
                        "logs" -> LogScreen(onBack = { currentScreen = "home" })
                        else -> {
                            val state by vm.state.collectAsStateWithLifecycle()
                            HomeScreen(
                                state = state,
                                onConnect = {
                                    maybeAskNotifications()
                                    vm.connect()
                                },
                                onDisconnect = vm::disconnect,
                                onOpenSettings = { currentScreen = "settings" },
                                onOpenLogs = { currentScreen = "logs" }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun maybeAskNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
