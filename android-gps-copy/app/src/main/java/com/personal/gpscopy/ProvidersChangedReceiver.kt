package com.personal.gpscopy

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The ONLY always-on piece. It does nothing but wait for the system to tell it
 * that the Location/GPS toggle changed. When GPS turns ON it kicks off a single
 * fresh fix and then everything goes back to sleep. No polling, no tracking.
 *
 * IMPORTANT: We do NOT start a foreground service here. On Android 12+ starting
 * a foreground service from a background broadcast (PROVIDERS_CHANGED is NOT an
 * exempt action) throws ForegroundServiceStartNotAllowedException and the whole
 * flow dies silently. Instead we keep the receiver alive with goAsync() and fetch
 * a single location fix directly, posting the notification ourselves.
 */
class ProvidersChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocationManager.PROVIDERS_CHANGED_ACTION) return

        val appContext = context.applicationContext
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Only react when location is actually enabled (the OFF->ON edge).
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
        if (!enabled) return

        // No location permission => nothing we can do.
        if (ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission missing; ignoring trigger.")
            return
        }

        // Debounce the burst of PROVIDERS_CHANGED events.
        val store = LocationStore(appContext)
        val now = SystemClock.elapsedRealtime()
        if (now - store.lastTriggerTime < Constants.TRIGGER_DEBOUNCE_MS) {
            Log.d(TAG, "Debounced duplicate trigger.")
            return
        }
        store.lastTriggerTime = now

        Log.d(TAG, "GPS turned ON -> fetching one-shot location (goAsync).")

        // Keep the broadcast alive while the async location callback runs.
        val pendingResult = goAsync()
        try {
            LocationFetcher.fetch(appContext) {
                // Always release the broadcast, even on failure.
                try {
                    pendingResult.finish()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location fetch", e)
            try {
                pendingResult.finish()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "ProvidersChangedRx"
    }
}
