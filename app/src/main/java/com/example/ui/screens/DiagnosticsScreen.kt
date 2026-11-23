package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.network.AutoFixStep
import com.example.network.BeforeAfterComparison
import com.example.network.CheckSeverity
import com.example.network.DiagnosticCheckItem
import com.example.network.FixStepStatus
import com.example.network.NetworkTelemetry
import com.example.ui.ModemUiState
import com.example.ui.components.HealthScoreArc
import com.example.ui.components.LtrText
import com.example.ui.components.SystemIntentHelpers
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnSignalEmerald
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.StatusFault
import com.example.ui.theme.StatusFaultContainer
import com.example.ui.theme.StatusFixed
import com.example.ui.theme.StatusFixedContainer
import com.example.ui.theme.StatusHealthy
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DiagnosticsScreen(
    state: ModemUiState,
    onRefreshTelemetry: () -> Unit,
    onStartScanAndAutoFix: () -> Unit,
    onStartScanOnly: () -> Unit,
    onRunAutoFixNow: () -> Unit,
    onStopOperation: () -> Unit,
    onOpenRouterTab: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isBusy = state.isDiagnosing || state.isAutoFixing

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top App Header
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = SlateSurfaceVariant,
                        border = BorderStroke(1.dp, SlateOutline)
                    ) {
                        IconButton(
                            onClick = onRefreshTelemetry,
                            enabled = !state.isRefreshingTelemetry,
                            modifier = Modifier.testTag("refresh_telemetry_button")
                        ) {
                            if (state.isRefreshingTelemetry) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = SignalEmerald
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.cd_refresh_telemetry),
                                    tint = SignalEmerald
                                )
                            }
                        }
                    }
                }
            }

            // Hero Modem Telemetry Card with generated artwork + Health Score
            item(key = "hero_modem_card") {
                ModemHeroCard(
                    telemetry = state.telemetry,
                    healthScore = state.healthScore,
                    isBusy = isBusy
                )
            }

            // Primary Action Buttons
            item(key = "action_buttons") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isBusy) {
                        Button(
                            onClick = onStopOperation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("stop_operation_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusFaultContainer,
                                contentColor = StatusFault
                            ),
                            border = BorderStroke(1.dp, StatusFault.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_stop_scan),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Button(
                            onClick = onStartScanAndAutoFix,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("scan_and_autofix_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalEmerald,
                                contentColor = OnSignalEmerald
                            )
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.btn_scan_and_autofix),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onStartScanOnly,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("scan_only_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, SlateOutline)
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = CyberCyan
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_scan_only),
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            FilledTonalButton(
                                onClick = onRunAutoFixNow,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("autofix_only_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CyberCyanContainer,
                                    contentColor = CyberCyan
                                )
                            ) {
                                Icon(
                                    Icons.Filled.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_autofix_now),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            // Before vs After Comparison Card (shown after Auto-Fix finishes)
            if (state.beforeAfter != null) {
                item(key = "before_after_card") {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically()
                    ) {
                        BeforeAfterResultCard(
                            comparison = state.beforeAfter,
                            onApplyPrivateDns = { hostname ->
                                SystemIntentHelpers.copyToClipboard(context, "Private DNS", hostname)
                                onShowMessage("آدرس $hostname کپی شد — در صفحه تنظیمات، گزینه Private DNS را روی آن تنظیم کنید.")
                                SystemIntentHelpers.openPrivateDnsSettings(context)
                            },
                            onOpenWifiPanel = {
                                SystemIntentHelpers.openWifiConnectivityPanel(context)
                            },
                            onOpenRouterPanel = onOpenRouterTab
                        )
                    }
                }
            }

            // Auto-Fix Live Step Progress Log
            if (state.autoFixSteps.isNotEmpty()) {
                item(key = "autofix_steps_card") {
                    AutoFixProgressCard(steps = state.autoFixSteps)
                }
            }

            // Diagnostic Checks Section Header
            item(key = "checks_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.section_diagnostic_checks),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    val faultCount = state.diagnosticChecks.count {
                        it.severity == CheckSeverity.FAULT || it.severity == CheckSeverity.WARNING
                    }
                    if (state.hasScannedOnce) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (faultCount == 0) SignalEmeraldContainer else StatusWarningContainer
                        ) {
                            Text(
                                text = if (faultCount == 0) "همه سالم" else "$faultCount مورد نیازمند توجه",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (faultCount == 0) SignalEmerald else StatusWarning,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 6 Real Diagnostic Check Cards
            items(state.diagnosticChecks, key = { it.id }) { check ->
                DiagnosticCheckCard(check = check)
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ModemHeroCard(
    telemetry: NetworkTelemetry,
    healthScore: Int,
    isBusy: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.8f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_modem_hero_1795470261193),
                    contentDescription = stringResource(R.string.cd_modem_hero),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Dark readability scrim blending seamlessly into the card surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ObsidianBg.copy(alpha = 0.88f),
                                    ObsidianBg.copy(alpha = 0.62f),
                                    ObsidianBg.copy(alpha = 0.35f)
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (telemetry.isConnected) SignalEmeraldContainer else StatusFaultContainer,
                            border = BorderStroke(
                                1.dp,
                                if (telemetry.isConnected) SignalEmerald.copy(alpha = 0.45f)
                                else StatusFault.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (telemetry.isConnected) Icons.Filled.SignalWifi4Bar else Icons.Filled.SignalWifiOff,
                                    contentDescription = null,
                                    tint = if (telemetry.isConnected) SignalEmerald else StatusFault,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (telemetry.isConnected) telemetry.transportType.persianLabel else "قطع",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (telemetry.isConnected) SignalEmerald else StatusFault
                                )
                            }
                        }

                        Text(
                            text = telemetry.networkName,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = telemetry.routerVendor,
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan,
                            maxLines = 1
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    HealthScoreArc(
                        score = healthScore,
                        isScanning = isBusy,
                        size = 110.dp
                    )
                }
            }

            HorizontalDivider(color = SlateOutline.copy(alpha = 0.5f))

            // 4 Precision Telemetry Micro-Tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryMiniStat(
                    label = stringResource(R.string.label_gateway_ip),
                    value = telemetry.gatewayIp,
                    accent = CyberCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMiniStat(
                    label = stringResource(R.string.label_signal_strength),
                    value = telemetry.rssiDbm?.let { "$it dBm" } ?: "${telemetry.signalPercent}%",
                    accent = if (telemetry.signalPercent >= 60) SignalEmerald else StatusWarning,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMiniStat(
                    label = stringResource(R.string.label_link_speed),
                    value = telemetry.linkSpeedMbps?.let { "$it Mbps" } ?: "—",
                    accent = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryMiniStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(Modifier.height(3.dp))
        LtrText(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            mono = true
        )
    }
}

@Composable
private fun DiagnosticCheckCard(check: DiagnosticCheckItem) {
    val (accentColor, containerColor, statusIcon, statusBadge) = when (check.severity) {
        CheckSeverity.IDLE -> Quad(TextMuted, SlateSurfaceElevated, Icons.Filled.WifiFind, "آماده")
        CheckSeverity.RUNNING -> Quad(CyberCyan, CyberCyanContainer, Icons.Filled.NetworkCheck, "در حال بررسی")
        CheckSeverity.HEALTHY -> Quad(StatusHealthy, SignalEmeraldContainer, Icons.Filled.CheckCircle, "سالم")
        CheckSeverity.WARNING -> Quad(StatusWarning, StatusWarningContainer, Icons.Filled.Warning, "هشدار")
        CheckSeverity.FAULT -> Quad(StatusFault, StatusFaultContainer, Icons.Filled.Error, "نیاز به رفع عیب")
        CheckSeverity.FIXED -> Quad(StatusFixed, StatusFixedContainer, Icons.Filled.Verified, "برطرف شد")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_check_${check.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(
            width = 1.dp,
            color = if (check.severity == CheckSeverity.FAULT || check.severity == CheckSeverity.WARNING) {
                accentColor.copy(alpha = 0.45f)
            } else {
                SlateOutline.copy(alpha = 0.55f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                if (check.severity == CheckSeverity.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = CyberCyan
                    )
                } else {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusBadge,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = check.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = containerColor
                    ) {
                        Text(
                            text = statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = check.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                LtrText(
                    text = check.metricValue,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    mono = true
                )
            }
        }
    }
}

@Composable
private fun AutoFixProgressCard(steps: List<AutoFixStep>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant),
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = SignalEmerald)
                Text(
                    text = stringResource(R.string.section_autofix_log),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            steps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (step.status) {
                            FixStepStatus.RUNNING -> CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyberCyan
                            )
                            FixStepStatus.SUCCESS -> Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "موفق",
                                tint = SignalEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            FixStepStatus.ACTION_NEEDED -> Icon(
                                Icons.Filled.Warning,
                                contentDescription = "نیازمند اقدام",
                                tint = StatusWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            FixStepStatus.FAILED -> Icon(
                                Icons.Filled.Error,
                                contentDescription = "ناموفق",
                                tint = StatusFault,
                                modifier = Modifier.size(18.dp)
                            )
                            FixStepStatus.PENDING -> Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SlateOutline)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BeforeAfterResultCard(
    comparison: BeforeAfterComparison,
    onApplyPrivateDns: (String) -> Unit,
    onOpenWifiPanel: () -> Unit,
    onOpenRouterPanel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("before_after_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SignalEmeraldContainer.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = SignalEmerald)
                Text(
                    text = stringResource(R.string.section_before_after),
                    style = MaterialTheme.typography.titleMedium,
                    color = SignalEmerald
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeltaMetricBox(
                    title = "امتیاز سلامت",
                    before = "${comparison.beforeScore}",
                    after = "${comparison.afterScore}",
                    modifier = Modifier.weight(1f)
                )
                DeltaMetricBox(
                    title = "پینگ مودم",
                    before = "${comparison.beforeGatewayPingMs}ms",
                    after = "${comparison.afterGatewayPingMs}ms",
                    modifier = Modifier.weight(1f)
                )
                DeltaMetricBox(
                    title = "سرعت دانلود",
                    before = "%.1f".format(comparison.beforeDownloadMbps),
                    after = "%.1f".format(comparison.afterDownloadMbps),
                    modifier = Modifier.weight(1f)
                )
            }

            // Recommended Fastest DNS quick apply
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SlateSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "بهترین DNS شناسایی‌شده: ${comparison.selectedDnsName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        LtrText(
                            text = "${comparison.selectedDnsIp} • ${comparison.selectedDotHostname}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }
                    FilledTonalButton(
                        onClick = { onApplyPrivateDns(comparison.selectedDotHostname) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SignalEmerald,
                            contentColor = OnSignalEmerald
                        ),
                        modifier = Modifier.testTag("apply_private_dns_button")
                    ) {
                        Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("اعمال DNS", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenWifiPanel,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.5f))
                ) {
                    Text("تجدید وای‌فای", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onOpenRouterPanel,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.Router, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("پنل مودم", color = CyberCyan, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DeltaMetricBox(
    title: String,
    before: String,
    after: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SlateSurface.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            LtrText(
                text = "$before → $after",
                style = MaterialTheme.typography.labelMedium,
                color = SignalEmerald,
                mono = true
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
