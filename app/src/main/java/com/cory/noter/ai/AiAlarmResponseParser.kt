package com.cory.noter.ai

import com.cory.noter.agent.tools.alarm.CreateAlarmArgumentsParser
import com.cory.noter.domain.ai.AiAlarmDraft

class AiAlarmResponseParser {
    private val parser = CreateAlarmArgumentsParser()

    class ClarificationRequiredException(val reason: String) : IllegalArgumentException(
        "Needs clarification: ${reason.ifBlank { "No reason provided" }}",
    )

    fun parse(responseText: String): Result<AiAlarmDraft> =
        parser.parse(responseText).fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(
                    when (error) {
                        is CreateAlarmArgumentsParser.ClarificationRequiredException ->
                            ClarificationRequiredException(error.reason)

                        else -> error
                    },
                )
            },
        )
}
