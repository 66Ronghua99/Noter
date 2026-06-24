package com.cory.noter.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.cory.noter.NoterApplication
import com.cory.noter.agent.AgentLlmGateway
import com.cory.noter.agent.AgentLoopRunner
import com.cory.noter.ai.AndroidOpenRouterDebugLogger
import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.AiCreateBackgroundScheduler
import com.cory.noter.ai.AiCreateResultNotifier
import com.cory.noter.ai.WorkManagerAiCreateBackgroundScheduler
import com.cory.noter.ai.OpenRouterAgentClient
import com.cory.noter.ai.OpenRouterAsrClient
import com.cory.noter.alarm.AlarmRingingCoordinator
import com.cory.noter.alarm.AlarmScheduler
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.AndroidAlarmScheduler
import com.cory.noter.alarm.StartupReconciliation
import com.cory.noter.data.alarm.AlarmDatabase
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.alarm.RoomAlarmRepository
import com.cory.noter.data.settings.DataStoreSettingsRepository
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.notifications.AndroidAiCreateResultNotifier
import com.cory.noter.permissions.AndroidPermissionStatusReader
import com.cory.noter.permissions.PermissionStatusReader
import com.cory.noter.voice.AndroidMicrophonePermissionChecker
import com.cory.noter.voice.AndroidSystemSpeechRecognizer
import com.cory.noter.voice.AndroidTemporaryAudioRecorder
import com.cory.noter.voice.BackgroundVoiceAiCreateEnqueuer
import com.cory.noter.voice.FileTemporaryAudioCleanup
import com.cory.noter.voice.MicrophonePermissionChecker
import com.cory.noter.voice.OpenRouterVoiceAsrTranscriber
import com.cory.noter.voice.RemoteAsrTranscriber
import com.cory.noter.voice.SystemSpeechRecognizer
import com.cory.noter.voice.TemporaryAudioCleanup
import com.cory.noter.voice.TemporaryAudioRecorder
import com.cory.noter.voice.VoiceAiCreateEnqueuer
import com.cory.noter.voice.VoiceCaptureController
import com.cory.noter.voice.VoiceCaptureCoordinator

class AppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    val database: AlarmDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AlarmDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(AlarmDatabase.MIGRATION_1_2)
            .build()
    }

    val alarmRepository: AlarmRepository by lazy {
        RoomAlarmRepository(database.alarmDao())
    }

    private val settingsDataStore by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = {
                applicationContext.preferencesDataStoreFile(SETTINGS_FILE_NAME)
            },
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(settingsDataStore)
    }

    val permissionStatusReader: PermissionStatusReader by lazy {
        AndroidPermissionStatusReader(applicationContext)
    }

    val alarmScheduler: AlarmScheduler by lazy {
        AndroidAlarmScheduler(
            context = applicationContext,
            permissionStatusReader = permissionStatusReader,
        )
    }

    val alarmSchedulingUseCase: AlarmSchedulingUseCase by lazy {
        AlarmSchedulingUseCase(alarmScheduler)
    }

    val openRouterAgentClient: AgentLlmGateway by lazy {
        OpenRouterAgentClient(
            debugLogger = AndroidOpenRouterDebugLogger(
                enabled = applicationContext.isDebuggableApplication(),
            ),
        )
    }

    val agentLoopRunner: AgentLoopRunner by lazy {
        AgentLoopRunner(openRouterAgentClient)
    }

    val aiAlarmCreator: AiAlarmCreator by lazy {
        AiAlarmCreator(
            settingsRepository = settingsRepository,
            agentLoopRunner = agentLoopRunner,
            alarmRepository = alarmRepository,
            schedulingUseCase = alarmSchedulingUseCase,
        )
    }

    val aiCreateResultNotifier: AiCreateResultNotifier by lazy {
        AndroidAiCreateResultNotifier(applicationContext)
    }

    val aiCreateBackgroundScheduler: AiCreateBackgroundScheduler by lazy {
        WorkManagerAiCreateBackgroundScheduler(applicationContext)
    }

    val microphonePermissionChecker: MicrophonePermissionChecker by lazy {
        AndroidMicrophonePermissionChecker(applicationContext)
    }

    val temporaryAudioRecorder: TemporaryAudioRecorder by lazy {
        AndroidTemporaryAudioRecorder(applicationContext)
    }

    val systemSpeechRecognizer: SystemSpeechRecognizer by lazy {
        AndroidSystemSpeechRecognizer(applicationContext)
    }

    val openRouterAsrClient: OpenRouterAsrClient by lazy {
        OpenRouterAsrClient(
            debugLogger = AndroidOpenRouterDebugLogger(
                enabled = applicationContext.isDebuggableApplication(),
            ),
        )
    }

    val remoteAsrTranscriber: RemoteAsrTranscriber by lazy {
        OpenRouterVoiceAsrTranscriber(openRouterAsrClient)
    }

    val temporaryAudioCleanup: TemporaryAudioCleanup by lazy {
        FileTemporaryAudioCleanup()
    }

    val voiceAiCreateEnqueuer: VoiceAiCreateEnqueuer by lazy {
        BackgroundVoiceAiCreateEnqueuer(aiCreateBackgroundScheduler)
    }

    val voiceCaptureController: VoiceCaptureController by lazy {
        VoiceCaptureCoordinator(
            settingsRepository = settingsRepository,
            temporaryAudioRecorder = temporaryAudioRecorder,
            systemSpeechRecognizer = systemSpeechRecognizer,
            remoteAsrTranscriber = remoteAsrTranscriber,
            temporaryAudioCleanup = temporaryAudioCleanup,
            aiCreateEnqueuer = voiceAiCreateEnqueuer,
        )
    }

    val alarmRingingCoordinator: AlarmRingingCoordinator by lazy {
        AlarmRingingCoordinator(
            repository = alarmRepository,
            schedulingUseCase = alarmSchedulingUseCase,
        )
    }

    val startupReconciliation: StartupReconciliation by lazy {
        StartupReconciliation(
            repository = alarmRepository,
            schedulingUseCase = alarmSchedulingUseCase,
        )
    }

    private companion object {
        const val DATABASE_NAME = "noter.db"
        const val SETTINGS_FILE_NAME = "noter_settings.preferences_pb"
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as NoterApplication).appContainer

private fun Context.isDebuggableApplication(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
