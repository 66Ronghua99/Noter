package com.cory.noter.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cory.noter.ai.AsrModel
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.domain.settings.AppSettings
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    @Test
    fun `default settings use deepseek v4 flash model`() = runTest {
        val repository = createRepository(backgroundScope)

        val settings = repository.settings.first()

        assertThat(settings.openRouterApiKey).isEmpty()
        assertThat(settings.selectedModelId).isEqualTo(OpenRouterModel.DefaultId)
        assertThat(settings.selectedAsrModelId).isEqualTo(AsrModel.DefaultId)
        assertThat(settings.defaultRingtoneUri).isEqualTo(AppSettings.DefaultRingtoneUri)
        assertThat(settings.themePresetId).isEqualTo(AppSettings.DefaultThemePresetId)
        assertThat(settings.customThemeSeedColor).isNull()
    }

    @Test
    fun `built in asr models include default and secondary options`() {
        assertThat(AsrModel.builtInIds).containsExactly(
            "nvidia/parakeet-tdt-0.6b-v3",
            "qwen/qwen3-asr-flash-2026-02-10",
            "mistralai/voxtral-mini-transcribe",
        ).inOrder()
        assertThat(AsrModel.DefaultId).isEqualTo("nvidia/parakeet-tdt-0.6b-v3")
        assertThat(AsrModel.ChineseDefaultId).isEqualTo("qwen/qwen3-asr-flash-2026-02-10")
    }

    @Test
    fun `saving api key persists it in settings flow`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setOpenRouterApiKey("sk-or-v1-123")

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.settings.first().openRouterApiKey).isEqualTo("sk-or-v1-123")
    }

    @Test
    fun `saving selected model persists known built in model`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setSelectedModel("deepseek/deepseek-v3.2")

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.settings.first().selectedModelId).isEqualTo("deepseek/deepseek-v3.2")
    }

    @Test
    fun `saving selected asr model persists known built in asr model`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setSelectedAsrModel("mistralai/voxtral-mini-transcribe")

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.settings.first().selectedAsrModelId)
            .isEqualTo("mistralai/voxtral-mini-transcribe")
    }

    @Test
    fun `unknown model id is rejected and does not replace prior selection`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setSelectedModel("unknown/model")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.settings.first().selectedModelId).isEqualTo(OpenRouterModel.DefaultId)
    }

    @Test
    fun `unknown asr model id is rejected and does not replace prior selection`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setSelectedAsrModel("unknown/asr-model")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.settings.first().selectedAsrModelId).isEqualTo(AsrModel.DefaultId)
    }

    @Test
    fun `saving ringtone uri persists it in settings flow`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setDefaultRingtoneUri("content://media/internal/audio/media/25")

        assertThat(result.isSuccess).isTrue()
        assertThat(
            repository.settings.first().defaultRingtoneUri,
        ).isEqualTo("content://media/internal/audio/media/25")
    }

    @Test
    fun `saving theme preset persists it and clears custom seed color`() = runTest {
        val repository = createRepository(backgroundScope)

        assertThat(repository.setCustomThemeSeedColor("#b65b70").isSuccess).isTrue()
        val result = repository.setThemePreset("fresh_green")

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.settings.first().themePresetId).isEqualTo("fresh_green")
        assertThat(repository.settings.first().customThemeSeedColor).isNull()
    }

    @Test
    fun `saving custom theme seed persists custom theme state`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setCustomThemeSeedColor("#4a6ea9")

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.settings.first().themePresetId).isEqualTo(AppSettings.CustomThemePresetId)
        assertThat(repository.settings.first().customThemeSeedColor).isEqualTo("#4a6ea9")
    }

    @Test
    fun `unknown theme preset write is rejected and preserves prior theme`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setThemePreset("electric_ultraviolet")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("UNKNOWN_THEME_PRESET_ID")
        assertThat(repository.settings.first().themePresetId).isEqualTo(AppSettings.DefaultThemePresetId)
    }

    @Test
    fun `invalid custom theme seed write is rejected and preserves prior theme`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setCustomThemeSeedColor("not-a-color")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("INVALID_THEME_SEED_COLOR")
        assertThat(repository.settings.first().themePresetId).isEqualTo(AppSettings.DefaultThemePresetId)
        assertThat(repository.settings.first().customThemeSeedColor).isNull()
    }

    @Test
    fun `unknown stored theme preset falls back to default for compatibility`() = runTest {
        val file = Files.createTempFile("settings-test", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("theme_preset_id")] = "old-theme-id"
        }
        val repository = DataStoreSettingsRepository(dataStore)

        val settings = repository.settings.first()

        assertThat(settings.themePresetId).isEqualTo(AppSettings.DefaultThemePresetId)
        assertThat(settings.customThemeSeedColor).isNull()
    }

    @Test
    fun `invalid stored custom theme seed falls back to default for compatibility`() = runTest {
        val file = Files.createTempFile("settings-test", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("theme_preset_id")] = AppSettings.CustomThemePresetId
            preferences[stringPreferencesKey("custom_theme_seed_color")] = "not-a-color"
        }
        val repository = DataStoreSettingsRepository(dataStore)

        val settings = repository.settings.first()

        assertThat(settings.themePresetId).isEqualTo(AppSettings.DefaultThemePresetId)
        assertThat(settings.customThemeSeedColor).isNull()
    }

    @Test
    fun `invalid stored model id fails explicitly on read`() = runTest {
        val file = Files.createTempFile("settings-test", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("selected_model_id")] = "unknown/model"
        }
        val repository = DataStoreSettingsRepository(dataStore)

        val error = runCatching {
            repository.settings.first()
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("UNKNOWN_MODEL_ID")
    }

    @Test
    fun `invalid stored asr model id fails explicitly on read`() = runTest {
        val file = Files.createTempFile("settings-test", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("selected_asr_model_id")] = "unknown/asr-model"
        }
        val repository = DataStoreSettingsRepository(dataStore)

        val error = runCatching {
            repository.settings.first()
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("UNKNOWN_ASR_MODEL_ID")
    }

    @Test
    fun `settings persist across repository recreation`() = runTest {
        val file = Files.createTempFile("settings-test", ".preferences_pb").toFile()
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val firstRepository = createRepository(file, firstScope)

        assertThat(firstRepository.setOpenRouterApiKey("sk-or-v1-999").isSuccess).isTrue()
        assertThat(firstRepository.setSelectedModel("deepseek/deepseek-v3.2").isSuccess).isTrue()
        assertThat(firstRepository.setSelectedAsrModel("mistralai/voxtral-mini-transcribe").isSuccess)
            .isTrue()
        assertThat(
            firstRepository.setDefaultRingtoneUri("content://media/internal/audio/media/99").isSuccess,
        ).isTrue()
        firstScope.cancel()

        val secondScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val secondRepository = createRepository(file, secondScope)

        val settings = secondRepository.settings.first()

        assertThat(settings).isEqualTo(
            AppSettings(
                openRouterApiKey = "sk-or-v1-999",
                selectedModelId = "deepseek/deepseek-v3.2",
                selectedAsrModelId = "mistralai/voxtral-mini-transcribe",
                defaultRingtoneUri = "content://media/internal/audio/media/99",
                themePresetId = AppSettings.DefaultThemePresetId,
                customThemeSeedColor = null,
            ),
        )
        secondScope.cancel()
    }

    private fun createRepository(scope: CoroutineScope): DataStoreSettingsRepository {
        val file = Files.createTempFile("settings-test", ".preferences_pb").toFile()
        return createRepository(file, scope)
    }

    private fun createRepository(
        file: java.io.File,
        scope: CoroutineScope,
    ): DataStoreSettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreSettingsRepository(dataStore)
    }
}
