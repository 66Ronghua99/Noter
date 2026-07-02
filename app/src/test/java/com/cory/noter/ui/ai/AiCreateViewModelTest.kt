package com.cory.noter.ui.ai

import com.cory.noter.R
import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentLoopRunner
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.AiAlarmPromptBuilder
import com.cory.noter.ai.AsrModel
import com.cory.noter.alarm.AlarmManagementUseCase
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.calendar.CalendarAlarmSyncer
import com.cory.noter.calendar.CalendarSyncResult
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.ui.FakeAlarmRepository
import com.cory.noter.ui.FakeAgentLlmGateway
import com.cory.noter.ui.MainDispatcherRule
import com.cory.noter.ui.text.UiText
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test

class AiCreateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), zoneId)

    @Test
    fun `missing api key becomes explicit settings error`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = settingsRepository,
                agentLoopRunner = AgentLoopRunner(FakeAgentLlmGateway()),
                alarmRepository = alarmRepository,
                schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
                managementUseCase = managementUseCase(alarmRepository),
                promptBuilder = AiAlarmPromptBuilder(),
                clock = clock,
            ),
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("wake me up tomorrow at 8")
        viewModel.submit()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(UiText.Resource(R.string.ai_create_missing_api_key_error))
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `missing exact alarm permission exposes permission action`() = runTest {
        val settingsRepository = FakeSettingsRepository(
            initialSettings = AppSettings(
                openRouterApiKey = "sk-or-v1-test",
                selectedModelId = "deepseek/deepseek-v3.2",
                selectedAsrModelId = AsrModel.DefaultId,
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
            ),
        )
        val agentGateway = FakeAgentLlmGateway().apply {
            results += AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(
                        AgentToolCall(
                            id = "call-1",
                            name = "create_alarm",
                            arguments = validAlarmJson(),
                        ),
                    ),
                ),
            )
        }
        val scheduler = FakeAlarmScheduler().apply {
            nextScheduleResult = ScheduleResult.MissingPermission(
                android.Manifest.permission.SCHEDULE_EXACT_ALARM,
            )
        }
        val alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = settingsRepository,
                agentLoopRunner = AgentLoopRunner(agentGateway),
                alarmRepository = alarmRepository,
                schedulingUseCase = AlarmSchedulingUseCase(scheduler),
                managementUseCase = managementUseCase(alarmRepository, scheduler),
                promptBuilder = AiAlarmPromptBuilder(),
                clock = clock,
            ),
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("tomorrow at 8 am remind me to take medicine")
        viewModel.submit()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.exactAlarmPermissionRequired).isTrue()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(
                UiText.Resource(
                    R.string.ai_create_missing_permission_error,
                    listOf(android.Manifest.permission.SCHEDULE_EXACT_ALARM),
                ),
            )
    }

    @Test
    fun `submit queues background creation when scheduler is available`() = runTest {
        val backgroundScheduler = RecordingBackgroundScheduler()
        val alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = FakeSettingsRepository(),
                agentLoopRunner = AgentLoopRunner(FakeAgentLlmGateway()),
                alarmRepository = alarmRepository,
                schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
                managementUseCase = managementUseCase(alarmRepository),
                promptBuilder = AiAlarmPromptBuilder(),
                clock = clock,
            ),
            settingsRepository = FakeSettingsRepository(),
            backgroundScheduler = backgroundScheduler,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("tomorrow at 8 am remind me to take medicine")
        viewModel.submit()

        assertThat(backgroundScheduler.prompts)
            .containsExactly("tomorrow at 8 am remind me to take medicine")
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.statusMessage)
            .isEqualTo(UiText.Resource(R.string.ai_create_background_status))
    }

    @Test
    fun `list only result becomes visible status instead of error`() = runTest {
        val settingsRepository = FakeSettingsRepository(
            initialSettings = AppSettings(
                openRouterApiKey = "sk-or-v1-test",
                selectedModelId = "deepseek/deepseek-v3.2",
                selectedAsrModelId = AsrModel.DefaultId,
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
            ),
        )
        val agentGateway = FakeAgentLlmGateway().apply {
            results += AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(
                        AgentToolCall(
                            id = "call-list",
                            name = "list_alarms",
                            arguments = "{}",
                        ),
                    ),
                ),
            )
            results += AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(
                        AgentToolCall(
                            id = "call-end",
                            name = "end_task",
                            arguments = """{"reason":"Alarms listed."}""",
                        ),
                    ),
                ),
            )
        }
        val alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId).apply {
            create(
                com.cory.noter.data.alarm.AlarmDraft(
                    title = "Take medicine",
                    hour = 8,
                    minute = 0,
                    repeatRule = com.cory.noter.domain.alarm.RepeatRule.Daily,
                    enabled = true,
                    ringtoneUri = AppSettings.DefaultRingtoneUri,
                    source = com.cory.noter.domain.alarm.AlarmSource.AI,
                    aiOriginalText = "take medicine",
                ),
            )
        }
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = settingsRepository,
                agentLoopRunner = AgentLoopRunner(agentGateway),
                alarmRepository = alarmRepository,
                schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
                managementUseCase = managementUseCase(alarmRepository),
                promptBuilder = AiAlarmPromptBuilder(),
                clock = clock,
            ),
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("list my alarms")
        viewModel.submit()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isNull()
        val status = viewModel.uiState.value.statusMessage as UiText.Raw
        assertThat(status.value).contains("Listed 1 alarm:")
        assertThat(status.value).contains("Take medicine")
        assertThat(status.value).contains("08:00")
        assertThat(status.value).contains("daily")
        assertThat(status.value).contains("pause: none")
    }

    @Test
    fun `created alarm with synced calendar becomes synced status message`() = runTest {
        val settingsRepository = validSettingsRepository()
        val agentGateway = FakeAgentLlmGateway().apply {
            results += createAlarmTurn(enabledCalendarAlarmJson())
            results += endTaskTurn()
        }
        val alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = settingsRepository,
                agentLoopRunner = AgentLoopRunner(agentGateway),
                alarmRepository = alarmRepository,
                schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
                calendarSyncer = CalendarAlarmSyncer { alarm, _ ->
                    CalendarSyncResult.Synced(
                        alarmId = alarm.id,
                        calendarId = 42,
                        eventId = 9001,
                    )
                },
                managementUseCase = managementUseCase(alarmRepository),
                promptBuilder = AiAlarmPromptBuilder(),
                clock = clock,
            ),
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("tomorrow at 8 am remind me to take medicine and add it to my calendar")
        viewModel.submit()
        advanceUntilIdle()

        val createdAlarm = alarmRepository.alarms.first().single()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
        assertThat(viewModel.uiState.value.createdAlarmId).isEqualTo(createdAlarm.id)
        assertThat(viewModel.uiState.value.statusMessage)
            .isEqualTo(UiText.Resource(R.string.ai_create_created_synced_status, listOf("Take medicine")))
    }

    @Test
    fun `created alarm with calendar failure becomes partial success status message`() = runTest {
        val settingsRepository = validSettingsRepository()
        val agentGateway = FakeAgentLlmGateway().apply {
            results += createAlarmTurn(enabledCalendarAlarmJson())
            results += endTaskTurn()
        }
        val alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = settingsRepository,
                agentLoopRunner = AgentLoopRunner(agentGateway),
                alarmRepository = alarmRepository,
                schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
                calendarSyncer = CalendarAlarmSyncer { _, _ ->
                    CalendarSyncResult.CalendarInsertFailed("provider insert failed")
                },
                managementUseCase = managementUseCase(alarmRepository),
                promptBuilder = AiAlarmPromptBuilder(),
                clock = clock,
            ),
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("tomorrow at 8 am remind me to take medicine and add it to my calendar")
        viewModel.submit()
        advanceUntilIdle()

        val createdAlarm = alarmRepository.alarms.first().single()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
        assertThat(viewModel.uiState.value.createdAlarmId).isEqualTo(createdAlarm.id)
        assertThat(viewModel.uiState.value.statusMessage)
            .isEqualTo(
                UiText.Resource(
                    R.string.ai_create_created_calendar_failed_status,
                    listOf("Take medicine", "provider insert failed"),
                ),
            )
    }

    @Test
    fun `created alarm calendar setup failures use actionable status text instead of raw codes`() = runTest {
        val cases = listOf(
            CalendarSyncResult.MissingCalendarPermission to
                R.string.ai_create_created_calendar_missing_permission_status,
            CalendarSyncResult.MissingDefaultCalendar to
                R.string.ai_create_created_calendar_missing_default_status,
            CalendarSyncResult.CalendarNotWritable to
                R.string.ai_create_created_calendar_not_writable_status,
        )

        for ((syncResult, expectedMessage) in cases) {
            val settingsRepository = validSettingsRepository()
            val agentGateway = FakeAgentLlmGateway().apply {
                results += createAlarmTurn(enabledCalendarAlarmJson())
                results += endTaskTurn()
            }
            val alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
            val viewModel = AiCreateViewModel(
                creator = AiAlarmCreator(
                    settingsRepository = settingsRepository,
                    agentLoopRunner = AgentLoopRunner(agentGateway),
                    alarmRepository = alarmRepository,
                    schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
                    calendarSyncer = CalendarAlarmSyncer { _, _ -> syncResult },
                    managementUseCase = managementUseCase(alarmRepository),
                    promptBuilder = AiAlarmPromptBuilder(),
                    clock = clock,
                ),
                settingsRepository = settingsRepository,
            )

            advanceUntilIdle()
            viewModel.onPromptChanged("tomorrow at 8 am remind me to take medicine and add it to my calendar")
            viewModel.submit()
            advanceUntilIdle()

            val statusMessage = viewModel.uiState.value.statusMessage
            assertThat(statusMessage).isEqualTo(
                UiText.Resource(
                    expectedMessage,
                    listOf("Take medicine"),
                ),
            )
            assertThat(statusMessage.toString()).doesNotContain("missing_calendar_permission")
            assertThat(statusMessage.toString()).doesNotContain("missing_default_calendar")
            assertThat(statusMessage.toString()).doesNotContain("calendar_not_writable")
        }
    }

    private class RecordingBackgroundScheduler : com.cory.noter.ai.AiCreateBackgroundScheduler {
        val prompts = mutableListOf<String>()

        override fun enqueue(prompt: String) {
            prompts += prompt
        }
    }

    private fun managementUseCase(
        repository: FakeAlarmRepository,
        scheduler: FakeAlarmScheduler = FakeAlarmScheduler(),
    ) = AlarmManagementUseCase(
        repository = repository,
        schedulingUseCase = AlarmSchedulingUseCase(scheduler),
        clock = clock,
        nextTriggerCalculator = NextTriggerCalculator(),
        zoneIdProvider = { zoneId },
    )

    private fun validAlarmJson(): String = """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 0,
          "repeatRule": { "type": "once", "daysOfWeek": [] },
          "date": "2026-04-24",
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

    private fun enabledCalendarAlarmJson(): String = """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 0,
          "repeatRule": { "type": "once", "daysOfWeek": [] },
          "date": "2026-04-24",
          "confidence": 0.92,
          "needsClarification": false,
          "clarificationReason": "",
          "calendarSync": {
            "enabled": true,
            "reason": "explicit_user_request",
            "durationMinutes": 45
          }
        }
    """.trimIndent()

    private fun createAlarmTurn(arguments: String): AgentLlmResult.Message = AgentLlmResult.Message(
        AgentMessage(
            role = AgentMessageRole.ASSISTANT,
            content = "",
            toolCalls = listOf(
                AgentToolCall(
                    id = "call-1",
                    name = "create_alarm",
                    arguments = arguments,
                ),
            ),
        ),
    )

    private fun endTaskTurn(): AgentLlmResult.Message = AgentLlmResult.Message(
        AgentMessage(
            role = AgentMessageRole.ASSISTANT,
            content = "",
            toolCalls = listOf(
                AgentToolCall(
                    id = "call-end",
                    name = "end_task",
                    arguments = """{"reason":"Alarm created."}""",
                ),
            ),
        ),
    )

    private fun validSettingsRepository(): FakeSettingsRepository = FakeSettingsRepository(
        initialSettings = AppSettings(
            openRouterApiKey = "sk-or-v1-test",
            selectedModelId = "deepseek/deepseek-v3.2",
            selectedAsrModelId = AsrModel.DefaultId,
            defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
        ),
    )
}
