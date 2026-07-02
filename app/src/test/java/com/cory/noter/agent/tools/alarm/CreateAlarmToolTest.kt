package com.cory.noter.agent.tools.alarm

import android.Manifest
import com.cory.noter.agent.AgentFailure
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolExecution
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.calendar.CalendarAlarmSyncer
import com.cory.noter.calendar.CalendarSyncRequest
import com.cory.noter.calendar.CalendarSyncResult
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.ui.FakeAlarmRepository
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class CreateAlarmToolTest {
    private val zoneId = ZoneId.of("Asia/Hong_Kong")
    private val now = ZonedDateTime.of(2026, 4, 23, 9, 0, 0, 0, zoneId)
    private val clock = Clock.fixed(now.toInstant(), zoneId)

    @Test
    fun `tool exposes create_alarm schema and not submit_alarm_draft`() {
        val tool = createTool()

        assertThat(tool.spec.name).isEqualTo("create_alarm")
        assertThat(tool.spec.name).isNotEqualTo("submit_alarm_draft")
        assertThat(tool.spec.parameters["type"]!!.jsonPrimitive.content).isEqualTo("object")
        val properties = tool.spec.parameters["properties"]!!.jsonObject
        assertThat(properties.keys).containsExactly(
            "title",
            "hour",
            "minute",
            "repeatRule",
            "date",
            "confidence",
            "needsClarification",
            "clarificationReason",
            "calendarSync",
        )
        val repeatRuleProperties = properties["repeatRule"]!!.jsonObject["properties"]!!.jsonObject
        assertThat(repeatRuleProperties.keys).containsExactly(
            "type",
            "daysOfWeek",
            "startDate",
            "endDate",
            "intervalWeeks",
        )
        assertThat(tool.spec.parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly(
            "title",
            "hour",
            "minute",
            "repeatRule",
            "date",
            "confidence",
            "needsClarification",
            "clarificationReason",
            "calendarSync",
        ).inOrder()
        val calendarSyncProperties = properties["calendarSync"]!!.jsonObject["properties"]!!.jsonObject
        assertThat(calendarSyncProperties.keys).containsExactly("enabled", "reason", "durationMinutes")
        assertThat(properties["calendarSync"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("enabled", "reason")
            .inOrder()
        assertThat(properties["repeatRule"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("type", "daysOfWeek")
            .inOrder()
        assertThat(tool.spec.parameters.toString()).contains("weekly_interval")
    }

    @Test
    fun `valid arguments create and schedule ai alarm`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val scheduler = FakeAlarmScheduler()
        val tool = createTool(repository = repository, scheduler = scheduler)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = validOnceArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isTrue()
        assertThat(success.result.content.toString()).contains("\"status\":\"created\"")
        assertThat(success.result.content.toString()).contains("\"calendarSync\":{\"enabled\":false")
        assertThat(success.result.content.toString()).contains("\"status\":\"skipped\"")
        val alarms = repository.alarms.first()
        assertThat(alarms).hasSize(1)
        val alarm = alarms.single()
        assertThat(alarm.source).isEqualTo(AlarmSource.AI)
        assertThat(alarm.aiOriginalText).isEqualTo("tomorrow morning remind me to take medicine")
        assertThat(scheduler.scheduledAlarms[alarm.id]).isEqualTo(alarm)
    }

    @Test
    fun `invalid arguments fail before repository write`() = runTest {
        val repository = RecordingAlarmRepository(clock = clock, zoneId = zoneId)
        val tool = createTool(repository = repository)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = "not json",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Failure::class.java)
        val failure = result as AgentToolExecution.Failure
        assertThat(failure.failure).isEqualTo(AgentFailure.ToolExecutionFailed("Invalid JSON"))
        assertThat(failure.committedResult).isNull()
        assertThat(repository.createCalls).isEqualTo(0)
    }

    @Test
    fun `prompt compliant calendar sync arguments still create and schedule local alarm`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val scheduler = FakeAlarmScheduler()
        val calendarSyncer = RecordingCalendarSyncer(
            result = CalendarSyncResult.Synced(
                alarmId = 1,
                calendarId = 42,
                eventId = 9001,
            ),
        )
        val tool = createTool(repository = repository, scheduler = scheduler, calendarSyncer = calendarSyncer)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = validOnceArguments(
                    calendarSync = """
                      "calendarSync": {
                        "enabled": true,
                        "reason": "explicit_user_request",
                        "durationMinutes": 45
                      }
                    """.trimIndent(),
                ),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        val content = success.result.content.toString()
        assertThat(content).contains("\"status\":\"created\"")
        assertThat(content).contains("\"calendarSync\":{\"enabled\":true")
        assertThat(content).contains("\"reason\":\"explicit_user_request\"")
        assertThat(content).contains("\"durationMinutes\":45")
        assertThat(content).contains("\"status\":\"synced\"")
        assertThat(content).contains("\"calendarId\":42")
        assertThat(content).contains("\"eventId\":9001")
        assertThat(repository.alarms.first()).hasSize(1)
        assertThat(scheduler.scheduledAlarms).hasSize(1)
        assertThat(calendarSyncer.calls).hasSize(1)
        assertThat(calendarSyncer.calls.single().alarm).isEqualTo(repository.alarms.first().single())
        assertThat(calendarSyncer.calls.single().request).isEqualTo(
            CalendarSyncRequest(
                enabled = true,
                reason = "explicit_user_request",
                durationMinutes = 45,
            ),
        )
        assertThat(scheduler.scheduledIds).containsExactly(calendarSyncer.calls.single().alarm.id)
    }

    @Test
    fun `disabled calendar sync returns skipped without invoking calendar syncer`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val calendarSyncer = RecordingCalendarSyncer(result = CalendarSyncResult.MissingDefaultCalendar)
        val tool = createTool(repository = repository, calendarSyncer = calendarSyncer)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = validOnceArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.content.toString()).contains("\"calendarSync\":{\"enabled\":false")
        assertThat(success.result.content.toString()).contains("\"status\":\"skipped\"")
        assertThat(calendarSyncer.calls).isEmpty()
        assertThat(repository.alarms.first()).hasSize(1)
    }

    @Test
    fun `calendar permission failure is partial success without rolling back alarm`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val calendarSyncer = RecordingCalendarSyncer(result = CalendarSyncResult.MissingCalendarPermission)
        val tool = createTool(repository = repository, calendarSyncer = calendarSyncer)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = calendarEnabledArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.content.toString()).contains("\"status\":\"created\"")
        assertThat(success.result.content.toString()).contains("\"status\":\"missing_calendar_permission\"")
        assertThat(repository.alarms.first()).hasSize(1)
        assertThat(calendarSyncer.calls).hasSize(1)
    }

    @Test
    fun `missing default calendar is partial success without rolling back alarm`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val calendarSyncer = RecordingCalendarSyncer(result = CalendarSyncResult.MissingDefaultCalendar)
        val tool = createTool(repository = repository, calendarSyncer = calendarSyncer)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = calendarEnabledArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.content.toString()).contains("\"status\":\"missing_default_calendar\"")
        assertThat(repository.alarms.first()).hasSize(1)
    }

    @Test
    fun `calendar not writable is partial success without rolling back alarm`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val calendarSyncer = RecordingCalendarSyncer(result = CalendarSyncResult.CalendarNotWritable)
        val tool = createTool(repository = repository, calendarSyncer = calendarSyncer)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = calendarEnabledArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.content.toString()).contains("\"status\":\"calendar_not_writable\"")
        assertThat(repository.alarms.first()).hasSize(1)
    }

    @Test
    fun `calendar provider failure is partial success with failure reason`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val calendarSyncer = RecordingCalendarSyncer(
            result = CalendarSyncResult.CalendarInsertFailed("provider insert failed"),
        )
        val tool = createTool(repository = repository, calendarSyncer = calendarSyncer)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = calendarEnabledArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        val content = success.result.content.toString()
        assertThat(content).contains("\"status\":\"created\"")
        assertThat(content).contains("\"status\":\"calendar_insert_failed\"")
        assertThat(content).contains("\"failureReason\":\"provider insert failed\"")
        assertThat(repository.alarms.first()).hasSize(1)
    }

    @Test
    fun `calendar mapping failure is partial success with external event id and failure reason`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val calendarSyncer = RecordingCalendarSyncer(
            result = CalendarSyncResult.MappingPersistFailed(
                eventId = 9002,
                reason = "mapping write failed",
            ),
        )
        val tool = createTool(repository = repository, calendarSyncer = calendarSyncer)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = calendarEnabledArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        val content = success.result.content.toString()
        assertThat(content).contains("\"status\":\"mapping_persist_failed\"")
        assertThat(content).contains("\"eventId\":9002")
        assertThat(content).contains("\"failureReason\":\"mapping write failed\"")
        assertThat(repository.alarms.first()).hasSize(1)
    }

    @Test
    fun `validation failure fails before repository write`() = runTest {
        val repository = RecordingAlarmRepository(clock = clock, zoneId = zoneId)
        val tool = createTool(repository = repository)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = expiredOnceArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Failure::class.java)
        val failure = result as AgentToolExecution.Failure
        assertThat(failure.failure).isEqualTo(
            AgentFailure.ToolExecutionFailed("Alarm validation failed: EXPIRED_ONE_TIME_ALARM"),
        )
        assertThat(failure.committedResult).isNull()
        assertThat(repository.createCalls).isEqualTo(0)
    }

    @Test
    fun `clarification request returns dedicated agent failure without repository write`() = runTest {
        val repository = RecordingAlarmRepository(clock = clock, zoneId = zoneId)
        val tool = createTool(repository = repository)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = clarificationArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Failure::class.java)
        val failure = result as AgentToolExecution.Failure
        assertThat(failure.failure).isEqualTo(AgentFailure.ClarificationRequired("Which day should I use?"))
        assertThat(failure.committedResult).isNull()
        assertThat(repository.createCalls).isEqualTo(0)
    }

    @Test
    fun `clarification request missing calendar sync returns tool execution failure`() = runTest {
        val repository = RecordingAlarmRepository(clock = clock, zoneId = zoneId)
        val tool = createTool(repository = repository)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = clarificationArguments(includeCalendarSync = false),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Failure::class.java)
        val failure = result as AgentToolExecution.Failure
        assertThat(failure.failure).isEqualTo(AgentFailure.ToolExecutionFailed("calendarSync is required"))
        assertThat(failure.committedResult).isNull()
        assertThat(repository.createCalls).isEqualTo(0)
    }

    @Test
    fun `repository create failure returns dedicated create failed agent failure`() = runTest {
        val tool = createTool(repository = CreateFailingAlarmRepository())

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = validOnceArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Failure::class.java)
        val failure = result as AgentToolExecution.Failure
        assertThat(failure.failure).isEqualTo(AgentFailure.CreateFailed("database write failed"))
        assertThat(failure.committedResult).isNull()
    }

    @Test
    fun `missing exact alarm permission returns committed scheduling permission result`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val scheduler = FakeAlarmScheduler().apply {
            nextScheduleResult = ScheduleResult.MissingPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }
        val calendarSyncer = RecordingCalendarSyncer(
            result = CalendarSyncResult.Synced(
                alarmId = 1,
                calendarId = 42,
                eventId = 9001,
            ),
        )
        val tool = createTool(repository = repository, scheduler = scheduler, calendarSyncer = calendarSyncer)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = calendarEnabledArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Failure::class.java)
        val failure = result as AgentToolExecution.Failure
        assertThat(failure.failure)
            .isEqualTo(
                AgentFailure.ToolExecutionFailed(
                    "Missing scheduling permission: ${Manifest.permission.SCHEDULE_EXACT_ALARM}",
                ),
            )
        assertThat(failure.committedResult).isNotNull()
        assertThat(failure.committedResult!!.committed).isTrue()
        assertThat(failure.committedResult!!.content.toString()).contains("\"status\":\"missing_scheduling_permission\"")
        assertThat(failure.committedResult!!.content.toString()).contains(Manifest.permission.SCHEDULE_EXACT_ALARM)
        assertThat(failure.committedResult!!.content.toString()).contains("\"status\":\"skipped\"")
        assertThat(calendarSyncer.calls).isEmpty()
    }

    @Test
    fun `schedule failure returns committed schedule failure result`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val scheduler = FakeAlarmScheduler().apply {
            nextScheduleResult = ScheduleResult.Failed("scheduler unavailable")
        }
        val tool = createTool(repository = repository, scheduler = scheduler)

        val result = tool.execute(
            AgentToolCall(
                id = "call-1",
                name = "create_alarm",
                arguments = validOnceArguments(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Failure::class.java)
        val failure = result as AgentToolExecution.Failure
        assertThat(failure.failure).isEqualTo(AgentFailure.ToolExecutionFailed("scheduler unavailable"))
        assertThat(failure.committedResult).isNotNull()
        assertThat(failure.committedResult!!.committed).isTrue()
        assertThat(failure.committedResult!!.content.toString()).contains("\"status\":\"schedule_failed\"")
        assertThat(failure.committedResult!!.content.toString()).contains("\"reason\":\"scheduler unavailable\"")
    }

    private fun createTool(
        repository: AlarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId),
        scheduler: FakeAlarmScheduler = FakeAlarmScheduler(),
        calendarSyncer: CalendarAlarmSyncer = RecordingCalendarSyncer(result = CalendarSyncResult.Skipped),
    ): CreateAlarmTool = CreateAlarmTool(
        context = CreateAlarmToolContext(
            userRequest = "tomorrow morning remind me to take medicine",
            ringtoneUri = "content://settings/system/alarm_alert",
        ),
        alarmRepository = repository,
        schedulingUseCase = AlarmSchedulingUseCase(scheduler),
        calendarSyncer = calendarSyncer,
        clock = clock,
    )

    private fun calendarEnabledArguments(): String = validOnceArguments(
        calendarSync = """
          "calendarSync": {
            "enabled": true,
            "reason": "explicit_user_request",
            "durationMinutes": 45
          }
        """.trimIndent(),
    )

    private fun validOnceArguments(
        calendarSync: String = """
          "calendarSync": {
            "enabled": false,
            "reason": "none",
            "durationMinutes": 30
          }
        """.trimIndent(),
    ): String = """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 30,
          "repeatRule": {
            "type": "once",
            "daysOfWeek": [],
            "startDate": null,
            "endDate": null,
            "intervalWeeks": null
          },
          "date": "2026-04-24",
          "confidence": 0.92,
          "needsClarification": false,
          "clarificationReason": "",
          $calendarSync
        }
    """.trimIndent()

    private fun expiredOnceArguments(): String = """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 30,
          "repeatRule": {
            "type": "once",
            "daysOfWeek": [],
            "startDate": null,
            "endDate": null,
            "intervalWeeks": null
          },
          "date": "2026-04-23",
          "confidence": 0.92,
          "needsClarification": false,
          "clarificationReason": "",
          "calendarSync": {
            "enabled": false,
            "reason": "none",
            "durationMinutes": 30
          }
        }
    """.trimIndent()

    private fun clarificationArguments(includeCalendarSync: Boolean = true): String {
        val calendarSync = if (includeCalendarSync) {
            """
              ,
              "calendarSync": {
                "enabled": false,
                "reason": "none",
                "durationMinutes": 30
              }
            """.trimIndent()
        } else {
            ""
        }
        return """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 30,
          "repeatRule": {
            "type": "once",
            "daysOfWeek": [],
            "startDate": null,
            "endDate": null,
            "intervalWeeks": null
          },
          "date": "2026-04-24",
          "confidence": 0.45,
          "needsClarification": true,
          "clarificationReason": "Which day should I use?"$calendarSync
        }
    """.trimIndent()
    }

    private class RecordingAlarmRepository(
        clock: Clock,
        zoneId: ZoneId,
    ) : AlarmRepository {
        private val delegate = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        var createCalls = 0

        override val alarms: Flow<List<Alarm>> = delegate.alarms

        override suspend fun get(id: Long): Alarm? = delegate.get(id)

        override suspend fun create(draft: AlarmDraft): Alarm {
            createCalls += 1
            return delegate.create(draft)
        }

        override suspend fun update(alarm: Alarm): Alarm = delegate.update(alarm)

        override suspend fun updateFromManagement(alarm: Alarm): Alarm = delegate.updateFromManagement(alarm)

        override suspend fun enable(id: Long): Alarm? = delegate.enable(id)

        override suspend fun disable(id: Long): Alarm? = delegate.disable(id)

        override suspend fun delete(id: Long) = delegate.delete(id)
    }

    private class CreateFailingAlarmRepository : AlarmRepository {
        override val alarms: Flow<List<Alarm>> = kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun get(id: Long): Alarm? = null

        override suspend fun create(draft: AlarmDraft): Alarm = error("database write failed")

        override suspend fun update(alarm: Alarm): Alarm = alarm

        override suspend fun updateFromManagement(alarm: Alarm): Alarm = alarm

        override suspend fun enable(id: Long): Alarm? = null

        override suspend fun disable(id: Long): Alarm? = null

        override suspend fun delete(id: Long) = Unit
    }

    private class RecordingCalendarSyncer(
        private val result: CalendarSyncResult,
    ) : CalendarAlarmSyncer {
        val calls = mutableListOf<Call>()

        override suspend fun sync(
            alarm: Alarm,
            request: CalendarSyncRequest,
        ): CalendarSyncResult {
            calls += Call(alarm, request)
            return result
        }

        data class Call(
            val alarm: Alarm,
            val request: CalendarSyncRequest,
        )
    }
}
