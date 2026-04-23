package com.cory.noter.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cory.noter.ai.OpenRouterModel
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    @Test
    fun `default settings use minimax free model`() = runTest {
        val repository = createRepository(backgroundScope)

        val settings = repository.settings.first()

        assertThat(settings.openRouterApiKey).isEmpty()
        assertThat(settings.selectedModelId).isEqualTo(OpenRouterModel.DefaultId)
        assertThat(settings.defaultRingtoneUri).isEmpty()
    }

    @Test
    fun `saving api key persists it in settings flow`() = runTest {
        val repository = createRepository(backgroundScope)

        repository.setOpenRouterApiKey("sk-or-v1-123")

        assertThat(repository.settings.first().openRouterApiKey).isEqualTo("sk-or-v1-123")
    }

    @Test
    fun `saving selected model persists known built in model`() = runTest {
        val repository = createRepository(backgroundScope)

        val result = repository.setSelectedModel("openrouter/free")

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.settings.first().selectedModelId).isEqualTo("openrouter/free")
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

        repository.setDefaultRingtoneUri("content://media/internal/audio/media/25")

        assertThat(
            repository.settings.first().defaultRingtoneUri,
        ).isEqualTo("content://media/internal/audio/media/25")
    }

    private fun createRepository(scope: CoroutineScope): DataStoreSettingsRepository {
        val file = Files.createTempFile("settings-test", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreSettingsRepository(dataStore)
    }
}
