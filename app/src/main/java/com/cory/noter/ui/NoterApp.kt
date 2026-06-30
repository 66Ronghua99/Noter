package com.cory.noter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val LIST = "alarms"
    const val EDIT_NEW = "alarms/new"
    const val EDIT_EXISTING = "alarms/{alarmId}/edit"
    const val AI_CREATE = "alarms/ai"
    const val SETTINGS = "settings"
    const val SETTINGS_HOME = "settings/home"
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
        onBackFromAiCreate: () -> Unit,
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
        settingsViewModelStoreOwner: ViewModelStoreOwner,
    ) -> Unit,
    appearanceSettingsScreen: @Composable (
        onBack: () -> Unit,
        settingsViewModelStoreOwner: ViewModelStoreOwner,
    ) -> Unit,
    aiVoiceSettingsScreen: @Composable (
        onBack: () -> Unit,
        settingsViewModelStoreOwner: ViewModelStoreOwner,
    ) -> Unit,
    soundSettingsScreen: @Composable (
        onBack: () -> Unit,
        settingsViewModelStoreOwner: ViewModelStoreOwner,
    ) -> Unit,
    permissionsSettingsScreen: @Composable (
        onBack: () -> Unit,
        settingsViewModelStoreOwner: ViewModelStoreOwner,
    ) -> Unit,
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
            val openAlarmList = { navController.navigate(Routes.LIST) }
            val closeAiCreateToAlarmList = {
                navController.navigate(Routes.LIST) {
                    popUpTo(Routes.AI_CREATE) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            val backFromAiCreate = {
                if (!navController.popBackStack()) {
                    closeAiCreateToAlarmList()
                }
            }
            unifiedAiCreateScreen(
                openAlarmList,
                { navController.navigate(Routes.SETTINGS) },
                { navController.navigate(Routes.EDIT_NEW) },
                backFromAiCreate,
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
        navigation(
            startDestination = Routes.SETTINGS_HOME,
            route = Routes.SETTINGS,
        ) {
            composable(route = Routes.SETTINGS_HOME) { backStackEntry ->
                val settingsViewModelStoreOwner = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.SETTINGS)
                }
                settingsScreen(
                    { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                    { navController.navigate(Routes.SETTINGS_AI_VOICE) },
                    { navController.navigate(Routes.SETTINGS_SOUND) },
                    { navController.navigate(Routes.SETTINGS_PERMISSIONS) },
                    { navController.popBackStack() },
                    settingsViewModelStoreOwner,
                )
            }
            composable(route = Routes.SETTINGS_APPEARANCE) { backStackEntry ->
                val settingsViewModelStoreOwner = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.SETTINGS)
                }
                appearanceSettingsScreen(
                    { navController.popBackStack() },
                    settingsViewModelStoreOwner,
                )
            }
            composable(route = Routes.SETTINGS_AI_VOICE) { backStackEntry ->
                val settingsViewModelStoreOwner = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.SETTINGS)
                }
                aiVoiceSettingsScreen(
                    { navController.popBackStack() },
                    settingsViewModelStoreOwner,
                )
            }
            composable(route = Routes.SETTINGS_SOUND) { backStackEntry ->
                val settingsViewModelStoreOwner = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.SETTINGS)
                }
                soundSettingsScreen(
                    { navController.popBackStack() },
                    settingsViewModelStoreOwner,
                )
            }
            composable(route = Routes.SETTINGS_PERMISSIONS) { backStackEntry ->
                val settingsViewModelStoreOwner = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.SETTINGS)
                }
                permissionsSettingsScreen(
                    { navController.popBackStack() },
                    settingsViewModelStoreOwner,
                )
            }
        }
    }
}
