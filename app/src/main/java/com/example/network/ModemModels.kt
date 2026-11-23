package com.example.network

enum class NetworkTransportType(val persianLabel: String) {
    WIFI("وای‌فای"),
    ETHERNET("اترنت / کابل"),
    CELLULAR("دیتای موبایل"),
    NONE("بدون اتصال")
}

data class NetworkTelemetry(
    val transportType: NetworkTransportType = NetworkTransportType.NONE,
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val hasCaptivePortal: Boolean = false,
    val networkName: String = "نامشخص",
    val gatewayIp: String = "نامشخص",
    val localIp: String = "نامشخص",
    val dnsServers: List<String> = emptyList(),
    val rssiDbm: Int? = null,
    val signalPercent: Int = 0,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val wifiChannel: Int? = null,
    val bandLabel: String = "نامشخص",
    val routerVendor: String = "مودم محلی",
    val routerAdminReachable: Boolean = false,
    val mtu: Int = 0
)

enum class CheckSeverity {
    IDLE,
    RUNNING,
    HEALTHY,
    WARNING,
    FAULT,
    FIXED
}

data class DiagnosticCheckItem(
    val id: String,
    val title: String,
    val description: String,
    val metricValue: String,
    val severity: CheckSeverity,
    val autoFixable: Boolean = true,
    val recommendation: String = ""
)

enum class FixStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ACTION_NEEDED,
    FAILED
}

data class AutoFixStep(
    val id: String,
    val title: String,
    val detail: String,
    val status: FixStepStatus
)

data class BeforeAfterComparison(
    val beforeScore: Int,
    val afterScore: Int,
    val beforeGatewayPingMs: Int,
    val afterGatewayPingMs: Int,
    val beforeInternetPingMs: Int,
    val afterInternetPingMs: Int,
    val beforeDownloadMbps: Double,
    val afterDownloadMbps: Double,
    val selectedDnsName: String,
    val selectedDnsIp: String,
    val selectedDotHostname: String,
    val fixedIssuesCount: Int
)

data class DnsBenchmarkItem(
    val id: String,
    val name: String,
    val primaryIp: String,
    val secondaryIp: String,
    val dotHostname: String,
    val latencyMs: Int?,
    val reachable: Boolean,
    val isFastest: Boolean = false,
    val isCurrentSystem: Boolean = false
)

data class ChannelCongestionInfo(
    val channel: Int,
    val apCount: Int,
    val interferenceScore: Int, // 0..100 (higher = more crowded)
    val isCurrentChannel: Boolean = false,
    val isRecommended: Boolean = false
)

data class WifiChannelReport(
    val channels24Ghz: List<ChannelCongestionInfo> = emptyList(),
    val nearby5GhzCount: Int = 0,
    val totalNetworksFound: Int = 0,
    val currentChannel: Int? = null,
    val recommendedChannel: Int = 1,
    val congestionSummary: String = "",
    val scannedAt: Long = 0L
)

enum class SpeedTestPhase(val persianLabel: String) {
    IDLE("آماده برای تست سرعت"),
    PING("اندازه‌گیری پینگ، جیتر و پکت لاس…"),
    DOWNLOAD("اندازه‌گیری سرعت دانلود و پینگ تحت بار…"),
    UPLOAD("اندازه‌گیری سرعت آپلود…"),
    COMPLETED("تست سرعت با موفقیت تکمیل شد"),
    ERROR("خطا در انجام تست سرعت")
}

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val liveSpeedMbps: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val idlePingMs: Int = 0,
    val loadedPingMs: Int = 0,
    val jitterMs: Int = 0,
    val packetLossPercent: Int = 0,
    val progress: Float = 0f,
    val downloadSamples: List<Float> = emptyList(),
    val uploadSamples: List<Float> = emptyList(),
    val errorMessage: String? = null
)

data class PingProbeResult(
    val avgPingMs: Int,
    val jitterMs: Int,
    val packetLossPercent: Int,
    val reachable: Boolean
)
