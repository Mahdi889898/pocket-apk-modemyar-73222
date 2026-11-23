package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.network.DnsBenchmarkItem
import com.example.network.SpeedTestPhase
import com.example.ui.ModemUiState
import com.example.ui.components.LiveThroughputChart
import com.example.ui.components.LtrText
import com.example.ui.components.SpeedometerGauge
import com.example.ui.components.SystemIntentHelpers
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.OnSignalEmerald
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.StatusFault
import com.example.ui.theme.StatusFaultContainer
import com.example.ui.theme.StatusHealthy
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SpeedLabScreen(
    state: ModemUiState,
    onStartSpeedTest: () -> Unit,
    onStopSpeedTest: () -> Unit,
    onRunDnsBenchmark: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val st = state.speedTest
    val isTesting = st.phase == SpeedTestPhase.PING ||
        st.phase == SpeedTestPhase.DOWNLOAD ||
        st.phase == SpeedTestPhase.UPLOAD

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
            item(key = "speed_header") {
                Column {
                    Text(
                        text = "آزمایشگاه سرعت و پایداری خط",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "اندازه‌گیری واقعی دانلود، آپلود، جیتر، پکت لاس و پینگ تحت بار (Bufferbloat)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Main Speedometer Gauge Card
            item(key = "speedometer_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val gaugeSpeed = when (st.phase) {
                            SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD -> st.liveSpeedMbps
                            SpeedTestPhase.COMPLETED -> st.downloadMbps
                            else -> st.downloadMbps
                        }

                        SpeedometerGauge(
                            speedMbps = gaugeSpeed,
                            phaseLabel = st.phase.persianLabel,
                            isActive = isTesting
                        )

                        if (isTesting) {
                            LinearProgressIndicator(
                                progress = { st.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = SignalEmerald,
                                trackColor = SlateSurfaceElevated
                            )
                        }

                        // Real-time Speed Curve Sparkline
                        val chartSamples = if (st.phase == SpeedTestPhase.UPLOAD && st.uploadSamples.isNotEmpty()) {
                            st.uploadSamples
                        } else {
                            st.downloadSamples
                        }
                        if (chartSamples.size >= 2) {
                            LiveThroughputChart(
                                samples = chartSamples,
                                lineColor = if (st.phase == SpeedTestPhase.UPLOAD) CyberCyan else SignalEmerald
                            )
                        }

                        if (!st.errorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StatusFaultContainer
                            ) {
                                Text(
                                    text = st.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusFault,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Download & Upload Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SpeedMetricTile(
                                title = stringResource(R.string.speed_download),
                                value = "${"%.1f".format(st.downloadMbps)} Mbps",
                                icon = Icons.Filled.ArrowDownward,
                                accent = SignalEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            SpeedMetricTile(
                                title = stringResource(R.string.speed_upload),
                                value = "${"%.1f".format(st.uploadMbps)} Mbps",
                                icon = Icons.Filled.ArrowUpward,
                                accent = CyberCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Ping, Loaded Ping, Jitter, Packet Loss Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactLatencyChip(
                                label = stringResource(R.string.speed_ping),
                                value = if (st.idlePingMs > 0) "${st.idlePingMs}ms" else "--",
                                accent = if (st.idlePingMs in 1..70) SignalEmerald else StatusWarning,
                                modifier = Modifier.weight(1f)
                            )
                            CompactLatencyChip(
                                label = stringResource(R.string.speed_bufferbloat),
                                value = if (st.loadedPingMs > 0) "${st.loadedPingMs}ms" else "--",
                                accent = if (st.loadedPingMs - st.idlePingMs <= 45) CyberCyan else StatusWarning,
                                modifier = Modifier.weight(1f)
                            )
                            CompactLatencyChip(
                                label = stringResource(R.string.speed_jitter),
                                value = if (st.idlePingMs > 0) "${st.jitterMs}ms" else "--",
                                accent = if (st.jitterMs <= 15) SignalEmerald else StatusWarning,
                                modifier = Modifier.weight(1f)
                            )
                            CompactLatencyChip(
                                label = stringResource(R.string.speed_packet_loss),
                                value = "${st.packetLossPercent}%",
                                accent = if (st.packetLossPercent == 0) SignalEmerald else StatusFault,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Start / Stop Button
                        Button(
                            onClick = if (isTesting) onStopSpeedTest else onStartSpeedTest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_speed_test_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = if (isTesting) {
                                ButtonDefaults.buttonColors(
                                    containerColor = StatusFaultContainer,
                                    contentColor = StatusFault
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = SignalEmerald,
                                    contentColor = OnSignalEmerald
                                )
                            }
                        ) {
                            Icon(
                                imageVector = if (isTesting) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isTesting) stringResource(R.string.btn_stop_scan)
                                else stringResource(R.string.speed_start_test),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Quality Readiness Verdict (shown once speed test completed)
            if (st.phase == SpeedTestPhase.COMPLETED) {
                item(key = "quality_verdict") {
                    QualityReadinessCard(
                        downloadMbps = st.downloadMbps,
                        uploadMbps = st.uploadMbps,
                        pingMs = st.idlePingMs,
                        jitterMs = st.jitterMs
                    )
                }
            }

            // DNS Speed Benchmark & Auto-Optimizer Section
            item(key = "dns_header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.75f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Dns, contentDescription = null, tint = CyberCyan)
                                Text(
                                    text = stringResource(R.string.dns_optimizer_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }

                            FilledTonalButton(
                                onClick = onRunDnsBenchmark,
                                enabled = !state.isBenchmarkingDns,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CyberCyanContainer,
                                    contentColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("dns_benchmark_button")
                            ) {
                                if (state.isBenchmarkingDns) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = CyberCyan
                                    )
                                } else {
                                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("تست DNS", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Text(
                            text = "پینگ واقعی سرورهای DNS را مقایسه کنید و سریع‌ترین گزینه را روی گوشی یا مودم خود تنظیم نمایید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        if (state.dnsBenchmarks.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SlateSurfaceVariant
                            ) {
                                Text(
                                    text = "برای یافتن سریع‌ترین سرور DNS، روی دکمه «تست DNS» ضربه بزنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.dnsBenchmarks.forEach { dns ->
                                    DnsServerRow(
                                        item = dns,
                                        onCopy = {
                                            val copyVal = dns.dotHostname.ifBlank { dns.primaryIp }
                                            SystemIntentHelpers.copyToClipboard(context, dns.name, copyVal)
                                            onShowMessage("${dns.name} ($copyVal) در حافظه کپی شد.")
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val bestHost = state.dnsBenchmarks.firstOrNull { it.isFastest }?.dotHostname
                                    ?.takeIf { it.isNotBlank() } ?: "one.one.one.one"
                                SystemIntentHelpers.copyToClipboard(context, "Private DNS", bestHost)
                                onShowMessage("آدرس $bestHost کپی شد — در تنظیمات شبکه وارد بخش Private DNS شوید.")
                                SystemIntentHelpers.openPrivateDnsSettings(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_private_dns_settings_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.55f))
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = SignalEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.dns_open_settings),
                                color = SignalEmerald,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SpeedMetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SlateSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                LtrText(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    mono = true
                )
            }
        }
    }
}

@Composable
private fun CompactLatencyChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SlateSurfaceElevated.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            LtrText(text = value, style = MaterialTheme.typography.labelMedium, color = accent, mono = true)
        }
    }
}

@Composable
private fun QualityReadinessCard(
    downloadMbps: Double,
    uploadMbps: Double,
    pingMs: Int,
    jitterMs: Int
) {
    val gamingOk = pingMs in 1..85 && jitterMs <= 20
    val stream4kOk = downloadMbps >= 20.0
    val videoCallOk = downloadMbps >= 5.0 && uploadMbps >= 2.0 && pingMs in 1..130

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ارزیابی کیفیت برای کاربردهای روزمره",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadinessBadge(
                    icon = Icons.Filled.SportsEsports,
                    label = "بازی آنلاین",
                    status = if (gamingOk) "عالی" else "متوسط",
                    isGood = gamingOk,
                    modifier = Modifier.weight(1f)
                )
                ReadinessBadge(
                    icon = Icons.Filled.LiveTv,
                    label = "استریم 4K",
                    status = if (stream4kOk) "روان" else "HD",
                    isGood = stream4kOk,
                    modifier = Modifier.weight(1f)
                )
                ReadinessBadge(
                    icon = Icons.Filled.Videocam,
                    label = "تماس تصویری",
                    status = if (videoCallOk) "پایدار" else "ضعیف",
                    isGood = videoCallOk,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReadinessBadge(
    icon: ImageVector,
    label: String,
    status: String,
    isGood: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isGood) StatusHealthy else StatusWarning
    val bg = if (isGood) SignalEmeraldContainer else StatusWarningContainer
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bg.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            Text(text = status, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DnsServerRow(
    item: DnsBenchmarkItem,
    onCopy: () -> Unit
) {
    val borderColor = when {
        item.isFastest -> SignalEmerald.copy(alpha = 0.7f)
        item.isCurrentSystem -> CyberCyan.copy(alpha = 0.55f)
        else -> SlateOutline.copy(alpha = 0.5f)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SlateSurfaceVariant,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    if (item.isFastest) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SignalEmeraldContainer
                        ) {
                            Text(
                                text = "سریع‌ترین",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (item.isCurrentSystem) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberCyanContainer
                        ) {
                            Text(
                                text = "فعلی",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                LtrText(
                    text = buildString {
                        append(item.primaryIp)
                        if (item.dotHostname.isNotBlank()) {
                            append(" • ")
                            append(item.dotHostname)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LtrText(
                    text = if (item.reachable && item.latencyMs != null) "${item.latencyMs} ms" else "قطع",
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        !item.reachable -> StatusFault
                        item.isFastest -> SignalEmerald
                        else -> CyberCyan
                    },
                    mono = true
                )
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
