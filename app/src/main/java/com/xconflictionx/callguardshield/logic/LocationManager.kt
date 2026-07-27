package com.xconflictionx.callguardshield.logic

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import java.util.*

class LocationManager(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentState(): String? {
        return try {
            // Get last known location first (battery efficient)
            val location = fusedLocationClient.lastLocation.await() ?: 
                // If last location is null, get current location with low power priority
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_LOW_POWER, null).await()
            
            location?.let {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                addresses?.firstOrNull()?.adminArea // adminArea usually returns the State in US
            }
        } catch (e: Exception) {
            null
        }
    }
}
