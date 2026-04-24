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
    const val ALARM_ID_ARG = "alarmId"

    fun editExisting(alarmId: Long): String = "alarms/$alarmId/edit"
}

@Composable
fun NoterApp(
    alarmListScreen: @Composable (
        onOpenManualCreate: () -> Unit,
        onOpenAiCreate: () -> Unit,
        onEditAlarm: (Long) -> Unit,
        onOpenSettings: () -> Unit,
    ) -> Unit,
    alarmEditorScreen: @Composable (alarmId: Long?, onDone: () -> Unit) -> Unit,
    aiCreateScreen: @Composable (onBack: () -> Unit, onOpenManualCreate: () -> Unit) -> Unit,
    settingsScreen: @Composable (onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.LIST,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
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
        composable(route = Routes.AI_CREATE) {
            aiCreateScreen(
                { navController.popBackStack() },
                { navController.navigate(Routes.EDIT_NEW) },
            )
        }
        composable(route = Routes.SETTINGS) {
            settingsScreen(
                { navController.popBackStack() },
            )
        }
    }
}
