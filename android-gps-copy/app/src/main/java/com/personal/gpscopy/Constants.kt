package com.personal.gpscopy

/**
 * Single source of truth for IDs, actions and tuning values.
 */
object Constants {

    // Notifications -----------------------------------------------------------
    const val CHANNEL_ID = "gps_coordinate_channel"
    // ONE fixed id => new notification always replaces the old one (no stacking).
    const val NOTIFICATION_ID = 1001

    // Intent actions ----------------------------------------------------------
    const val ACTION_COPY = "com.personal.gpscopy.ACTION_COPY"
    const val ACTION_DISMISS = "com.personal.gpscopy.ACTION_DISMISS"
    const val ACTION_AUTO_COPY = "com.personal.gpscopy.ACTION_AUTO_COPY"

    const val EXTRA_COORDINATE = "extra_coordinate"

    // Behaviour tuning --------------------------------------------------------
    /** Auto-copy + dismiss if the user ignores the notification this long. */
    const val AUTO_COPY_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes

    /** Ignore obviously bad fixes worse than this (meters). */
    const val MAX_ACCEPTABLE_ACCURACY_M = 100f

    /** Debounce: PROVIDERS_CHANGED can fire several times in a burst. */
    const val TRIGGER_DEBOUNCE_MS = 4000L

    /**
     * How long we wait for a fresh fix before falling back to the last known
     * location. Kept under the ~10s BroadcastReceiver/goAsync budget so the
     * receiver never gets killed mid-fetch.
     */
    const val FIX_SOFT_TIMEOUT_MS = 7_000L
}
