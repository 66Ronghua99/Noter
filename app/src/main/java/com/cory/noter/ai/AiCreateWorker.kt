package com.cory.noter.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cory.noter.di.appContainer

class AiCreateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prompt = inputData.getString(WorkManagerAiCreateBackgroundScheduler.KEY_PROMPT)
            .orEmpty()
            .trim()
        if (prompt.isEmpty()) {
            return Result.failure()
        }

        val runtime = runtimeFactory(applicationContext)
        runtime.notifyStarted()
        val result = runtime.createFromText(prompt)
        runtime.notifyResult(result)
        return Result.success()
    }

    internal interface Runtime {
        suspend fun createFromText(prompt: String): AiCreateResult

        fun notifyStarted()

        fun notifyResult(result: AiCreateResult)
    }

    private class AppContainerRuntime(
        context: Context,
    ) : Runtime {
        private val container = context.appContainer

        override suspend fun createFromText(prompt: String): AiCreateResult =
            container.aiAlarmCreator.createFromText(prompt)

        override fun notifyStarted() {
            container.aiCreateResultNotifier.notifyStarted()
        }

        override fun notifyResult(result: AiCreateResult) {
            container.aiCreateResultNotifier.notifyResult(result)
        }
    }

    internal companion object {
        var runtimeFactory: (Context) -> Runtime = ::productionRuntime

        fun productionRuntime(context: Context): Runtime = AppContainerRuntime(context)
    }
}
