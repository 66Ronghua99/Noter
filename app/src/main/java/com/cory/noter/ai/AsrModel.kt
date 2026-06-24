package com.cory.noter.ai

object AsrModel {
    const val DefaultId: String = "nvidia/parakeet-tdt-0.6b-v3"

    val builtInIds: List<String> = listOf(
        DefaultId,
        "mistralai/voxtral-mini-transcribe",
    )
}
