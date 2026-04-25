package com.cory.noter.ai

import android.util.Log

class AndroidOpenRouterDebugLogger(
    private val enabled: Boolean,
) : OpenRouterDebugLogger {
    override fun debug(message: String) {
        if (enabled) {
            Log.d(TAG, message)
        }
    }

    override fun warn(message: String, error: Throwable?) {
        if (enabled) {
            Log.w(TAG, message, error)
        }
    }

    private companion object {
        const val TAG = "NoterOpenRouter"
    }
}
