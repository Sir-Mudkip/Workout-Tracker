package com.luke.workouttracker.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.luke.workouttracker.ui.programs.DayEditorScreen
import com.luke.workouttracker.ui.programs.ProgramEditorScreen
import com.luke.workouttracker.ui.programs.ProgramListScreen
import com.luke.workouttracker.ui.peak.PeakDayScreen
import com.luke.workouttracker.ui.progress.ProgressScreen
import com.luke.workouttracker.ui.session.ActiveSessionScreen
import com.luke.workouttracker.ui.session.SessionPickerScreen
import com.luke.workouttracker.ui.settings.SettingsScreen

@Composable
fun AppNavHost(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.ProgramList) {
        composable(Routes.ProgramList) {
            ProgramListScreen(
                onOpenProgram = { id -> nav.navigate(Routes.programEditor(id)) },
                onStartSession = { id -> nav.navigate(Routes.sessionPicker(id)) },
                onOpenProgress = { id -> nav.navigate(Routes.progress(id)) },
                onOpenSettings = { nav.navigate(Routes.Settings) },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Routes.ProgramEditor,
            arguments = listOf(navArgument("programId") { type = NavType.LongType }),
        ) {
            ProgramEditorScreen(
                onOpenDay = { programId, dayId ->
                    nav.navigate(Routes.dayEditor(programId, dayId))
                },
                onOpenPeakDay = { programId -> nav.navigate(Routes.peakDay(programId)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.DayEditor,
            arguments = listOf(
                navArgument("programId") { type = NavType.LongType },
                navArgument("dayId") { type = NavType.LongType },
            ),
        ) {
            DayEditorScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Routes.SessionPicker,
            arguments = listOf(navArgument("programId") { type = NavType.LongType }),
        ) {
            SessionPickerScreen(
                onStart = { sessionId -> nav.navigate(Routes.activeSession(sessionId)) },
                onOpenPeakDay = { programId -> nav.navigate(Routes.peakDay(programId)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.PeakDay,
            arguments = listOf(navArgument("programId") { type = NavType.LongType }),
        ) {
            PeakDayScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Routes.ActiveSession,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            ActiveSessionScreen(onFinished = { nav.popBackStack(Routes.ProgramList, false) })
        }
        composable(
            Routes.Progress,
            arguments = listOf(navArgument("programId") { type = NavType.LongType }),
        ) {
            ProgressScreen(onBack = { nav.popBackStack() })
        }
    }
}
