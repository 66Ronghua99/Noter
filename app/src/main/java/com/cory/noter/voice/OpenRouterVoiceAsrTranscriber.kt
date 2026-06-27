package com.cory.noter.voice

import com.cory.noter.ai.AsrTranscriptionResult
import com.cory.noter.ai.OpenRouterAsrClient
import com.cory.noter.ai.OpenRouterAsrRequest

class OpenRouterVoiceAsrTranscriber(
    private val client: OpenRouterAsrClient,
) : RemoteAsrTranscriber {
    override suspend fun transcribe(request: VoiceAsrRequest): VoiceAsrResult = when (
        val result = client.transcribe(
            OpenRouterAsrRequest(
                apiKey = request.apiKey,
                modelId = request.modelId,
                languageCode = request.languageCode,
                audioBytes = request.audio.bytes,
            ),
        )
    ) {
        is AsrTranscriptionResult.Transcribed -> VoiceAsrResult.Transcript(result.text)
        is AsrTranscriptionResult.NetworkFailure -> VoiceAsrResult.Failed(result.reason)
        is AsrTranscriptionResult.RateLimited -> VoiceAsrResult.Failed(result.reason)
        is AsrTranscriptionResult.RemoteFailure -> {
            VoiceAsrResult.Failed("OpenRouter ASR failed with HTTP ${result.code}: ${result.reason}")
        }
        is AsrTranscriptionResult.InvalidResponse -> VoiceAsrResult.Failed(result.reason)
    }
}
