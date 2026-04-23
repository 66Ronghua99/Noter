package com.cory.noter.domain.settings

data class AppSettings(
    val openRouterApiKey: String,
    val selectedModelId: String,
    val defaultRingtoneUri: String,
)
