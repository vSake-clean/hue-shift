package io.github.vsakeclean.hueshift.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.vsakeclean.hueshift.engine.EngineKind
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/** Persists [HueSettings] with Jetpack DataStore. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
        ),
    )

    suspend fun load(): HueSettings = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .first()
        .toSettings()

    suspend fun save(settings: HueSettings) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            prefs[FILTER_ID] = settings.filterId
            prefs[ROOT_ALLOWED] = settings.rootAllowed
            settings.intensities.forEach { (id, value) -> prefs[intensityKey(id)] = value }
            prefs.setOrRemove(ENGINE, settings.enginePreference?.name)
            prefs.setOrRemove(APPLIED_ENGINE, settings.appliedEngine?.name)
        }
    }

    private fun Preferences.toSettings(): HueSettings {
        val defaults = HueSettings()
        val intensities = asMap().entries
            .filter { (key, value) -> key.name.startsWith(INTENSITY_PREFIX) && value is Float }
            .associate { (key, value) -> key.name.removePrefix(INTENSITY_PREFIX) to value as Float }
        return HueSettings(
            enabled = this[ENABLED] ?: defaults.enabled,
            filterId = this[FILTER_ID] ?: defaults.filterId,
            intensities = intensities,
            enginePreference = this[ENGINE].toEngineKind(),
            rootAllowed = this[ROOT_ALLOWED] ?: defaults.rootAllowed,
            appliedEngine = this[APPLIED_ENGINE].toEngineKind(),
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.setOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun String?.toEngineKind(): EngineKind? = EngineKind.entries.firstOrNull { it.name == this }

    private companion object {
        const val FILE_NAME = "hue_settings"
        const val INTENSITY_PREFIX = "intensity_"

        val ENABLED = booleanPreferencesKey("enabled")
        val FILTER_ID = stringPreferencesKey("filter_id")
        val ENGINE = stringPreferencesKey("engine")
        val ROOT_ALLOWED = booleanPreferencesKey("root_allowed")
        val APPLIED_ENGINE = stringPreferencesKey("applied_engine")

        fun intensityKey(filterId: String) = floatPreferencesKey(INTENSITY_PREFIX + filterId)
    }
}
