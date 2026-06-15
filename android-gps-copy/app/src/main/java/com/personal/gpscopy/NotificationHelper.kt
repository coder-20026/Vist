package com.personal.gpscopy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Builds the single notification. Two states:
 *   1. "Getting location..."  (no actions, just a placeholder)
 *   2. "22.1234,71.5628"      (COPY + DISMISS actions)
 */
object NotificationHelper {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(Constants.CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    Constants.CHANNEL_ID,
                    "GPS Coordinate",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shows the current GPS coordinate to copy"
                    setShowBadge(false)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** Placeholder shown while we wait for the first valid fix. */
    fun buildGettingLocation(context: Context): Notification {
        ensureChannel(context)
        return baseBuilder(context)
            .setContentTitle("Getting location...")
            .setOngoing(true)
            .build()
    }

    /** Final notification showing only the coordinate, with COPY / DISMISS. */
    fun buildCoordinate(context: Context, coordinate: String): Notification {
        ensureChannel(context)

        val copyIntent = Intent(context, CopyActivity::class.java).apply {
            action = Constants.ACTION_COPY
            putExtra(Constants.EXTRA_COORDINATE, coordinate)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val copyPending = PendingIntent.getActivity(
            context, 0, copyIntent, pendingFlags()
        )

        val dismissIntent = Intent(context, ActionReceiver::class.java).apply {
            action = Constants.ACTION_DISMISS
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, 1, dismissIntent, pendingFlags()
        )

        return baseBuilder(context)
            // Only the coordinate, no title / label / extra text.
            .setContentTitle(coordinate)
            .setOngoing(true) // stays until COPY / DISMISS / auto-copy
            .addAction(0, "COPY", copyPending)
            .addAction(0, "DISMISS", dismissPending)
            .build()
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, Constants.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
    }

    private fun pendingFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    }
}
