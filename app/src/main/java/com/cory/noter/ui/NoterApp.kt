package com.cory.noter.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val LIST = "alarms"
    const val EDIT_NEW = "alarms/new"
    const val EDIT_EXISTING = "alarms/{alarmId}/edit"
    const val AI_CREATE = "alarms/ai"
    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_AI_VOICE = "settings/ai"
    const val SETTINGS_SOUND = "settings/sound"
    const val SETTINGS_PERMISSIONS = "settings/permissions"
    const val ALARM_ID_ARG = "alarmId"

    fun editExisting(alarmId: Long): String = "alarms/$alarmId/edit"
}

@Composable
fun NoterApp(
    unifiedAiCreateScreen: @Composable (
        onOpenAlarmList: () -> Unit,
        onOpenSettings: () -> Unit,
        onOpenManualCreate: () -> Unit,
    ) -> Unit,
    alarmListScreen: @Composable (
        onOpenManualCreate: () -> Unit,
        onOpenAiCreate: () -> Unit,
        onEditAlarm: (Long) -> Unit,
        onOpenSettings: () -> Unit,
    ) -> Unit,
    alarmEditorScreen: @Composable (alarmId: Long?, onDone: () -> Unit) -> Unit,
    settingsScreen: @Composable (
        onOpenAppearance: () -> Unit,
        onOpenAiVoice: () -> Unit,
        onOpenSound: () -> Unit,
        onOpenPermissions: () -> Unit,
        onBack: () -> Unit,
    ) -> Unit,
    appearanceSettingsScreen: @Composable (onBack: () -> Unit) -> Unit,
    aiVoiceSettingsScreen: @Composable (onBack: () -> Unit) -> Unit,
    soundSettingsScreen: @Composable (onBack: () -> Unit) -> Unit,
    permissionsSettingsScreen: @Composable (onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.AI_CREATE,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(route = Routes.AI_CREATE) {
            unifiedAiCreateScreen(
                { navController.navigate(Routes.LIST) },
                { navController.navigate(Routes.SETTINGS) },
                { navController.navigate(Routes.EDIT_NEW) },
            )
        }
        composable(route = Routes.LIST) {
            alarmListScreen(
                { navController.navigate(Routes.EDIT_NEW) },
                { navController.navigate(Routes.AI_CREATE) },
                { alarmId -> navController.navigate(Routes.editExisting(alarmId)) },
                { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(route = Routes.EDIT_NEW) {
            alarmEditorScreen(
                null,
                { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EDIT_EXISTING,
            arguments = listOf(
                navArgument(Routes.ALARM_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            alarmEditorScreen(
                backStackEntry.arguments?.getLong(Routes.ALARM_ID_ARG),
                { navController.popBackStack() },
            )
        }
        composable(route = Routes.SETTINGS) {
            settingsScreen(
                { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                { navController.navigate(Routes.SETTINGS_AI_VOICE) },
                { navController.navigate(Routes.SETTINGS_SOUND) },
                { navController.navigate(Routes.SETTINGS_PERMISSIONS) },
                { navController.popBackStack() },
            )
        }
        composable(route = Routes.SETTINGS_APPEARANCE) {
            appearanceSettingsScreen(
                { navController.popBackStack() },
            )
        }
        composable(route = Routes.SETTINGS_AI_VOICE) {
            aiVoiceSettingsScreen(
                { navController.popBackStack() },
            )
        }
        composable(route = Routes.SETTINGS_SOUND) {
            soundSettingsScreen(
                { navController.popBackStack() },
            )
        }
        composable(route = Routes.SETTINGS_PERMISSIONS) {
            permissionsSettingsScreen(
                { navController.popBackStack() },
            )
        }
    }
}
