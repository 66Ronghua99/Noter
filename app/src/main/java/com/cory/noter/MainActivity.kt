package com.cory.noter

import android.Manifest
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cory.noter.ai.AsrModel
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.di.AppContainer
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.ui.NoterApp
import com.cory.noter.ui.ai.AiCreateScreen
import com.cory.noter.ui.ai.AiCreateViewModel
import com.cory.noter.ui.alarm_list.AlarmListScreen
import com.cory.noter.ui.alarm_list.AlarmListViewModel
import com.cory.noter.ui.editor.AlarmEditorScreen
import com.cory.noter.ui.editor.AlarmEditorViewModel
import com.cory.noter.ui.settings.SettingsScreen
import com.cory.noter.ui.settings.SettingsViewModel
import com.cory.noter.ui.theme.NoterTheme
import com.cory.noter.ui.voice.VoiceHomeScreen
import com.cory.noter.ui.voice.VoiceHomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as NoterApplication).appContainer

        setContent {
            val settings by appContainer.settingsRepository.settings.collectAsState(
                initial = AppSettings(
                    openRouterApiKey = "",
                    selectedModelId = OpenRouterModel.DefaultId,
                    selectedAsrModelId = AsrModel.DefaultId,
                    defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
                ),
            )
            NoterTheme(settings = settings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoterRoot(
                        appContainer = appContainer,
                        notificationPermissionProvider = ::notificationPermissionGranted,
                        batteryOptimizationIgnoredProvider = ::batteryOptimizationIgnored,
                        onOpenExactAlarmSettings = ::openExactAlarmSettings,
                    )
                }
            }
        }
    }

    private fun notificationPermissionGranted(): Boolean = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> true
        else -> ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun batteryOptimizationIgnored(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openExactAlarmSettings() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            },
        )
    }
}

@Composable
private fun NoterRoot(
    appContainer: AppContainer,
    notificationPermissionProvider: () -> Boolean,
    batteryOptimizationIgnoredProvider: () -> Boolean,
    onOpenExactAlarmSettings: () -> Unit,
) {
    NoterApp(
        voiceHomeScreen = { onOpenAlarmList, onOpenSettings, onOpenTextInput ->
            VoiceHomeRoute(
                appContainer = appContainer,
                onOpenAlarmList = onOpenAlarmList,
                onOpenSettings = onOpenSettings,
                onOpenTextInput = onOpenTextInput,
            )
        },
        alarmListScreen = { onOpenManualCreate, onOpenAiCreate, onEditAlarm, onOpenSettings ->
            AlarmListRoute(
                appContainer = appContainer,
                onOpenManualCreate = onOpenManualCreate,
                onOpenAiCreate = onOpenAiCreate,
                onEditAlarm = onEditAlarm,
                onOpenSettings = onOpenSettings,
            )
        },
        alarmEditorScreen = { alarmId, onDone ->
            AlarmEditorRoute(
                appContainer = appContainer,
                alarmId = alarmId,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onDone = onDone,
            )
        },
        aiCreateScreen = { onBack, onOpenManualCreate ->
            AiCreateRoute(
                appContainer = appContainer,
                onBack = onBack,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenManualCreate = onOpenManualCreate,
            )
        },
        settingsScreen = { onBack ->
            SettingsRoute(
                appContainer = appContainer,
                notificationPermissionProvider = notificationPermissionProvider,
                batteryOptimizationIgnoredProvider = batteryOptimizationIgnoredProvider,
                onBack = onBack,
            )
        },
    )
}

