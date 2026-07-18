package com.cory.noter.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cory.noter.domain.settings.AppSettings
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    @Test
    fun `migration removes exactly legacy keys and preserves unrelated preferences`() = runTest {
        val file = Files.createTempFile("settings-migration", ".preferences_pb").toFile()
        val dataStore = createDataStore(file, backgroundScope)
        dataStore.edit { preferences ->
            preferences[LEGACY_API_KEY] = "obsolete-secret"
            preferences[LEGACY_CHAT_MODEL] = "unknown/chat-model"
            preferences[LEGACY_ASR_MODEL] = "unknown/asr-model"
            preferences[RINGTONE] = "content://media/alarm"
            preferences[CALENDAR_ID] = 42L
            preferences[THEME] = "fresh_green"
            preferences[SENTINEL] = "keep-me"
        }

        val repository = DataStoreSettingsRepository(dataStore)
        val settings = repository.settings.first()
        val stored = dataStore.data.first()

        assertThat(settings.defaultRingtoneUri).isEqualTo("content://media/alarm")
        assertThat(settings.defaultCalendarId).isEqualTo(42L)
        assertThat(settings.themePresetId).isEqualTo("fresh_green")
        assertThat(stored[LEGACY_API_KEY]).isNull()
        assertThat(stored[LEGACY_CHAT_MODEL]).isNull()
        assertThat(stored[LEGACY_ASR_MODEL]).isNull()
        assertThat(stored[RINGTONE]).isEqualTo("content://media/alarm")
        assertThat(stored[CALENDAR_ID]).isEqualTo(42L)
        assertThat(stored[THEME]).isEqualTo("fresh_green")
        assertThat(stored[SENTINEL]).isEqualTo("keep-me")
    }

    @Test
    fun `migration is idempotent when the repository is recreated`() = runTest {
        val file = Files.createTempFile("settings-migration", ".preferences_pb").toFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val firstStore = createDataStore(file, firstScope)
            firstStore.edit { preferences ->
                preferences[LEGACY_API_KEY] = "obsolete-secret"
                preferences[LEGACY_CHAT_MODEL] = "unknown/chat-model"
                preferences[LEGACY_ASR_MODEL] = "unknown/asr-model"
                preferences[SENTINEL] = "keep-me"
            }

            DataStoreSettingsRepository(firstStore).settings.first()
            val afterFirstMigration = firstStore.data.first().asMap()
            firstScope.cancel()

            val secondStore = createDataStore(file, secondScope)
            DataStoreSettingsRepository(secondStore).settings.first()
            val afterSecondMigration = secondStore.data.first().asMap()

            assertThat(afterSecondMigration).isEqualTo(afterFirstMigration)
            assertThat(afterSecondMigration[SENTINEL]).isEqualTo("keep-me")
            assertThat(afterSecondMigration).containsKey(stringPreferencesKey("sentinel"))
        } finally {
            firstScope.cancel()
            secondScope.cancel()
        }
    }

    @Test
    fun `unknown legacy model values cannot fail settings decoding`() = runTest {
        val file = Files.createTempFile("settings-migration", ".preferences_pb").toFile()
        val dataStore = createDataStore(file, backgroundScope)
        dataStore.edit { preferences ->
            preferences[LEGACY_CHAT_MODEL] = "provider/private-model"
            preferences[LEGACY_ASR_MODEL] = "provider/private-asr"
        }

        val settings = DataStoreSettingsRepository(dataStore).settings.first()

        assertThat(settings).isEqualTo(
            AppSettings(defaultRingtoneUri = AppSettings.DefaultRingtoneUri),
        )
    }

    @Test
    fun `retained settings continue to persist`() = runTest {
        val file = Files.createTempFile("settings-persistence", ".preferences_pb").toFile()
        val repository = DataStoreSettingsRepository(createDataStore(file, backgroundScope))

        assertThat(repository.setDefaultRingtoneUri("content://media/alarm").isSuccess).isTrue()
        assertThat(repository.setDefaultCalendarId(7L).isSuccess).isTrue()
        assertThat(repository.setCustomThemeSeedColor("#b65b70").isSuccess).isTrue()

        assertThat(repository.settings.first()).isEqualTo(
            AppSettings(
                defaultRingtoneUri = "content://media/alarm",
                defaultCalendarId = 7L,
                themePresetId = AppSettings.CustomThemePresetId,
                customThemeSeedColor = "#b65b70",
            ),
        )
    }

    private fun createDataStore(file: java.io.File, scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private companion object {
        val LEGACY_API_KEY = stringPreferencesKey("open_router_api_key")
        val LEGACY_CHAT_MODEL = stringPreferencesKey("selected_model_id")
        val LEGACY_ASR_MODEL = stringPreferencesKey("selected_asr_model_id")
        val RINGTONE = stringPreferencesKey("default_ringtone_uri")
        val CALENDAR_ID = longPreferencesKey("default_calendar_id")
        val THEME = stringPreferencesKey("theme_preset_id")
        val SENTINEL = stringPreferencesKey("sentinel")
    }
}
