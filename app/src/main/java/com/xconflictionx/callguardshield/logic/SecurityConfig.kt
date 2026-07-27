package com.xconflictionx.callguardshield.logic

import android.util.Base64

object SecurityConfig {
    /**
     * Utility to create Basic Auth header for Twilio.
     */
    fun getTwilioAuthHeader(sid: String, token: String): String {
        val creds = "$sid:$token"
        return "Basic " + Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
    }
}
