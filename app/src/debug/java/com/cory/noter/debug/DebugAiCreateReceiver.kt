package com.cory.noter.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cory.noter.NoterApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DebugAiCreateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            return
        }
        val pendingResult = goAsync()
        val appContainer = (context.applicationContext as NoterApplication).appContainer
        val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty().trim()
        val model = intent.getStringExtra(EXTRA_MODEL).orEmpty().trim()

        scope.launch {
            try {
                if (prompt.isEmpty()) {
                    Log.w(TAG, "missing prompt extra")
                    return@launch
                }
                if (model.isNotEmpty()) {
                    appContainer.settingsRepository.setSelectedModel(model).getOrThrow()
                    Log.d(TAG, "selected model=$model")
                }
                Log.d(TAG, "enqueue.start promptChars=${prompt.length}")
                appContainer.aiCreateBackgroundScheduler.enqueue(prompt)
                Log.d(TAG, "enqueue.accepted")
            } catch (error: Throwable) {
                Log.e(TAG, "enqueue.failed ${error.javaClass.simpleName}: ${error.message}", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.cory.noter.DEBUG_AI_CREATE"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_MODEL = "model"
        const val TAG = "NoterDebugAiCreate"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
