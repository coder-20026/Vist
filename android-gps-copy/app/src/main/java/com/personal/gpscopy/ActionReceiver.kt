package com.personal.gpscopy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Handles:
 *  - DISMISS   : remove the notification, copy NOTHING.
 *  - AUTO_COPY : the 10-minute timeout fired -> copy current coordinate, remove
 *                notification, no popup/screen.
 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = LocationStore(context)
        when (intent.action) {

            Constants.ACTION_DISMISS -> {
                NotificationManagerCompat.from(context).cancel(Constants.NOTIFICATION_ID)
                cancelAutoCopyAlarm(context)
                store.notificationActive = false
                store.currentCoordinate = null
            }

            Constants.ACTION_AUTO_COPY -> {
                val coordinate = intent.getStringExtra(Constants.EXTRA_COORDINATE)
                    ?: store.currentCoordinate
                if (!coordinate.isNullOrEmpty()) {
                    // Best-effort background clipboard write (no UI is opened).
                    ClipboardUtils.copy(context, coordinate)
                    Log.d(TAG, "Auto-copied coordinate after timeout.")
                }
                NotificationManagerCompat.from(context).cancel(Constants.NOTIFICATION_ID)
                store.notificationActive = false
                store.currentCoordinate = null
            }
        }
    }

    companion object {
        private const val TAG = "ActionReceiver"

        fun cancelAutoCopyAlarm(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ActionReceiver::class.java).apply {
                action = Constants.ACTION_AUTO_COPY
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, 2, intent, flags)
            am.cancel(pending)
        }
    }
}
