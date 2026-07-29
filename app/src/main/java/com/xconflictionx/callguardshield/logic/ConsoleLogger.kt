package com.xconflictionx.callguardshield.logic

import com.xconflictionx.callguardshield.ui.ConsoleEntry
import com.xconflictionx.callguardshield.ui.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConsoleLogger {
    private val _logs = MutableStateFlow<List<ConsoleEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val current = _logs.value.toMutableList()
        current.add(ConsoleEntry(System.currentTimeMillis(), tag, message, level))
        if (current.size > 200) current.removeAt(0)
        _logs.value = current
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
