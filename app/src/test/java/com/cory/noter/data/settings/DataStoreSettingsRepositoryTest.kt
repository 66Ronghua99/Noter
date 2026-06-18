package com.cory.noter.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
        assertThat(settings.defaultRingtoneUri).isEqualTo(AppSettings.DefaultRingtoneUri)
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
    fun `unknown model id is rejected and does not replace prior selection`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setSelectedModel("unknown/model")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.settings.first().selectedModelId).isEqualTo(OpenRouterModel.DefaultId)
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
    fun `settings persist across repository recreation`() = runTest {
        val file = Files.createTempFile("settings-test", ".preferences_pb").toFile()
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val firstRepository = createRepository(file, firstScope)

        assertThat(firstRepository.setOpenRouterApiKey("sk-or-v1-999").isSuccess).isTrue()
        assertThat(firstRepository.setSelectedModel("deepseek/deepseek-v3.2").isSuccess).isTrue()
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
                defaultRingtoneUri = "content://media/internal/audio/media/99",
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
