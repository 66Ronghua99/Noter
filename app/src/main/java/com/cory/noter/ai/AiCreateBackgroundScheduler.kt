package com.cory.noter.ai

interface AiCreateBackgroundScheduler {
    fun enqueue(prompt: String)
}

interface AiCreateResultNotifier {
    fun notifyStarted()
    fun notifyResult(result: AiCreateResult)
}
