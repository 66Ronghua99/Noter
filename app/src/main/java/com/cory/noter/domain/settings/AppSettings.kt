package com.cory.noter.domain.settings

data class AppSettings(
    val openRouterApiKey: String,
    val selectedModelId: String,
    val selectedAsrModelId: String,
    val defaultRingtoneUri: String,
) {
    companion object {
        const val DefaultRingtoneUri: String = "content://settings/system/alarm_alert"
    }
}
