package com.eatplease.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eatplease.app.ui.theme.EatPleaseTheme
import com.eatplease.app.ui.theme.NeoBox
import com.eatplease.app.ui.theme.NeoColors
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
    EatPleaseTheme {
        val backStack = rememberNavBackStack(navBackStackConfig, HomeKey)
        // SessionDetail is pushed from Log, so it keeps the Log tab highlighted.
        val currentTab: AppNavKey? = when (val top = backStack.lastOrNull()) {
            is SessionDetailKey -> LogKey
            else -> top as? AppNavKey
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().background(NeoColors.Cream),
            containerColor = NeoColors.Cream,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                NeoBottomBar(
                    currentTab = currentTab,
                    onSelect = { key ->
                        // Tabs are single-instance roots: switching resets the stack.
                        if (backStack.lastOrNull() != key) {
                            backStack.clear()
                            backStack.add(key)
                        }
                    },
                )
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
                // Opaque cream band behind the status bar; content sits below it.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(NeoColors.Cream),
                )
            }
        }
    }
}

/** Cream bar with a hard top rule; the selected tab is a pressed yellow chip. */
@Composable
private fun NeoBottomBar(currentTab: AppNavKey?, onSelect: (AppNavKey) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(NeoColors.Cream)) {
        // Hard ink rule across the top of the bar.
        Box(Modifier.fillMaxWidth().height(3.dp).background(NeoColors.Ink))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomNavItems.forEach { item ->
                NeoNavItem(
                    label = item.label,
                    icon = item.icon,
                    selected = currentTab == item.key,
                    onClick = { onSelect(item.key) },
                )
            }
        }
        // Respect the system navigation-bar inset below the row.
        Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun NeoNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        NeoBox(
            backgroundColor = NeoColors.Yellow,
            cornerRadius = 14.dp,
            shadowOffset = 3.dp,
            onClick = onClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 7.dp),
        ) {
            NavItemContent(label, icon)
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            NavItemContent(label, icon)
        }
    }
}

@Composable
private fun NavItemContent(label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = label, tint = NeoColors.Ink, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = NeoColors.Ink, textAlign = TextAlign.Center)
    }
}
