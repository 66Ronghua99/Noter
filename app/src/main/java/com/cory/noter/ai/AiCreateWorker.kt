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

        val container = applicationContext.appContainer
        container.aiCreateResultNotifier.notifyStarted()
        val result = container.aiAlarmCreator.createFromText(prompt)
        container.aiCreateResultNotifier.notifyResult(result)
        return Result.success()
    }
}
