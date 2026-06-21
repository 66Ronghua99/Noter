package com.cory.noter.ai

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiCreateBackgroundSchedulerTest {
    @Test
    fun `enqueue creates one ai create work request with prompt input`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        val scheduler = WorkManagerAiCreateBackgroundScheduler(context)
        val prompt = "tomorrow at 8 am remind me to take medicine"

        scheduler.enqueue(prompt)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerAiCreateBackgroundScheduler.UNIQUE_WORK_NAME)
            .get()

        assertThat(workInfos).hasSize(1)
        val inputData = requireNotNull(workSpecInputData(context, workInfos.single().id.toString()))

        assertThat(inputData.getString(WorkManagerAiCreateBackgroundScheduler.KEY_PROMPT))
            .isEqualTo(prompt)
    }

    private fun workSpecInputData(
        context: Context,
        workSpecId: String,
    ) = WorkManagerImpl.getInstance(context)
        .workDatabase
        .workSpecDao()
        .getWorkSpec(workSpecId)
        ?.input
}
