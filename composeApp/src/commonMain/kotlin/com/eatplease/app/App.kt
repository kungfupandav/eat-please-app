package com.eatplease.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.eatplease.app.di.AppGraph
import com.eatplease.app.di.Di
import com.eatplease.app.ui.HomeScreen
import com.eatplease.app.ui.LogScreen
import com.eatplease.app.ui.SessionDetailScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
sealed interface AppNavKey : NavKey

@Serializable
data object HomeKey : AppNavKey

@Serializable
data object LogKey : AppNavKey

@Serializable
data class SessionDetailKey(val sessionId: Long) : AppNavKey

private val navBackStackConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(HomeKey::class, HomeKey.serializer())
            subclass(LogKey::class, LogKey.serializer())
            subclass(SessionDetailKey::class, SessionDetailKey.serializer())
        }
    }
}

/** Root composable shared by the Android and iOS apps; Navigation 3 back stack. */
@Composable
fun App(graph: AppGraph = Di.graph) {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val backStack = rememberNavBackStack(navBackStackConfig, HomeKey)
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                },
                popTransitionSpec = {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                },
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        HomeScreen(
                            graph = graph,
                            onOpenLog = { backStack.add(LogKey) },
                        )
                    }
                    entry<LogKey> {
                        LogScreen(
                            graph = graph,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenSession = { backStack.add(SessionDetailKey(it)) },
                        )
                    }
                    entry<SessionDetailKey> { key ->
                        SessionDetailScreen(
                            graph = graph,
                            sessionId = key.sessionId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}
