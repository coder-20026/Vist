package com.personal.gpscopy

import android.app.Activity
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat

/**
 * Invisible, instantly-finishing activity. Notification COPY taps route here so
 * the clipboard write happens with a focused foreground context (required on
 * Android 10+). No visible UI is ever shown.
 */
class CopyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val coordinate = intent.getStringExtra(Constants.EXTRA_COORDINATE)
            ?: LocationStore(this).currentCoordinate

        if (!coordinate.isNullOrEmpty()) {
            ClipboardUtils.copy(this, coordinate)
            ClipboardUtils.toast(this, "Copied")
            ClipboardUtils.lightVibrate(this)
        }

        // Tear everything down: cancel alarm + notification, clear state.
        ActionReceiver.cancelAutoCopyAlarm(this)
        NotificationManagerCompat.from(this).cancel(Constants.NOTIFICATION_ID)
        LocationStore(this).apply {
            notificationActive = false
            currentCoordinate = null
        }

        finish()
        overridePendingTransition(0, 0)
    }
}
