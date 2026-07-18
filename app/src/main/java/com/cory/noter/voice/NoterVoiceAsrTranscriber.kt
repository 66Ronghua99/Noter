package com.cory.noter.voice

import com.cory.noter.ai.NoterApiFailureCode
import com.cory.noter.ai.NoterAsrRequest
import com.cory.noter.ai.NoterAsrGateway
import com.cory.noter.ai.NoterAsrResult
import kotlinx.coroutines.delay

class NoterVoiceAsrTranscriber(
    private val client: NoterAsrGateway,
    private val maxAttempts: Int = 2,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
    private val defaultRetryDelayMillis: Long = 1_000L,
) : RemoteAsrTranscriber {
    init {
        require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
        require(defaultRetryDelayMillis >= 0) { "defaultRetryDelayMillis must not be negative" }
    }

    override suspend fun transcribe(request: VoiceAsrRequest): VoiceAsrResult {
        val asrRequest = NoterAsrRequest(
            languageCode = request.languageCode,
            audioBytes = request.audio.bytes,
        )
        var attempt = 1
        while (true) {
            when (val result = client.transcribe(asrRequest)) {
                is NoterAsrResult.Transcribed -> return VoiceAsrResult.Transcript(result.text)
                is NoterAsrResult.ServiceFailure -> {
                    if (result.retryable && attempt < maxAttempts) {
                        delayProvider(
                            result.retryAfterSeconds?.coerceAtLeast(0L)?.times(1_000L)
                                ?: defaultRetryDelayMillis,
                        )
                        attempt += 1
                    } else {
                        return VoiceAsrResult.Failed(result.code.toVoiceFailure())
                    }
                }
            }
        }
    }

    private fun NoterApiFailureCode.toVoiceFailure(): VoiceCaptureFailure = when (this) {
        NoterApiFailureCode.INVALID_SERVICE_RESPONSE -> VoiceCaptureFailure.UpdateRequired
        NoterApiFailureCode.PAYLOAD_TOO_LARGE -> VoiceCaptureFailure.UploadTooLarge
        NoterApiFailureCode.UNAUTHORIZED,
        NoterApiFailureCode.INVALID_REQUEST,
        NoterApiFailureCode.RATE_LIMITED,
        NoterApiFailureCode.UPSTREAM_REJECTED,
        NoterApiFailureCode.UPSTREAM_UNAVAILABLE,
        NoterApiFailureCode.UPSTREAM_TIMEOUT,
        NoterApiFailureCode.INVALID_UPSTREAM_RESPONSE,
        NoterApiFailureCode.SERVICE_UNAVAILABLE,
        NoterApiFailureCode.NETWORK_UNAVAILABLE,
        -> VoiceCaptureFailure.ServiceUnavailable
    }
}
