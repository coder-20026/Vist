package com.personal.gpscopy

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Short-lived foreground service. Lifecycle:
 *   start -> show "Getting location..." -> request ONE fresh high-accuracy fix
 *         -> validate -> format -> (dedupe) -> update notification + arm 10-min alarm
 *         -> detach the notification and stop. Total awake time: a few seconds.
 */
class LocationService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private val cts = CancellationTokenSource()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var finished = false

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Fix timeout reached without a valid location.")
        finishWithoutResult()
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately with the placeholder notification.
        val placeholder = NotificationHelper.buildGettingLocation(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                placeholder,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, placeholder)
        }
        LocationStore(this).notificationActive = true

        requestFreshFix()
        mainHandler.postDelayed(timeoutRunnable, Constants.FIX_TIMEOUT_MS)
        return START_NOT_STICKY
    }

    private fun requestFreshFix() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            finishWithoutResult()
            return
        }

        // CurrentLocationRequest forces a *fresh* computation and lets us reject
        // stale cached results, satisfying the "prefer fresh fix" requirement.
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0) // do not accept old cached fixes
            .setDurationMillis(Constants.FIX_TIMEOUT_MS)
            .build()

        try {
            fused.getCurrentLocation(request, cts.token)
                .addOnSuccessListener { location -> onLocation(location) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "getCurrentLocation failed", e)
                    finishWithoutResult()
                }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException requesting location", se)
            finishWithoutResult()
        }
    }

    private fun onLocation(location: Location?) {
        if (finished) return

        // Reject invalid / obviously bad fixes (kept internal, never shown to user).
        if (location == null ||
            (location.hasAccuracy() && location.accuracy > Constants.MAX_ACCEPTABLE_ACCURACY_M) ||
            location.latitude == 0.0 && location.longitude == 0.0
        ) {
            Log.w(TAG, "Rejected fix (null/low-accuracy).")
            finishWithoutResult()
            return
        }

        val coordinate = CoordinateFormatter.format(location.latitude, location.longitude)
        val store = LocationStore(this)

        // Duplicate suppression: same coordinate AND a notification is already up.
        if (coordinate == store.lastCoordinate && store.notificationActive &&
            store.currentCoordinate != null
        ) {
            Log.d(TAG, "Duplicate coordinate; not re-posting.")
            // Keep the existing notification; just end the service quietly.
            detachAndStop()
            return
        }

        store.currentCoordinate = coordinate
        store.lastCoordinate = coordinate
        store.notificationActive = true

        // Replace the placeholder with the coordinate notification (same ID).
        val notif = NotificationHelper.buildCoordinate(this, coordinate)
        NotificationManagerCompat.from(this).notify(Constants.NOTIFICATION_ID, notif)

        scheduleAutoCopy(coordinate)
        detachAndStop()
    }

    /** Arm the 10-minute "no action -> auto copy + dismiss" alarm. */
    private fun scheduleAutoCopy(coordinate: String) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ActionReceiver::class.java).apply {
            action = Constants.ACTION_AUTO_COPY
            putExtra(Constants.EXTRA_COORDINATE, coordinate)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getBroadcast(this, 2, intent, flags)

        val triggerAt = SystemClock.elapsedRealtime() + Constants.AUTO_COPY_TIMEOUT_MS
        // Inexact + allow-while-idle: battery friendly, no exact-alarm permission needed.
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
    }

    private fun finishWithoutResult() {
        if (finished) return
        val store = LocationStore(this)
        // If we never produced a coordinate, take the placeholder down.
        if (store.currentCoordinate == null) {
            store.notificationActive = false
            NotificationManagerCompat.from(this).cancel(Constants.NOTIFICATION_ID)
            stopForegroundCompat(removeNotification = true)
        } else {
            detachAndStop()
            return
        }
        finished = true
        stopSelf()
    }

    /** Keep the notification on screen but release the service. */
    private fun detachAndStop() {
        if (finished) return
        finished = true
        mainHandler.removeCallbacks(timeoutRunnable)
        stopForegroundCompat(removeNotification = false)
        stopSelf()
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(timeoutRunnable)
        cts.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationService"
    }
}
