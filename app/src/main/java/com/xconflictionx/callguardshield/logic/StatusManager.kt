package com.xconflictionx.callguardshield.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object StatusManager {
    private val _driveError = MutableStateFlow<String?>(null)
    val driveError = _driveError.asStateFlow()

    private val _geminiStage = MutableStateFlow<String?>(null)
    val geminiStage = _geminiStage.asStateFlow()

    fun setDriveError(error: String?) {
        _driveError.value = error
    }

    fun clearDriveError() {
        _driveError.value = null
    }

    fun setGeminiStage(stage: String?) {
        _geminiStage.value = stage
    }
}
