package com.cory.noter.domain.ai

import com.cory.noter.domain.alarm.RepeatRule
import java.time.LocalDate

data class AiAlarmDraft(
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeatRule: RepeatRule,
    val originalDate: LocalDate?,
    val confidence: Double,
    val originalResponseText: String,
)
