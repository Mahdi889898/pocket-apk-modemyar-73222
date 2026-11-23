package com.example.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DiagnosticRecordEntity
import com.example.data.local.DiagnosticRepository
import com.example.network.AutoFixStep
import com.example.network.BeforeAfterComparison
import com.example.network.CheckSeverity
import com.example.network.DiagnosticCheckItem
import com.example.network.DnsBenchmarkItem
import com.example.network.ModemDiagnosticEngine
import com.example.network.NetworkTelemetry
import com.example.network.NetworkTransportType
import com.example.network.SpeedTestPhase
import com.example.network.SpeedTestState
import com.example.network.WifiChannelReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModemUiState(
    val telemetry: NetworkTelemetry = NetworkTelemetry(),
    val isRefreshingTelemetry: Boolean = false,
    val healthScore: Int = 0,
    val hasScannedOnce: Boolean = false,
    val isDiagnosing: Boolean = false,
    val isAutoFixing: Boolean = false,
    val diagnosticChecks: List<DiagnosticCheckItem> = defaultIdleChecks(),
    val autoFixSteps: List<AutoFixStep> = emptyList(),
    val beforeAfter: BeforeAfterComparison? = null,
    val speedTest: SpeedTestState = SpeedTestState(),
    val dnsBenchmarks: List<DnsBenchmarkItem> = emptyList(),
    val isBenchmarkingDns: Boolean = false,
    val channelReport: WifiChannelReport = WifiChannelReport(),
    val isScanningChannels: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val snackbarMessage: String? = null
)

private fun defaultIdleChecks(): List<DiagnosticCheckItem> = listOf(
    DiagnosticCheckItem(
        id = "signal",
        title = "سلامت سیگنال وای‌فای و لینک مودم",
        description = "بررسی قدرت سیگنال (dBm)، باند فرکانسی و سرعت اتصال بی‌سیم",
        metricValue = "آماده بررسی",
        severity = CheckSeverity.IDLE
    ),
    DiagnosticCheckItem(
        id = "gateway",
        title = "پاسخ‌دهی مودم (دروازه شبکه)",
        description = "ارسال بسته آزمایشی به آی‌پی مودم برای کشف هنگ کردن یا جیتر بالا",
        metricValue = "آماده بررسی",
        severity = CheckSeverity.IDLE
    ),
    DiagnosticCheckItem(
        id = "dns",
        title = "سرعت و سلامت سرور DNS",
        description = "تشخیص کندی DNS مخابرات یا خطای ترجمه آدرس‌ها و انتخاب سریع‌ترین DNS",
        metricValue = "آماده بررسی",
        severity = CheckSeverity.IDLE
    ),
    DiagnosticCheckItem(
        id = "internet",
        title = "دسترسی واقعی اینترنت و پورتال",
        description = "تایید برقراری خط اینترنت روی مودم و نبود خطای «متصل بدون اینترنت»",
        metricValue = "آماده بررسی",
        severity = CheckSeverity.IDLE
    ),
    DiagnosticCheckItem(
        id = "speed",
        title = "بررسی گلوگاه سرعت و پینگ تحت بار",
        description = "سنجش پهنای باند دانلود و کشف افت سرعت ناشی از Bufferbloat",
        metricValue = "آماده بررسی",
        severity = CheckSeverity.IDLE
    ),
    DiagnosticCheckItem(
        id = "channel",
        title = "تداخل کانال وای‌فای اطراف",
        description = "بررسی شلوغی کانال مودم نسبت به مودم‌های همسایه",
        metricValue = "آماده بررسی",
        severity = CheckSeverity.IDLE
    )
)

class ModemViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = ModemDiagnosticEngine(application)
    private val repository = DiagnosticRepository(AppDatabase.getInstance(application).diagnosticDao())
    private val connectivityManager =
        application.getSystemService(ConnectivityManager::class.java)

    private val _uiState = MutableStateFlow(
        ModemUiState(hasLocationPermission = engine.hasLocationPermission())
    )
    val uiState: StateFlow<ModemUiState> = _uiState.asStateFlow()

    val historyRecords: StateFlow<List<DiagnosticRecordEntity>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private var diagnosticJob: Job? = null
    private var speedTestJob: Job? = null

    private var lastGatewayPingMs: Int = 0
    private var lastInternetPingMs: Int = 0
    private var lastJitterMs: Int = 0
    private var lastPacketLoss: Int = 0
    private var lastQuickDownloadMbps: Double = 0.0

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshTelemetry(probeRouterHttp = false)
        }

        override fun onLost(network: Network) {
            refreshTelemetry(probeRouterHttp = false)
        }
    }

    init {
        registerNetworkObserver()
        refreshTelemetry(probeRouterHttp = true)
    }

    private fun registerNetworkObserver() {
        try {
            connectivityManager?.registerNetworkCallback(
                NetworkRequest.Builder().build(),
                networkCallback
            )
        } catch (_: Exception) {
            // Safe fallback if system limits callbacks
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted || engine.hasLocationPermission()) }
        refreshTelemetry(probeRouterHttp = false)
        if (granted) {
            scanWifiChannels()
        } else {
            showMessage("بدون مجوز موقعیت مکانی، اسکن کانال اطراف غیرفعال می‌ماند؛ سایر عیب‌یابی‌ها فعال هستند.")
        }
    }

    fun refreshTelemetry(probeRouterHttp: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTelemetry = true) }
            val telemetry = engine.readTelemetry(probeRouterHttp = probeRouterHttp)
            _uiState.update { state ->
                val initialScore = if (!state.hasScannedOnce) {
                    if (!telemetry.isConnected) 15
                    else if (!telemetry.isValidated) 45
                    else telemetry.signalPercent.coerceIn(55, 92)
                } else {
                    state.healthScore
                }
                state.copy(
                    telemetry = telemetry,
                    isRefreshingTelemetry = false,
                    healthScore = initialScore,
                    hasLocationPermission = engine.hasLocationPermission()
                )
            }
        }
    }

    fun startDiagnostics(autoFixAfter: Boolean) {
        diagnosticJob?.cancel()
        diagnosticJob = viewModelScope.launch {
            val checks = defaultIdleChecks().toMutableList()
            _uiState.update {
                it.copy(
                    isDiagnosing = true,
                    isAutoFixing = false,
                    autoFixSteps = emptyList(),
                    beforeAfter = null,
                    diagnosticChecks = checks.toList()
                )
            }

            fun updateCheck(index: Int, transform: (DiagnosticCheckItem) -> DiagnosticCheckItem) {
                checks[index] = transform(checks[index])
                _uiState.update { it.copy(diagnosticChecks = checks.toList()) }
            }

            // 1. Signal & Wi-Fi Link Check
            updateCheck(0) { it.copy(severity = CheckSeverity.RUNNING, metricValue = "در حال بررسی…") }
            val telemetry = engine.readTelemetry(probeRouterHttp = true)
            _uiState.update { it.copy(telemetry = telemetry) }
            delay(200)

            val signalCheck = when {
                !telemetry.isConnected -> DiagnosticCheckItem(
                    id = "signal",
                    title = "سلامت سیگنال وای‌فای و لینک مودم",
                    description = "هیچ اتصال شبکه‌ای فعال نیست. وای‌فای دستگاه را روشن کنید.",
                    metricValue = "قطع",
                    severity = CheckSeverity.FAULT,
                    recommendation = "اتصال به وای‌فای مودم را بررسی کنید."
                )
                telemetry.transportType == NetworkTransportType.WIFI && telemetry.signalPercent < 45 -> DiagnosticCheckItem(
                    id = "signal",
                    title = "سلامت سیگنال وای‌فای و لینک مودم",
                    description = "قدرت سیگنال ضعیف است و باعث افت سرعت و قطعی لحظه‌ای می‌شود.",
                    metricValue = "${telemetry.rssiDbm ?: -80} dBm (${telemetry.signalPercent}%)",
                    severity = CheckSeverity.WARNING,
                    recommendation = "به مودم نزدیک‌تر شوید یا کانال وای‌فای را بهینه کنید."
                )
                telemetry.transportType == NetworkTransportType.WIFI -> DiagnosticCheckItem(
                    id = "signal",
                    title = "سلامت سیگنال وای‌فای و لینک مودم",
                    description = "سیگنال وای‌فای پایدار روی باند ${telemetry.bandLabel}",
                    metricValue = "${telemetry.rssiDbm ?: -55} dBm • ${telemetry.linkSpeedMbps ?: 0} Mbps",
                    severity = CheckSeverity.HEALTHY
                )
                else -> DiagnosticCheckItem(
                    id = "signal",
                    title = "سلامت اتصال شبکه",
                    description = "متصل از طریق ${telemetry.transportType.persianLabel}",
                    metricValue = "متصل",
                    severity = CheckSeverity.HEALTHY
                )
            }
            updateCheck(0) { signalCheck }

            // 2. Gateway / Modem Ping & Jitter
            updateCheck(1) { it.copy(severity = CheckSeverity.RUNNING, metricValue = "ارسال بسته به مودم…") }
            val gwProbe = engine.probeGatewayLatency(telemetry.gatewayIp, samples = 6)
            lastGatewayPingMs = gwProbe.avgPingMs
            val gwCheck = when {
                !gwProbe.reachable -> DiagnosticCheckItem(
                    id = "gateway",
                    title = "پاسخ‌دهی مودم (دروازه شبکه)",
                    description = "مودم (${telemetry.gatewayIp}) به درخواست‌ها پاسخ نداد. احتمال هنگ کردن مودم وجود دارد.",
                    metricValue = "بدون پاسخ",
                    severity = CheckSeverity.FAULT,
                    recommendation = "بازنشانی مسیر شبکه و در صورت نیاز ریبوت مودم"
                )
                gwProbe.avgPingMs > 25 || gwProbe.packetLossPercent > 0 -> DiagnosticCheckItem(
                    id = "gateway",
                    title = "پاسخ‌دهی مودم (دروازه شبکه)",
                    description = "تاخیر داخلی مودم بالاتر از حد استاندارد است (پکت لاس ${gwProbe.packetLossPercent}%).",
                    metricValue = "${gwProbe.avgPingMs} ms • جیتر ${gwProbe.jitterMs} ms",
                    severity = CheckSeverity.WARNING,
                    recommendation = "پاکسازی سوکت‌ها و کاهش تداخل وای‌فای"
                )
                else -> DiagnosticCheckItem(
                    id = "gateway",
                    title = "پاسخ‌دهی مودم (دروازه شبکه)",
                    description = "ارتباط مستقیم گوشی با مودم سریع و بدون پکت لاس است.",
                    metricValue = "${gwProbe.avgPingMs} ms • جیتر ${gwProbe.jitterMs} ms",
                    severity = CheckSeverity.HEALTHY
                )
            }
            updateCheck(1) { gwCheck }

            // 3. DNS Speed & Comparison
            updateCheck(2) { it.copy(severity = CheckSeverity.RUNNING, metricValue = "تست سرورهای DNS…") }
            val dnsList = engine.benchmarkDnsServers(telemetry.dnsServers)
            _uiState.update { it.copy(dnsBenchmarks = dnsList) }
            val fastestDns = dnsList.firstOrNull { it.isFastest } ?: dnsList.firstOrNull { it.reachable }
            val currentDnsItem = dnsList.firstOrNull { it.isCurrentSystem }

            val dnsCheck = when {
                fastestDns == null -> DiagnosticCheckItem(
                    id = "dns",
                    title = "سرعت و سلامت سرور DNS",
                    description = "هیچ سرور DNS در دسترس نیست. خط اینترنت مودم را بررسی کنید.",
                    metricValue = "خطای DNS",
                    severity = CheckSeverity.FAULT,
                    recommendation = "اعمال DNS سریع مانند 1.1.1.1"
                )
                currentDnsItem != null && currentDnsItem.latencyMs != null &&
                    fastestDns.latencyMs != null && currentDnsItem.latencyMs > fastestDns.latencyMs + 25 -> DiagnosticCheckItem(
                    id = "dns",
                    title = "سرعت و سلامت سرور DNS",
                    description = "DNS فعلی کندتر از ${fastestDns.name} است. تغییر به ${fastestDns.primaryIp} باز شدن صفحات را سریع‌تر می‌کند.",
                    metricValue = "${currentDnsItem.latencyMs} ms → بهینه: ${fastestDns.latencyMs} ms",
                    severity = CheckSeverity.WARNING,
                    recommendation = "تغییر خودکار DNS به ${fastestDns.name}"
                )
                else -> DiagnosticCheckItem(
                    id = "dns",
                    title = "سرعت و سلامت سرور DNS",
                    description = "پاسخ‌دهی DNS مناسب است. بهترین گزینه پیشنهادی: ${fastestDns.name}",
                    metricValue = "${fastestDns.latencyMs ?: 0} ms (${fastestDns.primaryIp})",
                    severity = CheckSeverity.HEALTHY
                )
            }
            updateCheck(2) { dnsCheck }

            // 4. Internet Validation & Captive Portal
            updateCheck(3) { it.copy(severity = CheckSeverity.RUNNING, metricValue = "بررسی خط اینترنت…") }
            val internetProbe = engine.probeInternetLatency(samples = 6)
            val (httpOk, httpRtt) = engine.verifyHttpInternetAccess()
            lastInternetPingMs = internetProbe.avgPingMs
            lastJitterMs = internetProbe.jitterMs
            lastPacketLoss = internetProbe.packetLossPercent

            val internetCheck = when {
                telemetry.hasCaptivePortal -> DiagnosticCheckItem(
                    id = "internet",
                    title = "دسترسی واقعی اینترنت و پورتال",
                    description = "شبکه نیاز به ورود در صفحه پورتال (Captive Portal) دارد.",
                    metricValue = "نیاز به ورود",
                    severity = CheckSeverity.WARNING
                )
                !httpOk && !internetProbe.reachable -> DiagnosticCheckItem(
                    id = "internet",
                    title = "دسترسی واقعی اینترنت و پورتال",
                    description = "مودم متصل است اما خط اینترنت برقرار نیست (خطای PPPoE یا قطع خط از ISP).",
                    metricValue = "اینترنت قطع",
                    severity = CheckSeverity.FAULT,
                    recommendation = "ریبوت مودم و بررسی چراغ Internet/DSL"
                )
                internetProbe.packetLossPercent > 5 || internetProbe.avgPingMs > 150 -> DiagnosticCheckItem(
                    id = "internet",
                    title = "دسترسی واقعی اینترنت و پورتال",
                    description = "اینترنت وصل است اما پینگ یا پکت لاس (${internetProbe.packetLossPercent}%) بالاست.",
                    metricValue = "${internetProbe.avgPingMs} ms • لاس ${internetProbe.packetLossPercent}%",
                    severity = CheckSeverity.WARNING
                )
                else -> DiagnosticCheckItem(
                    id = "internet",
                    title = "دسترسی واقعی اینترنت و پورتال",
                    description = "اتصال اینترنت تایید شد و بدون قطعی است.",
                    metricValue = "${internetProbe.avgPingMs} ms (HTTP $httpRtt ms)",
                    severity = CheckSeverity.HEALTHY
                )
            }
            updateCheck(3) { internetCheck }

            // 5. Speed & Bufferbloat Probe
            updateCheck(4) { it.copy(severity = CheckSeverity.RUNNING, metricValue = "تست سرعت لحظه‌ای…") }
            val (quickMbps, loadedPing) = if (httpOk || internetProbe.reachable) {
                engine.runQuickDownloadProbe()
            } else {
                0.0 to 0
            }
            lastQuickDownloadMbps = quickMbps
            val bufferbloatDelta = if (loadedPing > 0 && internetProbe.avgPingMs > 0) {
                (loadedPing - internetProbe.avgPingMs).coerceAtLeast(0)
            } else 0

            val speedCheck = when {
                quickMbps <= 0.1 -> DiagnosticCheckItem(
                    id = "speed",
                    title = "بررسی گلوگاه سرعت و پینگ تحت بار",
                    description = "امکان دریافت داده از سرور تست سرعت وجود نداشت.",
                    metricValue = "0.0 Mbps",
                    severity = CheckSeverity.FAULT
                )
                quickMbps < 5.0 || bufferbloatDelta > 80 -> DiagnosticCheckItem(
                    id = "speed",
                    title = "بررسی گلوگاه سرعت و پینگ تحت بار",
                    description = if (bufferbloatDelta > 80) {
                        "افزایش شدید پینگ تحت بار (+$bufferbloatDelta ms) شناسایی شد."
                    } else {
                        "سرعت دانلود پایین‌تر از حد مطلوب است."
                    },
                    metricValue = "${"%.1f".format(quickMbps)} Mbps",
                    severity = CheckSeverity.WARNING,
                    recommendation = "پاکسازی اتصالات و بهینه‌سازی کانال و DNS"
                )
                else -> DiagnosticCheckItem(
                    id = "speed",
                    title = "بررسی گلوگاه سرعت و پینگ تحت بار",
                    description = "سرعت دانلود مناسب و گلوگاه محسوسی در مسیر دیده نشد.",
                    metricValue = "${"%.1f".format(quickMbps)} Mbps",
                    severity = CheckSeverity.HEALTHY
                )
            }
            updateCheck(4) { speedCheck }

            // 6. Wi-Fi Channel Congestion Check
            updateCheck(5) { it.copy(severity = CheckSeverity.RUNNING, metricValue = "تحلیل کانال…") }
            val channelReport = if (engine.hasLocationPermission() && telemetry.transportType == NetworkTransportType.WIFI) {
                val report = engine.scanWifiChannels(telemetry.wifiChannel)
                _uiState.update { it.copy(channelReport = report) }
                report
            } else {
                null
            }

            val channelCheck = when {
                telemetry.transportType != NetworkTransportType.WIFI -> DiagnosticCheckItem(
                    id = "channel",
                    title = "تداخل کانال وای‌فای اطراف",
                    description = "در اتصال غیر وای‌فای نیازی به تحلیل کانال نیست.",
                    metricValue = "غیرفعال",
                    severity = CheckSeverity.HEALTHY
                )
                channelReport == null -> DiagnosticCheckItem(
                    id = "channel",
                    title = "تداخل کانال وای‌فای اطراف",
                    description = "برای بررسی شلوغی کانال مودم‌های همسایه، در تب کنترل مودم اسکن کانال را فعال کنید.",
                    metricValue = "نیاز به مجوز",
                    severity = CheckSeverity.WARNING,
                    autoFixable = false
                )
                channelReport.currentChannel != null &&
                    channelReport.currentChannel in 1..13 &&
                    channelReport.currentChannel != channelReport.recommendedChannel -> DiagnosticCheckItem(
                    id = "channel",
                    title = "تداخل کانال وای‌فای اطراف",
                    description = channelReport.congestionSummary,
                    metricValue = "فعلی: ${channelReport.currentChannel} → پیشنهادی: ${channelReport.recommendedChannel}",
                    severity = CheckSeverity.WARNING
                )
                else -> DiagnosticCheckItem(
                    id = "channel",
                    title = "تداخل کانال وای‌فای اطراف",
                    description = channelReport.congestionSummary.ifBlank { "کانال فعلی وای‌فای وضعیت مطلوبی دارد." },
                    metricValue = "کانال بهینه: ${channelReport.recommendedChannel}",
                    severity = CheckSeverity.HEALTHY
                )
            }
            updateCheck(5) { channelCheck }

            val healthScore = engine.computeHealthScore(
                isConnected = telemetry.isConnected,
                isValidated = httpOk || internetProbe.reachable,
                signalPercent = telemetry.signalPercent,
                gatewayProbe = gwProbe,
                internetProbe = internetProbe,
                downloadMbps = quickMbps
            )

            val issuesCount = checks.count {
                it.severity == CheckSeverity.FAULT || it.severity == CheckSeverity.WARNING
            }

            _uiState.update {
                it.copy(
                    isDiagnosing = false,
                    hasScannedOnce = true,
                    healthScore = healthScore
                )
            }

            if (autoFixAfter) {
                runAutoFixInternal(
                    telemetry = telemetry,
                    beforeScore = healthScore,
                    issuesCount = issuesCount
                )
            } else {
                saveDiagnosticRecord(
                    type = "DIAGNOSTIC",
                    telemetry = telemetry,
                    score = healthScore,
                    gwPing = gwProbe.avgPingMs,
                    netPing = internetProbe.avgPingMs,
                    jitter = internetProbe.jitterMs,
                    loss = internetProbe.packetLossPercent,
                    download = quickMbps,
                    upload = 0.0,
                    issuesFound = issuesCount,
                    issuesFixed = 0,
                    recommendedDns = fastestDns?.primaryIp ?: "1.1.1.1",
                    summary = if (issuesCount == 0) {
                        "مودم و اتصال اینترنت در وضعیت سالم قرار دارند."
                    } else {
                        "$issuesCount مورد نیازمند بهینه‌سازی شناسایی شد."
                    }
                )
            }
        }
    }

    fun runAutoFixNow() {
        if (_uiState.value.isDiagnosing || _uiState.value.isAutoFixing) return
        diagnosticJob?.cancel()
        diagnosticJob = viewModelScope.launch {
            val currentState = _uiState.value
            val issuesCount = currentState.diagnosticChecks.count {
                it.severity == CheckSeverity.FAULT || it.severity == CheckSeverity.WARNING
            }
            runAutoFixInternal(
                telemetry = currentState.telemetry,
                beforeScore = currentState.healthScore.takeIf { it > 0 } ?: 60,
                issuesCount = issuesCount
            )
        }
    }

    private suspend fun runAutoFixInternal(
        telemetry: NetworkTelemetry,
        beforeScore: Int,
        issuesCount: Int
    ) {
        _uiState.update { it.copy(isAutoFixing = true, beforeAfter = null) }

        val result = engine.executeAutoFixSequence(
            telemetry = telemetry,
            beforeScore = beforeScore,
            beforeGatewayPing = lastGatewayPingMs,
            beforeInternetPing = lastInternetPingMs,
            beforeDownloadMbps = lastQuickDownloadMbps
        ) { updatedSteps ->
            _uiState.update { it.copy(autoFixSteps = updatedSteps) }
        }

        // Update diagnostic check badges that were resolved
        val refreshedChecks = _uiState.value.diagnosticChecks.map { check ->
            when {
                check.id == "dns" && check.severity != CheckSeverity.HEALTHY ->
                    check.copy(
                        severity = CheckSeverity.FIXED,
                        metricValue = "${result.selectedDnsName} (${result.selectedDnsIp})"
                    )
                check.id == "gateway" && result.afterGatewayPingMs in 1..30 && check.severity != CheckSeverity.HEALTHY ->
                    check.copy(
                        severity = CheckSeverity.FIXED,
                        metricValue = "${result.afterGatewayPingMs} ms"
                    )
                check.id == "speed" && result.afterDownloadMbps >= 5.0 && check.severity != CheckSeverity.HEALTHY ->
                    check.copy(
                        severity = CheckSeverity.FIXED,
                        metricValue = "${"%.1f".format(result.afterDownloadMbps)} Mbps"
                    )
                else -> check
            }
        }

        _uiState.update {
            it.copy(
                isAutoFixing = false,
                healthScore = maxOf(result.afterScore, it.healthScore),
                diagnosticChecks = refreshedChecks,
                beforeAfter = result,
                snackbarMessage = "رفع عیب خودکار انجام شد (${result.fixedIssuesCount} اقدام بهینه‌سازی موفق)"
            )
        }

        saveDiagnosticRecord(
            type = "AUTO_FIX",
            telemetry = telemetry,
            score = result.afterScore,
            gwPing = result.afterGatewayPingMs,
            netPing = result.afterInternetPingMs,
            jitter = lastJitterMs,
            loss = lastPacketLoss,
            download = result.afterDownloadMbps,
            upload = 0.0,
            issuesFound = issuesCount,
            issuesFixed = result.fixedIssuesCount,
            recommendedDns = result.selectedDnsIp,
            summary = "رفع عیب خودکار تکمیل شد • بهترین DNS: ${result.selectedDnsName} (${result.selectedDnsIp})"
        )
    }

    fun stopCurrentOperation() {
        diagnosticJob?.cancel()
        speedTestJob?.cancel()
        _uiState.update {
            it.copy(
                isDiagnosing = false,
                isAutoFixing = false,
                speedTest = if (it.speedTest.phase != SpeedTestPhase.COMPLETED) {
                    it.speedTest.copy(phase = SpeedTestPhase.IDLE, progress = 0f)
                } else {
                    it.speedTest
                }
            )
        }
    }

    fun startSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            val finalState = engine.runFullSpeedTest { liveState ->
                _uiState.update { it.copy(speedTest = liveState) }
            }
            if (finalState.phase == SpeedTestPhase.COMPLETED) {
                val telemetry = _uiState.value.telemetry
                saveDiagnosticRecord(
                    type = "SPEED_TEST",
                    telemetry = telemetry,
                    score = _uiState.value.healthScore.takeIf { it > 0 } ?: 85,
                    gwPing = lastGatewayPingMs,
                    netPing = finalState.idlePingMs,
                    jitter = finalState.jitterMs,
                    loss = finalState.packetLossPercent,
                    download = finalState.downloadMbps,
                    upload = finalState.uploadMbps,
                    issuesFound = 0,
                    issuesFixed = 0,
                    recommendedDns = telemetry.dnsServers.firstOrNull() ?: "1.1.1.1",
                    summary = "تست سرعت: دانلود ${"%.1f".format(finalState.downloadMbps)} و آپلود ${"%.1f".format(finalState.uploadMbps)} مگابیت"
                )
            }
        }
    }

    fun runDnsBenchmark() {
        if (_uiState.value.isBenchmarkingDns) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBenchmarkingDns = true) }
            val results = engine.benchmarkDnsServers(_uiState.value.telemetry.dnsServers)
            _uiState.update {
                it.copy(
                    dnsBenchmarks = results,
                    isBenchmarkingDns = false,
                    snackbarMessage = results.firstOrNull { d -> d.isFastest }?.let { best ->
                        "سریع‌ترین سرور DNS: ${best.name} (${best.latencyMs} ms)"
                    } ?: "تست سرورهای DNS انجام شد."
                )
            }
        }
    }

    fun scanWifiChannels() {
        if (_uiState.value.isScanningChannels) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningChannels = true) }
            val telemetry = engine.readTelemetry(probeRouterHttp = false)
            val report = engine.scanWifiChannels(telemetry.wifiChannel)
            _uiState.update {
                it.copy(
                    telemetry = telemetry,
                    channelReport = report,
                    isScanningChannels = false,
                    hasLocationPermission = engine.hasLocationPermission()
                )
            }
        }
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteRecord(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
            showMessage("تمام گزارش‌های قبلی پاک شدند.")
        }
    }

    private suspend fun saveDiagnosticRecord(
        type: String,
        telemetry: NetworkTelemetry,
        score: Int,
        gwPing: Int,
        netPing: Int,
        jitter: Int,
        loss: Int,
        download: Double,
        upload: Double,
        issuesFound: Int,
        issuesFixed: Int,
        recommendedDns: String,
        summary: String
    ) {
        repository.saveRecord(
            DiagnosticRecordEntity(
                recordType = type,
                networkName = telemetry.networkName,
                gatewayIp = telemetry.gatewayIp,
                healthScore = score,
                gatewayPingMs = gwPing,
                internetPingMs = netPing,
                jitterMs = jitter,
                packetLossPercent = loss,
                downloadMbps = download,
                uploadMbps = upload,
                issuesFound = issuesFound,
                issuesFixed = issuesFixed,
                recommendedDns = recommendedDns,
                summaryText = summary
            )
        )
    }
}
