package com.cory.noter.agent.tools.alarm

import android.Manifest
import com.cory.noter.agent.AgentFailure
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolExecution
import com.cory.noter.agent.AgentToolRisk
import com.cory.noter.alarm.AlarmManagementUseCase
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.ui.FakeAlarmRepository
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class AlarmManagementToolsTest {
    private val zoneId = ZoneId.of("Asia/Hong_Kong")
    private val clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), zoneId)

    @Test
    fun `list alarms returns required management fields`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val alarm = repository.create(activeDailyDraft())
        val tool = ListAlarmsTool(
            alarmRepository = repository,
            zoneIdProvider = { zoneId },
        )

        val result = tool.execute(AgentToolCall("call-list", "list_alarms", "{}"))

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isFalse()
        assertThat(success.result.content["status"]!!.jsonPrimitive.content).isEqualTo("listed_alarms")
        val listed = success.result.content["alarms"]!!.jsonArray.single().jsonObject
        assertThat(listed["id"]!!.jsonPrimitive.content).isEqualTo(alarm.id.toString())
        assertThat(listed["title"]!!.jsonPrimitive.content).isEqualTo("Take medicine")
        assertThat(listed["localTime"]!!.jsonPrimitive.content).isEqualTo("08:30")
        assertThat(listed["repeatSummary"]!!.jsonPrimitive.content).isEqualTo("daily")
        assertThat(listed["nextTriggerAtMillis"]!!.jsonPrimitive.content)
            .isEqualTo(alarm.nextTriggerAtMillis.toString())
        assertThat(listed["pauseState"]!!.jsonPrimitive.content).isEqualTo("none")
    }

    @Test
    fun `pause alarm next occurrence uses shared management use case`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val alarm = repository.create(activeDailyDraft())
        val scheduler = FakeAlarmScheduler()
        val tool = PauseAlarmTool(managementUseCase(repository, scheduler))

        val result = tool.execute(
            AgentToolCall(
                id = "call-pause",
                name = "pause_alarm",
                arguments = """{"alarmId":${alarm.id},"mode":"next_occurrence"}""",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isTrue()
        assertThat(success.result.content["status"]!!.jsonPrimitive.content)
            .isEqualTo("paused_next_occurrence")
        val stored = repository.get(alarm.id)!!
        assertThat(stored.pauseMode).isEqualTo(AlarmPauseMode.NEXT_OCCURRENCE)
        assertThat(stored.pausedOccurrenceAtMillis).isEqualTo(alarm.nextTriggerAtMillis)
    }

    @Test
    fun `pause alarm indefinitely uses shared management use case`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val alarm = repository.create(activeDailyDraft())
        val scheduler = FakeAlarmScheduler()
        val tool = PauseAlarmTool(managementUseCase(repository, scheduler))

        val result = tool.execute(
            AgentToolCall(
                id = "call-pause",
                name = "pause_alarm",
                arguments = """{"alarmId":${alarm.id},"mode":"indefinite"}""",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.content["status"]!!.jsonPrimitive.content)
            .isEqualTo("paused_indefinitely")
        assertThat(repository.get(alarm.id)!!.pauseMode).isEqualTo(AlarmPauseMode.INDEFINITE)
        assertThat(scheduler.cancelledIds).contains(alarm.id)
    }

    @Test
    fun `pause alarm rejects unknown mode before management mutation`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val alarm = repository.create(activeDailyDraft())
        val tool = PauseAlarmTool(managementUseCase(repository, FakeAlarmScheduler()))

        val result = tool.execute(
            AgentToolCall(
                id = "call-pause",
                name = "pause_alarm",
                arguments = """{"alarmId":${alarm.id},"mode":"foreverish"}""",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Failure::class.java)
        val failure = result as AgentToolExecution.Failure
        assertThat(failure.failure).isEqualTo(
            AgentFailure.CorrectableToolFailure("pause_alarm mode must be one of next_occurrence, indefinite."),
        )
        assertThat(repository.get(alarm.id)!!.pauseMode).isEqualTo(AlarmPauseMode.NONE)
    }

    @Test
    fun `resume alarm reports missing alarm explicitly`() = runTest {
        val tool = ResumeAlarmTool(
            managementUseCase(
                repository = FakeAlarmRepository(clock = clock, zoneId = zoneId),
                scheduler = FakeAlarmScheduler(),
            ),
        )

        val result = tool.execute(
            AgentToolCall(
                id = "call-resume",
                name = "resume_alarm",
                arguments = """{"alarmId":404}""",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isFalse()
        assertThat(success.result.content["status"]!!.jsonPrimitive.content).isEqualTo("missing_alarm")
        assertThat(success.result.content["alarmId"]!!.jsonPrimitive.content).isEqualTo("404")
    }

    @Test
    fun `resume alarm uses shared management use case`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val alarm = repository.create(activeDailyDraft())
        val scheduler = FakeAlarmScheduler()
        val managementUseCase = managementUseCase(repository, scheduler)
        managementUseCase.pauseIndefinitely(alarm.id)
        val tool = ResumeAlarmTool(managementUseCase)

        val result = tool.execute(
            AgentToolCall(
                id = "call-resume",
                name = "resume_alarm",
                arguments = """{"alarmId":${alarm.id}}""",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isTrue()
        assertThat(success.result.content["status"]!!.jsonPrimitive.content).isEqualTo("resumed")
        assertThat(repository.get(alarm.id)!!.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(scheduler.scheduledIds).contains(alarm.id)
    }

    @Test
    fun `resume alarm missing scheduling permission commits persisted resume state`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val alarm = repository.create(activeDailyDraft())
        val scheduler = FakeAlarmScheduler()
        val managementUseCase = managementUseCase(repository, scheduler)
        managementUseCase.pauseIndefinitely(alarm.id)
        scheduler.nextScheduleResult = ScheduleResult.MissingPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        val tool = ResumeAlarmTool(managementUseCase)

        val result = tool.execute(
            AgentToolCall(
                id = "call-resume",
                name = "resume_alarm",
                arguments = """{"alarmId":${alarm.id}}""",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isTrue()
        assertThat(success.result.content["status"]!!.jsonPrimitive.content)
            .isEqualTo("missing_scheduling_permission")
        assertThat(success.result.content["permission"]!!.jsonPrimitive.content)
            .isEqualTo(Manifest.permission.SCHEDULE_EXACT_ALARM)
        val stored = repository.get(alarm.id)!!
        assertThat(stored.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(stored.enabled).isTrue()
    }

    @Test
    fun `management scheduler permission failure is explicit`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val alarm = repository.create(activeDailyDraft())
        val scheduler = FakeAlarmScheduler().apply {
            nextCancelResult = ScheduleResult.MissingPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }
        val tool = PauseAlarmTool(managementUseCase(repository, scheduler))

        val result = tool.execute(
            AgentToolCall(
                id = "call-pause",
                name = "pause_alarm",
                arguments = """{"alarmId":${alarm.id},"mode":"indefinite"}""",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isFalse()
        assertThat(success.result.content["status"]!!.jsonPrimitive.content)
            .isEqualTo("missing_scheduling_permission")
        assertThat(success.result.content["permission"]!!.jsonPrimitive.content)
            .isEqualTo(Manifest.permission.SCHEDULE_EXACT_ALARM)
    }

    @Test
    fun `tool schemas expose expected names and risks`() {
        assertThat(ListAlarmsTool(FakeAlarmRepository(clock = clock, zoneId = zoneId)).spec.name)
            .isEqualTo("list_alarms")
        assertThat(PauseAlarmTool(managementUseCase(FakeAlarmRepository(clock = clock, zoneId = zoneId), FakeAlarmScheduler())).spec.risk)
            .isEqualTo(AgentToolRisk.WRITE)
        assertThat(ResumeAlarmTool(managementUseCase(FakeAlarmRepository(clock = clock, zoneId = zoneId), FakeAlarmScheduler())).spec.risk)
            .isEqualTo(AgentToolRisk.WRITE)
    }

    private fun managementUseCase(
        repository: FakeAlarmRepository,
        scheduler: FakeAlarmScheduler,
    ) = AlarmManagementUseCase(
        repository = repository,
        schedulingUseCase = AlarmSchedulingUseCase(scheduler),
        clock = clock,
        nextTriggerCalculator = NextTriggerCalculator(),
        zoneIdProvider = { zoneId },
    )

    private fun activeDailyDraft() = AlarmDraft(
        title = "Take medicine",
        hour = 8,
        minute = 30,
        repeatRule = RepeatRule.Daily,
        enabled = true,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.AI,
        aiOriginalText = "take medicine",
    )
}
