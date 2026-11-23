package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.RouterControlScreen
import com.example.ui.screens.SpeedLabScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

private enum class AppTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    DIAGNOSTICS(R.string.nav_diagnostics, Icons.Filled.BuildCircle, Icons.Outlined.BuildCircle, "nav_diagnostics"),
    SPEED(R.string.nav_speed, Icons.Filled.Speed, Icons.Outlined.Speed, "nav_speed"),
    ROUTER(R.string.nav_router, Icons.Filled.Router, Icons.Outlined.Router, "nav_router"),
    HISTORY(R.string.nav_history, Icons.Filled.History, Icons.Outlined.History, "nav_history")
}

@Composable
fun ModemYarApp(
    viewModel: ModemViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.historyRecords.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(AppTab.DIAGNOSTICS) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Return to primary Diagnostics tab on system back press from secondary tabs
    BackHandler(enabled = currentTab != AppTab.DIAGNOSTICS) {
        currentTab = AppTab.DIAGNOSTICS
    }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    // Native Right-to-Left layout for Persian UI
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ObsidianBg, SlateSurface.copy(alpha = 0.92f), ObsidianBg)
                    )
                )
        ) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = SlateSurfaceElevated,
                            contentColor = TextPrimary,
                            actionColor = SignalEmerald
                        )
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = SlateSurface,
                            tonalElevation = 4.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    modifier = Modifier.testTag(tab.tag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.titleRes)
                                        )
                                    },
                                    label = { Text(text = stringResource(tab.titleRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SignalEmerald,
                                        selectedTextColor = SignalEmerald,
                                        indicatorColor = SignalEmeraldContainer,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding(),
                            containerColor = SlateSurface
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    modifier = Modifier.testTag(tab.tag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.titleRes)
                                        )
                                    },
                                    label = { Text(text = stringResource(tab.titleRes)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = SignalEmerald,
                                        selectedTextColor = SignalEmerald,
                                        indicatorColor = SignalEmeraldContainer,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    )
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentTab) {
                            AppTab.DIAGNOSTICS -> DiagnosticsScreen(
                                state = state,
                                onRefreshTelemetry = { viewModel.refreshTelemetry(true) },
                                onStartScanAndAutoFix = { viewModel.startDiagnostics(autoFixAfter = true) },
                                onStartScanOnly = { viewModel.startDiagnostics(autoFixAfter = false) },
                                onRunAutoFixNow = { viewModel.runAutoFixNow() },
                                onStopOperation = { viewModel.stopCurrentOperation() },
                                onOpenRouterTab = { currentTab = AppTab.ROUTER },
                                onShowMessage = { viewModel.showMessage(it) }
                            )

                            AppTab.SPEED -> SpeedLabScreen(
                                state = state,
                                onStartSpeedTest = { viewModel.startSpeedTest() },
                                onStopSpeedTest = { viewModel.stopCurrentOperation() },
                                onRunDnsBenchmark = { viewModel.runDnsBenchmark() },
                                onShowMessage = { viewModel.showMessage(it) }
                            )

                            AppTab.ROUTER -> RouterControlScreen(
                                state = state,
                                onLocationPermissionResult = { viewModel.onLocationPermissionResult(it) },
                                onScanChannels = { viewModel.scanWifiChannels() },
                                onShowMessage = { viewModel.showMessage(it) }
                            )

                            AppTab.HISTORY -> HistoryScreen(
                                records = history,
                                onDeleteRecord = { viewModel.deleteHistoryRecord(it) },
                                onClearAll = { viewModel.clearHistory() }
                            )
                        }
                    }
                }
            }
        }
    }
}
