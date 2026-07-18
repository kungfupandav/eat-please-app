package com.eatplease.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eatplease.app.di.AppGraph
import com.eatplease.app.di.Di
import com.eatplease.app.ui.HomeScreen
import com.eatplease.app.ui.LogScreen
import com.eatplease.app.ui.SessionDetailScreen

private sealed interface Screen {
    data object Home : Screen
    data object Log : Screen
    data class SessionDetail(val sessionId: Long) : Screen
}

/** Root composable shared by the Android and iOS apps. */
@Composable
fun App(graph: AppGraph = Di.graph) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var screen by remember { mutableStateOf<Screen>(Screen.Home) }
            when (val current = screen) {
                is Screen.Home -> HomeScreen(
                    graph = graph,
                    onOpenLog = { screen = Screen.Log },
                )

                is Screen.Log -> LogScreen(
                    graph = graph,
                    onBack = { screen = Screen.Home },
                    onOpenSession = { screen = Screen.SessionDetail(it) },
                )

                is Screen.SessionDetail -> SessionDetailScreen(
                    graph = graph,
                    sessionId = current.sessionId,
                    onBack = { screen = Screen.Log },
                )
            }
        }
    }
}
