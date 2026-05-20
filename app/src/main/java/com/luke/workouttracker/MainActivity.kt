package com.luke.workouttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.luke.workouttracker.data.prefs.ThemePrefs
import com.luke.workouttracker.ui.nav.AppNavHost
import com.luke.workouttracker.ui.theme.WorkoutTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePrefs: ThemePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode by themePrefs.mode.collectAsState()
            WorkoutTheme(mode = mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    AppNavHost(nav)
                }
            }
        }
    }
}
