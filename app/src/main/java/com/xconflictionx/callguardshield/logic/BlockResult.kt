package com.xconflictionx.callguardshield.logic

sealed class BlockResult {
    data class Allow(val isContact: Boolean) : BlockResult()
    data class Block(val reason: String, val isContact: Boolean) : BlockResult()
}
