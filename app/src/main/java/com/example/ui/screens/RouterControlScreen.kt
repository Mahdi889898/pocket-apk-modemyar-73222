package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WifiProtectedSetup
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.ModemUiState
import com.example.ui.components.ChannelCongestionChart
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
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RouterControlScreen(
    state: ModemUiState,
    onLocationPermissionResult: (Boolean) -> Unit,
    onScanChannels: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEmbeddedRouterWeb by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onLocationPermissionResult(granted)
    }

    if (showEmbeddedRouterWeb) {
        val gateway = state.telemetry.gatewayIp.takeIf { it.isNotBlank() && it != "نامشخص" } ?: "192.168.1.1"
        val fastestDns = state.dnsBenchmarks.firstOrNull { it.isFastest }?.primaryIp ?: "1.1.1.1"
        val bestChannel = state.channelReport.recommendedChannel
        EmbeddedRouterAdminView(
            gatewayIp = gateway,
            recommendedDns = fastestDns,
            recommendedChannel = bestChannel,
            onCopy = { label, value ->
                SystemIntentHelpers.copyToClipboard(context, label, value)
                onShowMessage("$label ($value) کپی شد.")
            },
            onClose = { showEmbeddedRouterWeb = false }
        )
        return
    }

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
            item(key = "router_header") {
                Column {
                    Text(
                        text = "کنترل هوشمند مودم و رادار کانال",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "دسترسی مستقیم به تنظیمات روتر، تجدید اتصال وای‌فای و یافتن خلوت‌ترین کانال بی‌سیم",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Smart Modem Control Panel Card
            item(key = "modem_panel_card") {
                val t = state.telemetry
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SignalEmeraldContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Router, contentDescription = null, tint = SignalEmerald)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.router_panel_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = t.routerVendor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberCyan
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (t.routerAdminReachable) SignalEmeraldContainer else StatusWarningContainer
                            ) {
                                Text(
                                    text = if (t.routerAdminReachable) "در دسترس" else "بررسی محلی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (t.routerAdminReachable) SignalEmerald else StatusWarning,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = SlateOutline.copy(alpha = 0.45f))

                        // Network Technical Details Table
                        RouterDetailRow(
                            label = stringResource(R.string.label_gateway_ip),
                            value = t.gatewayIp,
                            onCopy = {
                                SystemIntentHelpers.copyToClipboard(context, "Gateway IP", t.gatewayIp)
                                onShowMessage("آدرس مودم (${t.gatewayIp}) کپی شد.")
                            }
                        )
                        RouterDetailRow(
                            label = stringResource(R.string.label_local_ip),
                            value = t.localIp,
                            onCopy = {
                                SystemIntentHelpers.copyToClipboard(context, "Local IP", t.localIp)
                                onShowMessage("آی‌پی دستگاه (${t.localIp}) کپی شد.")
                            }
                        )
                        RouterDetailRow(
                            label = stringResource(R.string.label_dns_active),
                            value = t.dnsServers.take(2).joinToString(" , ").ifBlank { "خودکار (DHCP)" }
                        )
                        RouterDetailRow(
                            label = stringResource(R.string.label_band),
                            value = t.bandLabel + if (t.mtu > 0) " • MTU ${t.mtu}" else ""
                        )

                        Spacer(Modifier.height(2.dp))

                        Button(
                            onClick = { showEmbeddedRouterWeb = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("open_embedded_router_button"),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalEmerald,
                                contentColor = OnSignalEmerald
                            )
                        ) {
                            Icon(Icons.Filled.SettingsEthernet, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.router_open_embedded),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { SystemIntentHelpers.openRouterInBrowser(context, t.gatewayIp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_router_browser_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, SlateOutline)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.router_open_browser),
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            FilledTonalButton(
                                onClick = { SystemIntentHelpers.openWifiConnectivityPanel(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("wifi_reconnect_panel_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CyberCyanContainer,
                                    contentColor = CyberCyan
                                )
                            ) {
                                Icon(
                                    Icons.Filled.WifiProtectedSetup,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.router_refresh_wifi),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Wi-Fi Channel Congestion Radar Card
            item(key = "channel_radar_card") {
                val report = state.channelReport
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
                                Icon(Icons.Filled.Radar, contentDescription = null, tint = CyberCyan)
                                Text(
                                    text = stringResource(R.string.channel_radar_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }

                            if (state.hasLocationPermission) {
                                FilledTonalButton(
                                    onClick = onScanChannels,
                                    enabled = !state.isScanningChannels,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = SignalEmeraldContainer,
                                        contentColor = SignalEmerald
                                    ),
                                    modifier = Modifier.testTag("scan_channels_button")
                                ) {
                                    if (state.isScanningChannels) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = SignalEmerald
                                        )
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("اسکن", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (!state.hasLocationPermission) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SlateSurfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            tint = StatusWarning,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.channel_permission_msg),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("grant_location_permission_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberCyan,
                                            contentColor = ObsidianBg
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.channel_grant_permission),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else if (report.channels24Ghz.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SlateSurfaceVariant
                            ) {
                                Text(
                                    text = "برای مشاهده نمودار تداخل کانال‌های ۱ تا ۱۳ و پیشنهاد بهترین کانال مودم، دکمه «اسکن» را بزنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        } else {
                            ChannelCongestionChart(channels = report.channels24Ghz)

                            // Legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LegendDot(color = SignalEmerald, label = "کانال پیشنهادی (${report.recommendedChannel})")
                                if (report.currentChannel != null) {
                                    LegendDot(color = CyberCyan, label = "کانال فعلی (${report.currentChannel})")
                                }
                                LegendDot(color = StatusWarning, label = "پرتداخل")
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SignalEmeraldContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = report.congestionSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Practical Hardware & Speed Optimization Guide Card
            item(key = "modem_tips_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant),
                    border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, tint = StatusWarning)
                            Text(
                                text = "راهنمای سریع رفع افت سرعت سخت‌افزاری مودم",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "• اگر پینگ مودم بالای ۳۰ms بود یا چراغ Internet قرمز شد، مودم را ۳۰ ثانیه از برق بکشید تا کش RAM و نشست PPPoE آن تازه شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "• در پنل مودم بخش Wireless، کانال را به جای Auto روی کانال پیشنهادی رادار (معمولاً ۱، ۶ یا ۱۱) قفل کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "• برای بازی و تماس تصویری بدون لگ، در صورت پشتیبانی مودم از باند 5GHz متصل شوید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun RouterDetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LtrText(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                mono = true
            )
            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy),
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedRouterAdminView(
    gatewayIp: String,
    recommendedDns: String,
    recommendedChannel: Int,
    onCopy: (String, String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val gatewayUrl = remember(gatewayIp) { "http://$gatewayIp" }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar for router console
        Surface(
            color = SlateSurface,
            border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_router_webview_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = TextPrimary
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "پنل مدیریت مودم",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        LtrText(
                            text = gatewayUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh_telemetry),
                            tint = SignalEmerald
                        )
                    }
                }

                // Helper strip with recommended optimal values to copy quickly while inside router admin
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onCopy("DNS بهینه", recommendedDns) },
                        shape = RoundedCornerShape(10.dp),
                        color = SignalEmeraldContainer
                    ) {
                        Text(
                            text = "کپی DNS بهینه: $recommendedDns",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        onClick = { onCopy("کانال پیشنهادی", "$recommendedChannel") },
                        shape = RoundedCornerShape(10.dp),
                        color = CyberCyanContainer
                    ) {
                        Text(
                            text = "کانال پیشنهادی: $recommendedChannel",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // Keep navigation inside the local router gateway only
                            val host = request?.url?.host ?: return false
                            val isLocal = host == gatewayIp ||
                                host.startsWith("192.168.") ||
                                host.startsWith("10.") ||
                                host.startsWith("172.")
                            return !isLocal
                        }
                    }
                    loadUrl(gatewayUrl)
                    webViewRef = this
                }
            }
        )
    }
}
