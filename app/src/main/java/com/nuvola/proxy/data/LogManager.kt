package com.nuvola.proxy.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun add(message: String) {
        val line = "[${timeFormat.format(Date())}] $message"
        _logs.update { (it + line).takeLast(200) }
    }

    fun clear() { _logs.value = emptyList() }
    fun getAllAsText(): String = _logs.value.joinToString("\n")
}
