package com.buggygamesyt.shortsfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buggygamesyt.shortsfinder.ui.AppViewModel
import com.buggygamesyt.shortsfinder.ui.MomentsScreen
import com.buggygamesyt.shortsfinder.ui.SettingsScreen
import com.buggygamesyt.shortsfinder.ui.VideoListScreen
import com.buggygamesyt.shortsfinder.ui.theme.ShortsFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            ShortsFinderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

enum class Screen { LIST, MOMENTS, SETTINGS }

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val list by vm.list.collectAsState()
    val settings by vm.settings.collectAsState()
    val selected by vm.selected.collectAsState()
    val moments by vm.moments.collectAsState()

    var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.LIST) }

    val video = selected
    // After process death the selected video is gone; fall back to the list.
    val current = if (screen == Screen.MOMENTS && video == null) Screen.LIST else screen

    BackHandler(enabled = current != Screen.LIST) {
        screen = if (current == Screen.SETTINGS) settingsReturn else Screen.LIST
    }

    when (current) {
        Screen.LIST -> VideoListScreen(
            state = list,
            settings = settings,
            onRefresh = vm::refresh,
            onDismissError = vm::dismissError,
            onOpen = { v ->
                vm.openVideo(v)
                screen = Screen.MOMENTS
            },
            onSettings = {
                settingsReturn = Screen.LIST
                screen = Screen.SETTINGS
            },
        )

        Screen.MOMENTS -> if (video != null) {
            MomentsScreen(
                video = video,
                state = moments,
                settings = settings,
                onBack = { screen = Screen.LIST },
                onRetry = vm::analyze,
                onSettings = {
                    settingsReturn = Screen.MOMENTS
                    screen = Screen.SETTINGS
                },
            )
        }

        Screen.SETTINGS -> SettingsScreen(
            initial = settings,
            onSave = { s ->
                vm.updateSettings(s)
                screen = settingsReturn
            },
            onBack = { screen = settingsReturn },
        )
    }
}
