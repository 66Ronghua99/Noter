package com.cory.noter.ai

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class WorkManagerAiCreateBackgroundScheduler(
    context: Context,
) : AiCreateBackgroundScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueue(prompt: String) {
        val request = OneTimeWorkRequestBuilder<AiCreateWorker>()
            .setInputData(workDataOf(KEY_PROMPT to prompt))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ai_create_alarm"
        const val TAG = "ai_create_alarm"
        const val KEY_PROMPT = "prompt"
    }
}
