package com.cory.noter.ai

object AiListedAlarmFormatter {
    fun formatStatus(alarms: List<AiListedAlarm>): String {
        if (alarms.isEmpty()) {
            return "Listed 0 alarms."
        }
        return buildString {
            append("Listed ")
            append(alarms.size)
            append(if (alarms.size == 1) " alarm:" else " alarms:")
            alarms.forEach { alarm ->
                append('\n')
                append(formatRow(alarm))
            }
        }
    }

    fun formatRows(alarms: List<AiListedAlarm>): String =
        if (alarms.isEmpty()) {
            "No alarms found."
        } else {
            alarms.joinToString(separator = "\n", transform = ::formatRow)
        }

    private fun formatRow(alarm: AiListedAlarm): String {
        val parts = mutableListOf(
            alarm.title,
            alarm.localTime,
            alarm.repeatSummary,
            "pause: ${alarm.pauseState}",
        )
        alarm.nextTriggerAtMillis?.let { parts += "next: $it" }
        return parts.joinToString(" - ")
    }
}
