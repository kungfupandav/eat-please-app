package com.eatplease.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.eatplease.app.ui.SettingsScreen
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
data object SettingsKey : AppNavKey

@Serializable
data class SessionDetailKey(val sessionId: Long) : AppNavKey

private val navBackStackConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(HomeKey::class, HomeKey.serializer())
            subclass(LogKey::class, LogKey.serializer())
            subclass(SettingsKey::class, SettingsKey.serializer())
            subclass(SessionDetailKey::class, SessionDetailKey.serializer())
        }
    }
}

private data class BottomNavItem(val key: AppNavKey, val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(HomeKey, "Watch", Icons.Filled.Visibility),
    BottomNavItem(LogKey, "Log", Icons.AutoMirrored.Filled.List),
    BottomNavItem(SettingsKey, "Settings", Icons.Filled.Settings),
)

/** Root composable shared by the Android and iOS apps; Navigation 3 back stack. */
@Composable
fun App(graph: AppGraph = Di.graph) {
    MaterialTheme {
        val backStack = rememberNavBackStack(navBackStackConfig, HomeKey)
        // SessionDetail is pushed from Log, so it keeps the Log tab highlighted.
        val currentTab = when (val top = backStack.lastOrNull()) {
            is SessionDetailKey -> LogKey
            else -> top
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentTab == item.key,
                            onClick = {
                                // Tabs are single-instance roots: switching resets the stack.
                                if (backStack.lastOrNull() != item.key) {
                                    backStack.clear()
                                    backStack.add(item.key)
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
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
                            HomeScreen(graph = graph)
                        }
                        entry<LogKey> {
                            LogScreen(
                                graph = graph,
                                onOpenSession = { backStack.add(SessionDetailKey(it)) },
                            )
                        }
                        entry<SettingsKey> {
                            SettingsScreen(graph = graph)
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
                // Translucent scrim behind the status bar; content sits below it.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)),
                )
            }
        }
    }
}
