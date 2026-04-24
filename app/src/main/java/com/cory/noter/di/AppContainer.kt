package com.cory.noter.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.cory.noter.NoterApplication
import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.OpenRouterClient
import com.cory.noter.ai.OpenRouterGateway
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
import com.cory.noter.permissions.AndroidPermissionStatusReader
import com.cory.noter.permissions.PermissionStatusReader

class AppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    val database: AlarmDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AlarmDatabase::class.java,
            DATABASE_NAME,
        ).build()
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

    val openRouterClient: OpenRouterGateway by lazy {
        OpenRouterClient()
    }

    val aiAlarmCreator: AiAlarmCreator by lazy {
        AiAlarmCreator(
            settingsRepository = settingsRepository,
            openRouterClient = openRouterClient,
            alarmRepository = alarmRepository,
            schedulingUseCase = alarmSchedulingUseCase,
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
