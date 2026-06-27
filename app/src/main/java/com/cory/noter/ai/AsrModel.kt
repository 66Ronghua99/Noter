package com.cory.noter.ai

object AsrModel {
    const val DefaultId: String = "nvidia/parakeet-tdt-0.6b-v3"
    const val ChineseDefaultId: String = "qwen/qwen3-asr-flash-2026-02-10"

    val builtInIds: List<String> = listOf(
        DefaultId,
        ChineseDefaultId,
        "mistralai/voxtral-mini-transcribe",
    )

    fun resolveForLanguage(selectedModelId: String, languageCode: String): String =
        if (selectedModelId == DefaultId && languageCode.lowercase().startsWith("zh")) {
            ChineseDefaultId
        } else {
            selectedModelId
        }
}
