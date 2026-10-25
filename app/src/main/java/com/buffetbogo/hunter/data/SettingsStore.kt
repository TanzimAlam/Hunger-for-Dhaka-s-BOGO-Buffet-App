package com.buffetbogo.hunter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.buffetbogo.hunter.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "buffet_settings")

data class AppSettings(
    /** Optional: only widens web coverage. The keyless sources run without it. */
    val braveApiKey: String = "",
    /** Google Places key: either typed into Settings or baked in from local.properties. */
    val placesApiKey: String = "",
    val placesKeyFromBuild: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val lastSyncAt: Long = 0L,
    val seedVersion: Int = 0,
    val sortOrder: SortOrder = SortOrder.ENDS_SOONEST,
) {
    val usesBrave: Boolean get() = braveApiKey.isNotBlank()
    val canFetchRatings: Boolean get() = placesApiKey.isNotBlank()
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val BRAVE_API_KEY = stringPreferencesKey("brave_api_key")
        val PLACES_API_KEY = stringPreferencesKey("places_api_key")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val SEED_VERSION = intPreferencesKey("seed_version")
        val SORT_ORDER = stringPreferencesKey("sort_order")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            val storedPlacesKey = prefs[Keys.PLACES_API_KEY].orEmpty()
            val bakedKey = BuildConfig.PLACES_API_KEY
            AppSettings(
                braveApiKey = prefs[Keys.BRAVE_API_KEY].orEmpty(),
                placesApiKey = storedPlacesKey.ifBlank { bakedKey },
                placesKeyFromBuild = storedPlacesKey.isBlank() && bakedKey.isNotBlank(),
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                lastSyncAt = prefs[Keys.LAST_SYNC_AT] ?: 0L,
                seedVersion = prefs[Keys.SEED_VERSION] ?: 0,
                sortOrder = SortOrder.fromKey(prefs[Keys.SORT_ORDER]),
            )
        }

    suspend fun setBraveApiKey(value: String) = context.dataStore.edit { it[Keys.BRAVE_API_KEY] = value.trim() }

    suspend fun setPlacesApiKey(value: String) = context.dataStore.edit { it[Keys.PLACES_API_KEY] = value.trim() }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }

    suspend fun setSeedVersion(version: Int) = context.dataStore.edit { it[Keys.SEED_VERSION] = version }

    suspend fun setSortOrder(order: SortOrder) = context.dataStore.edit { it[Keys.SORT_ORDER] = order.key }

    suspend fun markSynced(at: Long) = context.dataStore.edit { it[Keys.LAST_SYNC_AT] = at }
}
