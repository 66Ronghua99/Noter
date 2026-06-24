package com.cory.noter.ai

import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

internal fun defaultOpenRouterCallFactory(): Call.Factory =
    OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

interface OpenRouterDebugLogger {
    fun debug(message: String)
    fun warn(message: String, error: Throwable? = null)
}

object NoOpOpenRouterDebugLogger : OpenRouterDebugLogger {
    override fun debug(message: String) = Unit

    override fun warn(message: String, error: Throwable?) = Unit
}

internal const val OPEN_ROUTER_CHAT_COMPLETIONS_ENDPOINT =
    "https://openrouter.ai/api/v1/chat/completions"

internal const val OPEN_ROUTER_AUDIO_TRANSCRIPTIONS_ENDPOINT =
    "https://openrouter.ai/api/v1/audio/transcriptions"

internal val OPEN_ROUTER_JSON_MEDIA_TYPE = "application/json".toMediaType()
