package com.babydatalog.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var deviceName: String?
        get() = prefs.getString(KEY_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var pairingCode: String?
        get() = prefs.getString(KEY_PAIRING_CODE, null)
        set(value) = prefs.edit().putString(KEY_PAIRING_CODE, value).apply()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var lastSyncMs: Long
        get() = prefs.getLong(KEY_LAST_SYNC_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_MS, value).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_LAST_SYNC_MS = "last_sync_ms"
    }
}
