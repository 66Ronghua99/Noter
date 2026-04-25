package com.cory.noter.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AiCreateBackgroundScheduler {
    fun enqueue(prompt: String)
}

interface AiCreateResultNotifier {
    fun notifyStarted()
    fun notifyResult(result: AiCreateResult)
}

class ApplicationAiCreateBackgroundScheduler(
    private val creator: AiAlarmCreator,
    private val notifier: AiCreateResultNotifier,
    private val scope: CoroutineScope,
) : AiCreateBackgroundScheduler {
    override fun enqueue(prompt: String) {
        scope.launch {
            notifier.notifyStarted()
            notifier.notifyResult(creator.createFromText(prompt))
        }
    }
}
