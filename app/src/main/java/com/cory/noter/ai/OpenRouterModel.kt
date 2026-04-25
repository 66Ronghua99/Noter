package com.cory.noter.ai

object OpenRouterModel {
    const val DefaultId: String = "minimax/minimax-m2.5:free"

    val builtInIds: List<String> = listOf(
        DefaultId,
        "deepseek/deepseek-v3.2",
        "openrouter/free",
        "qwen/qwen3-next-80b-a3b-instruct:free",
        "z-ai/glm-4.5-air:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "google/gemma-3-27b-it:free",
        "openai/gpt-oss-120b:free",
    )
}
