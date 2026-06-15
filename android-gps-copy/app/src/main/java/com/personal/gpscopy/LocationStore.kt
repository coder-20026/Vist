package com.personal.gpscopy

import android.content.Context

/**
 * Tiny SharedPreferences-backed state. Keeps the app "dormant" friendly:
 * no in-memory singletons that need a live process.
 */
class LocationStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("gps_copy_state", Context.MODE_PRIVATE)

    /** The coordinate currently shown / to be copied. */
    var currentCoordinate: String?
        get() = prefs.getString(KEY_CURRENT, null)
        set(value) = prefs.edit().putString(KEY_CURRENT, value).apply()

    /** The last coordinate we displayed (for duplicate suppression). */
    var lastCoordinate: String?
        get() = prefs.getString(KEY_LAST, null)
        set(value) = prefs.edit().putString(KEY_LAST, value).apply()

    /** Is a notification currently on screen? */
    var notificationActive: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVE, value).apply()

    /** Timestamp of the last accepted trigger (debounce). */
    var lastTriggerTime: Long
        get() = prefs.getLong(KEY_TRIGGER_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_TRIGGER_TS, value).apply()

    companion object {
        private const val KEY_CURRENT = "current_coordinate"
        private const val KEY_LAST = "last_coordinate"
        private const val KEY_ACTIVE = "notification_active"
        private const val KEY_TRIGGER_TS = "last_trigger_ts"
    }
}
