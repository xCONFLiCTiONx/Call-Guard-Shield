package com.xconflictionx.callguardshield.service

import android.telecom.Call
import android.telecom.InCallService

/**
 * Empty InCallService. On some devices (Samsung), declaring an InCallService
 * helps the system recognize the app as a candidate for call handling roles.
 */
class DummyInCallService : InCallService() {
    override fun onCallAdded(call: Call?) {}
    override fun onCallRemoved(call: Call?) {}
}