@Composable
private fun VoiceHomeRoute(
    appContainer: AppContainer,
    onOpenAlarmList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTextInput: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: VoiceHomeViewModel = viewModel(
        factory = factoryOf {
            VoiceHomeViewModel(
                microphonePermissionChecker = appContainer.microphonePermissionChecker,
                captureController = appContainer.voiceCaptureController,
            )
        },
    )
    val state by viewModel.uiState.collectAsState()
    var recordPressActive by remember { mutableStateOf(false) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        when (resolveMicrophonePermissionResult(granted, recordPressActive)) {
            VoiceMicrophonePermissionAction.StartCapture,
            VoiceMicrophonePermissionAction.ShowPermissionNeeded,
            -> viewModel.onRecordPressed()

            VoiceMicrophonePermissionAction.WaitForNewPress -> Unit
        }
    }

    VoiceHomeScreen(
        state = state,
        onRecordPressed = {
            recordPressActive = true
            if (appContainer.microphonePermissionChecker.isGranted()) {
                viewModel.onRecordPressed()
            } else {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onRecordReleased = {
            recordPressActive = false
            viewModel.onRecordReleased()
        },
        onRecordCancelled = {
            recordPressActive = false
            viewModel.onRecordCancelled()
        },
        onRetry = viewModel::onRetry,
        onOpenPermissionSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        },
        onOpenTextInput = onOpenTextInput,
        onOpenAlarmList = onOpenAlarmList,
        onOpenSettings = onOpenSettings,
    )
}

internal enum class VoiceMicrophonePermissionAction {
    StartCapture,
    ShowPermissionNeeded,
    WaitForNewPress,
}

internal fun resolveMicrophonePermissionResult(
    granted: Boolean,
    recordPressActive: Boolean,
): VoiceMicrophonePermissionAction = when {
    !granted -> VoiceMicrophonePermissionAction.ShowPermissionNeeded
    recordPressActive -> VoiceMicrophonePermissionAction.StartCapture
    else -> VoiceMicrophonePermissionAction.WaitForNewPress
}

@Composable
private fun AlarmListRoute(
    appContainer: AppContainer,
    onOpenManualCreate: () -> Unit,
    onOpenAiCreate: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: AlarmListViewModel = viewModel(
        factory = factoryOf {
            AlarmListViewModel(
                repository = appContainer.alarmRepository,
                schedulingUseCase = appContainer.alarmSchedulingUseCase,
            )
        },
    )
    val state by viewModel.uiState.collectAsState()

    AlarmListScreen(
        state = state,
        onAlarmEnabledChanged = viewModel::onAlarmEnabledChanged,
        onEditAlarm = onEditAlarm,
        onDeleteAlarm = viewModel::onDeleteAlarm,
        onOpenSettings = onOpenSettings,
        onOpenManualCreate = onOpenManualCreate,
        onOpenAiCreate = onOpenAiCreate,
    )
}

@Composable
private fun AlarmEditorRoute(
    appContainer: AppContainer,
    alarmId: Long?,
    onOpenExactAlarmSettings: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: AlarmEditorViewModel = viewModel(
        key = "editor-$alarmId",
        factory = factoryOf {
            AlarmEditorViewModel(
                alarmId = alarmId,
                repository = appContainer.alarmRepository,
                settingsRepository = appContainer.settingsRepository,
                schedulingUseCase = appContainer.alarmSchedulingUseCase,
            )
        },
    )
    val state by viewModel.uiState.collectAsState()
    val ringtonePickerTitle = stringResource(R.string.ringtone_picker_alarm_title)
    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        extractPickedRingtoneUri(result.data)?.let { pickedUri ->
            viewModel.onRingtoneSelected(pickedUri.toString())
        }
    }

    LaunchedEffect(state.savedAlarmId, state.deleted) {
        if (state.savedAlarmId != null || state.deleted) {
            onDone()
        }
    }

    AlarmEditorScreen(
        state = state,
        onTitleChanged = viewModel::onTitleChanged,
        onHourSelected = viewModel::onHourSelected,
        onMinuteSelected = viewModel::onMinuteSelected,
        onRepeatRuleChanged = viewModel::onRepeatRuleChanged,
        onOnceDateSelected = viewModel::onOnceDateSelected,
        onIntervalStartDateSelected = viewModel::onIntervalStartDateSelected,
        onIntervalEndDateSelected = viewModel::onIntervalEndDateSelected,
        onIntervalWeeksSelected = viewModel::onIntervalWeeksSelected,
        onCustomWeekdayToggled = viewModel::onCustomWeekdayToggled,
        onPickRingtone = {
            ringtonePicker.launch(
                createRingtonePickerIntent(
                    currentRingtoneUri = state.ringtoneUri,
                    title = ringtonePickerTitle,
                ),
            )
        },
        onEnabledChanged = viewModel::onEnabledChanged,
        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@Composable
private fun AiCreateRoute(
    appContainer: AppContainer,
    onBack: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenManualCreate: () -> Unit,
) {
    val viewModel: AiCreateViewModel = viewModel(
        factory = factoryOf {
            AiCreateViewModel(
                creator = appContainer.aiAlarmCreator,
                settingsRepository = appContainer.settingsRepository,
                backgroundScheduler = appContainer.aiCreateBackgroundScheduler,
            )
        },
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdAlarmId) {
        if (state.createdAlarmId != null) {
            onBack()
        }
    }

    AiCreateScreen(
        state = state,
        onPromptChanged = viewModel::onPromptChanged,
        onSubmit = viewModel::submit,
        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        onOpenManualCreate = onOpenManualCreate,
        onBack = onBack,
    )
}

@Composable
private fun SettingsRoute(
    appContainer: AppContainer,
    notificationPermissionProvider: () -> Boolean,
    batteryOptimizationIgnoredProvider: () -> Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = factoryOf {
            SettingsViewModel(
                settingsRepository = appContainer.settingsRepository,
                exactAlarmPermissionReader = appContainer.permissionStatusReader,
                notificationPermissionProvider = notificationPermissionProvider,
                batteryOptimizationIgnoredProvider = batteryOptimizationIgnoredProvider,
            )
        },
    )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissionRows()
    }
    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshPermissionRows()
        (context.applicationContext as? NoterApplication)?.reconcileStartupState()
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshPermissionRows()
        (context.applicationContext as? NoterApplication)?.reconcileStartupState()
    }
    val state by viewModel.uiState.collectAsState()
    val ringtonePickerTitle = stringResource(R.string.ringtone_picker_default_alarm_title)
    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        extractPickedRingtoneUri(result.data)?.let { pickedUri ->
            viewModel.onDefaultRingtoneSelected(pickedUri.toString())
        }
    }

    SettingsScreen(
        state = state,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSaveApiKey = viewModel::saveApiKey,
        onModelSelected = viewModel::onModelSelected,
        onAsrModelSelected = viewModel::onAsrModelSelected,
        onPickDefaultRingtone = {
            ringtonePicker.launch(
                createRingtonePickerIntent(
                    currentRingtoneUri = state.defaultRingtoneUri,
                    title = ringtonePickerTitle,
                ),
            )
        },
        onPermissionAction = { permissionId ->
            when (permissionId) {
                "notifications" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                "exact_alarms" -> {
                    exactAlarmSettingsLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                }

                "battery_optimization" -> {
                    batteryOptimizationLauncher.launch(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                }
            }
        },
        onBack = onBack,
    )
}

private fun createRingtonePickerIntent(
    currentRingtoneUri: String,
    title: String,
): Intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtoneUri.takeIf { it.isNotBlank() }?.let(Uri::parse))
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)

@Suppress("DEPRECATION")
private fun extractPickedRingtoneUri(data: Intent?): Uri? = when {
    data == null -> null
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    }

    else -> data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
}

private fun <VM : ViewModel> factoryOf(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
