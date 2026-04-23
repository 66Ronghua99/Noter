package com.cory.noter.ai

import com.cory.noter.domain.ai.AiAlarmDraft
import com.cory.noter.domain.alarm.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

class AiAlarmResponseParser {
    private val json = Json {
        ignoreUnknownKeys = false
    }

    fun parse(responseText: String): Result<AiAlarmDraft> = runCatching {
        val root = try {
            json.parseToJsonElement(responseText).jsonObject
        } catch (exception: IllegalArgumentException) {
            throw invalid("Invalid JSON", exception)
        } catch (exception: SerializationException) {
            throw invalid("Invalid JSON", exception)
        }

        root.requireOnlyKeys(
            allowedKeys = setOf(
                "title",
                "hour",
                "minute",
                "repeatRule",
                "date",
                "confidence",
                "needsClarification",
                "clarificationReason",
            ),
        )

        val needsClarification = root.requiredBoolean("needsClarification")
        val clarificationReason = root.requiredString("clarificationReason")
        if (needsClarification) {
            throw invalid("Needs clarification: ${clarificationReason.ifBlank { "No reason provided" }}")
        }

        val title = root.requiredString("title").trim()
        if (title.isEmpty()) {
            throw invalid("title must be non-empty")
        }

        val hour = root.requiredInt("hour")
        if (hour !in 0..23) {
            throw invalid("hour must be an integer from 0 through 23")
        }

        val minute = root.requiredInt("minute")
        if (minute !in 0..59) {
            throw invalid("minute must be an integer from 0 through 59")
        }

        val repeatRuleObject = root.requiredObject("repeatRule")
        repeatRuleObject.requireOnlyKeys(
            allowedKeys = setOf("type", "daysOfWeek"),
            owner = "repeatRule",
        )
        val repeatRuleType = repeatRuleObject.requiredString("type")
        val daysOfWeek = repeatRuleObject.requiredIntArray("daysOfWeek")
        val invalidDay = daysOfWeek.firstOrNull { it !in 1..7 }
        if (invalidDay != null) {
            throw invalid("repeatRule.daysOfWeek must contain only integers from 1 through 7")
        }

        val originalDate = root.optionalDate("date")
        val repeatRule = when (repeatRuleType) {
            "once" -> {
                val date = originalDate ?: throw invalid("date is required for once repeatRule")
                RepeatRule.Once(date)
            }
            "daily" -> RepeatRule.Daily
            "weekdays" -> RepeatRule.Weekdays
            "custom_weekdays" -> {
                if (daysOfWeek.isEmpty()) {
                    throw invalid("repeatRule.daysOfWeek must not be empty for custom_weekdays")
                }
                RepeatRule.CustomWeekdays(daysOfWeek.map { DayOfWeek.of(it) }.toSet())
            }
            else -> throw invalid("repeatRule.type must be one of once, daily, weekdays, custom_weekdays")
        }

        val confidence = root.requiredDouble("confidence")

        AiAlarmDraft(
            title = title,
            hour = hour,
            minute = minute,
            repeatRule = repeatRule,
            originalDate = originalDate,
            confidence = confidence,
            originalResponseText = responseText,
        )
    }

    private fun JsonObject.requireOnlyKeys(allowedKeys: Set<String>, owner: String? = null) {
        val unexpectedKey = keys.firstOrNull { it !in allowedKeys } ?: return
        val prefix = owner?.let { "$it " }.orEmpty()
        throw invalid("${prefix}unexpected key: $unexpectedKey")
    }

    private fun JsonObject.requiredObject(name: String): JsonObject {
        val element = this[name] ?: throw invalid("$name is required")
        return element as? JsonObject ?: throw invalid("$name must be an object")
    }

    private fun JsonObject.requiredString(name: String): String {
        val primitive = requiredPrimitive(name)
        if (!primitive.isString) {
            throw invalid("$name must be a string")
        }
        return primitive.content
    }

    private fun JsonObject.optionalString(name: String): String? {
        val element = this[name] ?: return null
        if (element is JsonNull) {
            return null
        }
        val primitive = element as? JsonPrimitive ?: throw invalid("$name must be a string")
        if (!primitive.isString) {
            throw invalid("$name must be a string")
        }
        return primitive.content
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val primitive = requiredPrimitive(name)
        if (primitive.isString) {
            throw invalid("$name must be an integer")
        }
        return primitive.intOrNull ?: throw invalid("$name must be an integer")
    }

    private fun JsonObject.requiredDouble(name: String): Double {
        val primitive = requiredPrimitive(name)
        if (primitive.isString) {
            throw invalid("$name must be a number")
        }
        return primitive.doubleOrNull ?: throw invalid("$name must be a number")
    }

    private fun JsonObject.requiredBoolean(name: String): Boolean {
        val primitive = requiredPrimitive(name)
        if (primitive.isString) {
            throw invalid("$name must be a boolean")
        }
        return primitive.booleanOrNull ?: throw invalid("$name must be a boolean")
    }

    private fun JsonObject.requiredIntArray(name: String): List<Int> {
        val element = this[name] ?: throw invalid("$name is required")
        val array = element as? JsonArray ?: throw invalid("$name must be an array")
        return array.mapIndexed { index, item ->
            val primitive = item as? JsonPrimitive ?: throw invalid("$name[$index] must be an integer")
            if (primitive.isString) {
                throw invalid("$name[$index] must be an integer")
            }
            primitive.intOrNull ?: throw invalid("$name[$index] must be an integer")
        }
    }

    private fun JsonObject.optionalDate(name: String): LocalDate? {
        val dateText = optionalString(name)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            LocalDate.parse(dateText)
        } catch (exception: DateTimeParseException) {
            throw invalid("$name must be an ISO local date", exception)
        }
    }

    private fun JsonObject.requiredPrimitive(name: String): JsonPrimitive {
        val element = this[name] ?: throw invalid("$name is required")
        return element as? JsonPrimitive ?: throw invalid("$name must be a primitive value")
    }

    private fun invalid(message: String, cause: Throwable? = null): IllegalArgumentException {
        return IllegalArgumentException(message, cause)
    }
}
