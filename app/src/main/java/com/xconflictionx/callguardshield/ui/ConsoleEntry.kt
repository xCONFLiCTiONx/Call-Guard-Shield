package com.xconflictionx.callguardshield.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConsoleEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

enum class LogLevel {
    INFO, WARN, ERROR
}
