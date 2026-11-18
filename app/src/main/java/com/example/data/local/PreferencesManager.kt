package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DarkModePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cic_off_course_prefs")

data class NotificationPreferences(
    val enabled: Boolean = true,
    val alertHour: Int = 8,
    val alertMinute: Int = 0
)

class PreferencesManager(private val context: Context) {
    private object Keys {
        val DARK_MODE = stringPreferencesKey("dark_mode_preference")
        val ALERTS_ENABLED = booleanPreferencesKey("period_alerts_enabled")
        val ALERT_HOUR = intPreferencesKey("period_alert_hour")
        val ALERT_MINUTE = intPreferencesKey("period_alert_minute")
        val LOGGED_IN_USERNAME = stringPreferencesKey("logged_in_portal_username")
        val SELECTED_ACADEMIC_YEAR = stringPreferencesKey("selected_academic_year")
        val SELECTED_SEMESTER = stringPreferencesKey("selected_semester")
        val SKIPPED_UPDATE_VERSION_CODE = intPreferencesKey("skipped_update_version_code")
    }

    val darkModeFlow: Flow<DarkModePreference> = context.dataStore.data.map { prefs ->
        DarkModePreference.fromRaw(prefs[Keys.DARK_MODE])
    }

    val loggedInUsernameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOGGED_IN_USERNAME]?.takeIf { it.isNotBlank() }
    }

    val selectedAcademicYearFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_ACADEMIC_YEAR]?.takeIf { it.isNotBlank() }
    }

    val selectedSemesterFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_SEMESTER]?.takeIf { it.isNotBlank() }
    }

    val skippedUpdateVersionFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.SKIPPED_UPDATE_VERSION_CODE] ?: 0
    }

    val notificationPreferencesFlow: Flow<NotificationPreferences> = context.dataStore.data.map { prefs ->
        NotificationPreferences(
            enabled = prefs[Keys.ALERTS_ENABLED] ?: true,
            alertHour = prefs[Keys.ALERT_HOUR] ?: 8,
            alertMinute = prefs[Keys.ALERT_MINUTE] ?: 0
        )
    }

    suspend fun setDarkMode(preference: DarkModePreference) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = preference.name
        }
    }

    suspend fun setLoggedInUsername(username: String?) {
        context.dataStore.edit { prefs ->
            if (username.isNullOrBlank()) {
                prefs.remove(Keys.LOGGED_IN_USERNAME)
            } else {
                prefs[Keys.LOGGED_IN_USERNAME] = username.trim().lowercase()
            }
        }
    }

    suspend fun setSelectedAcademicYear(year: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_ACADEMIC_YEAR] = year.trim()
        }
    }

    suspend fun setSelectedSemester(semester: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_SEMESTER] = semester.trim()
        }
    }

    suspend fun setSkippedUpdateVersion(versionCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SKIPPED_UPDATE_VERSION_CODE] = versionCode.coerceAtLeast(0)
        }
    }

    suspend fun setNotificationPreferences(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALERTS_ENABLED] = enabled
            prefs[Keys.ALERT_HOUR] = hour.coerceIn(0, 23)
            prefs[Keys.ALERT_MINUTE] = minute.coerceIn(0, 59)
        }
    }
}
