package com.cory.noter.ai

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiCreateBackgroundSchedulerTest {
    @After
    fun tearDown() {
        AiCreateWorker.runtimeFactory = AiCreateWorker::productionRuntime
    }

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

    @Test
    fun `worker fails when prompt is blank`() = runTest {
        val runtime = RecordingRuntime(AiCreateResult.InvalidResponse("unused"))
        AiCreateWorker.runtimeFactory = { runtime }
        val worker = buildWorker(prompt = "   ")

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        assertThat(runtime.events).isEmpty()
    }

    @Test
    fun `worker notifies started and result around ai create for non empty prompt`() = runTest {
        val expected = AiCreateResult.InvalidResponse("provider malformed response")
        val runtime = RecordingRuntime(expected)
        AiCreateWorker.runtimeFactory = { runtime }
        val prompt = "tomorrow at 8 am remind me to take medicine"
        val worker = buildWorker(prompt)

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(runtime.events).containsExactly(
            "notifyStarted",
            "createFromText:$prompt",
            "notifyResult:$expected",
        ).inOrder()
    }

    private fun workSpecInputData(
        context: Context,
        workSpecId: String,
    ) = WorkManagerImpl.getInstance(context)
        .workDatabase
        .workSpecDao()
        .getWorkSpec(workSpecId)
        ?.input

    private fun buildWorker(prompt: String): AiCreateWorker =
        TestListenableWorkerBuilder<AiCreateWorker>(
            ApplicationProvider.getApplicationContext(),
        )
            .setInputData(
                androidx.work.workDataOf(
                    WorkManagerAiCreateBackgroundScheduler.KEY_PROMPT to prompt,
                ),
            )
            .build()

    private class RecordingRuntime(
        private val nextResult: AiCreateResult,
    ) : AiCreateWorker.Runtime {
        val events = mutableListOf<String>()

        override suspend fun createFromText(prompt: String): AiCreateResult {
            events += "createFromText:$prompt"
            return nextResult
        }

        override fun notifyStarted() {
            events += "notifyStarted"
        }

        override fun notifyResult(result: AiCreateResult) {
            events += "notifyResult:$result"
        }
    }
}
