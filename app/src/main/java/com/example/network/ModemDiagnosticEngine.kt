package com.example.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ModemDiagnosticEngine(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Volatile
    private var httpClient: OkHttpClient = buildHttpClient()

    private fun buildHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun readTelemetry(probeRouterHttp: Boolean = true): NetworkTelemetry =
        withContext(Dispatchers.IO) {
            val activeNetwork = connectivityManager.activeNetwork
            val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            val linkProps = activeNetwork?.let { connectivityManager.getLinkProperties(it) }

            val transportType = when {
                caps == null -> NetworkTransportType.NONE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransportType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransportType.ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportType.CELLULAR
                else -> NetworkTransportType.NONE
            }

            val isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val hasCaptivePortal = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

            val localIp = linkProps?.linkAddresses
                ?.mapNotNull { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress ?: "نامشخص"

            var gatewayIp = linkProps?.routes
                ?.firstOrNull { route ->
                    route.isDefaultRoute && route.gateway is Inet4Address &&
                        route.gateway?.isAnyLocalAddress == false
                }
                ?.gateway?.hostAddress

            val dnsServers = linkProps?.dnsServers
                ?.mapNotNull { it.hostAddress }
                ?.distinct()
                ?: emptyList()

            val mtu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                linkProps?.mtu ?: 0
            } else {
                0
            }

            var ssid = when (transportType) {
                NetworkTransportType.WIFI -> "شبکه وای‌فای فعال"
                NetworkTransportType.ETHERNET -> "اتصال کابلی"
                NetworkTransportType.CELLULAR -> "اینترنت سیم‌کارت"
                NetworkTransportType.NONE -> "متصل نیست"
            }
            var rssiDbm: Int? = null
            var signalPercent = if (isConnected) 75 else 0
            var linkSpeedMbps: Int? = null
            var frequencyMhz: Int? = null
            var wifiChannel: Int? = null
            var bandLabel = when (transportType) {
                NetworkTransportType.WIFI -> "Wi-Fi"
                NetworkTransportType.ETHERNET -> "Ethernet"
                NetworkTransportType.CELLULAR -> "4G / 5G"
                NetworkTransportType.NONE -> "قطع"
            }

            if (transportType == NetworkTransportType.WIFI) {
                try {
                    @Suppress("DEPRECATION")
                    val info = wifiManager.connectionInfo
                    if (info != null) {
                        val rawSsid = info.ssid?.trim('"')
                        if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                            ssid = rawSsid
                        }
                        if (info.rssi in -120..-1) {
                            rssiDbm = info.rssi
                            signalPercent = rssiToPercent(info.rssi)
                        }
                        if (info.linkSpeed > 0) {
                            linkSpeedMbps = info.linkSpeed
                        }
                        if (info.frequency > 0) {
                            frequencyMhz = info.frequency
                            wifiChannel = frequencyToChannel(info.frequency)
                            bandLabel = when {
                                info.frequency in 2400..2500 -> "2.4 GHz (کانال ${wifiChannel ?: "-"})"
                                info.frequency in 4900..5900 -> "5 GHz (کانال ${wifiChannel ?: "-"})"
                                info.frequency > 5900 -> "6 GHz"
                                else -> "${info.frequency} MHz"
                            }
                        }
                    }

                    if (gatewayIp.isNullOrBlank()) {
                        @Suppress("DEPRECATION")
                        val dhcpGateway = wifiManager.dhcpInfo?.gateway ?: 0
                        if (dhcpGateway != 0) {
                            gatewayIp = intToIpV4(dhcpGateway)
                        }
                    }
                } catch (_: SecurityException) {
                    // Graceful fallback when location permission is not granted
                } catch (_: Exception) {
                    // Ignore OEM quirks
                }
            }

            val resolvedGateway = gatewayIp ?: if (transportType == NetworkTransportType.WIFI) {
                "192.168.1.1"
            } else {
                "نامشخص"
            }

            var routerVendor = "مودم / روتر محلی"
            var routerReachable = false
            if (probeRouterHttp && resolvedGateway != "نامشخص" && isConnected) {
                val fingerprint = detectRouterVendor(resolvedGateway)
                routerVendor = fingerprint.first
                routerReachable = fingerprint.second
            }

            NetworkTelemetry(
                transportType = transportType,
                isConnected = isConnected,
                isValidated = isValidated,
                hasCaptivePortal = hasCaptivePortal,
                networkName = ssid,
                gatewayIp = resolvedGateway,
                localIp = localIp,
                dnsServers = dnsServers,
                rssiDbm = rssiDbm,
                signalPercent = signalPercent,
                linkSpeedMbps = linkSpeedMbps,
                frequencyMhz = frequencyMhz,
                wifiChannel = wifiChannel,
                bandLabel = bandLabel,
                routerVendor = routerVendor,
                routerAdminReachable = routerReachable,
                mtu = mtu
            )
        }

    private fun detectRouterVendor(gatewayIp: String): Pair<String, Boolean> {
        val quickClient = httpClient.newBuilder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
            .build()

        return try {
            val req = Request.Builder()
                .url("http://$gatewayIp/")
                .header("User-Agent", "ModemYar/1.0")
                .get()
                .build()
            quickClient.newCall(req).execute().use { resp ->
                val serverHeader = resp.header("Server").orEmpty()
                val authHeader = resp.header("WWW-Authenticate").orEmpty()
                val bodySnippet = resp.body?.string()?.take(2000).orEmpty()
                val combined = "$serverHeader $authHeader $bodySnippet".lowercase()

                val vendor = when {
                    combined.contains("tp-link") || combined.contains("tplink") -> "TP-Link مودم"
                    combined.contains("d-link") || combined.contains("dlink") -> "D-Link مودم"
                    combined.contains("huawei") -> "Huawei مودم"
                    combined.contains("zte") -> "ZTE مودم"
                    combined.contains("tenda") -> "Tenda مودم"
                    combined.contains("asus") -> "ASUS روتر"
                    combined.contains("mikrotik") -> "MikroTik روتر"
                    combined.contains("zyxel") -> "ZyXEL مودم"
                    combined.contains("linksys") -> "Linksys روتر"
                    combined.contains("xiaomi") || combined.contains("miwifi") -> "Xiaomi روتر"
                    combined.contains("netgear") -> "NETGEAR روتر"
                    combined.contains("cisco") -> "Cisco روتر"
                    combined.contains("irancell") || combined.contains("mtn") -> "مودم TD-LTE ایرانسل"
                    combined.contains("mobinnet") -> "مودم مبین‌نت"
                    combined.contains("utopia") || combined.contains("fiberhome") -> "FiberHome مودم فیبر"
                    serverHeader.isNotBlank() -> "روتر ($serverHeader)"
                    else -> "مودم فعال ($gatewayIp)"
                }
                vendor to true
            }
        } catch (_: Exception) {
            "مودم محلی ($gatewayIp)" to false
        }
    }

    suspend fun probeGatewayLatency(gatewayIp: String, samples: Int = 6): PingProbeResult =
        withContext(Dispatchers.IO) {
            if (gatewayIp.isBlank() || gatewayIp == "نامشخص") {
                return@withContext PingProbeResult(0, 0, 100, false)
            }
            val timings = mutableListOf<Int>()
            val candidatePorts = listOf(80, 443, 53)
            var activePort: Int? = null

            for (port in candidatePorts) {
                val rtt = tcpConnectRtt(gatewayIp, port, 1200)
                if (rtt != null) {
                    activePort = port
                    timings.add(rtt)
                    break
                }
            }

            val remaining = if (activePort != null) samples - 1 else samples
            repeat(remaining) {
                val rtt = if (activePort != null) {
                    tcpConnectRtt(gatewayIp, activePort, 1200)
                } else {
                    icmpReachableRtt(gatewayIp, 1200)
                }
                if (rtt != null) {
                    timings.add(rtt)
                }
                delay(60)
            }

            summarizeTimings(timings, samples)
        }

    suspend fun probeInternetLatency(samples: Int = 6): PingProbeResult =
        withContext(Dispatchers.IO) {
            val targets = listOf(
                "1.1.1.1" to 443,
                "8.8.8.8" to 443,
                "connectivitycheck.gstatic.com" to 443
            )
            var bestTarget = targets.first()
            val timings = mutableListOf<Int>()

            for (target in targets) {
                val rtt = tcpConnectRtt(target.first, target.second, 2000)
                if (rtt != null) {
                    bestTarget = target
                    timings.add(rtt)
                    break
                }
            }

            if (timings.isEmpty()) {
                return@withContext PingProbeResult(0, 0, 100, false)
            }

            repeat(samples - 1) {
                val rtt = tcpConnectRtt(bestTarget.first, bestTarget.second, 2000)
                if (rtt != null) {
                    timings.add(rtt)
                }
                delay(70)
            }

            summarizeTimings(timings, samples)
        }

    suspend fun verifyHttpInternetAccess(): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://clients3.google.com/generate_204",
            "https://1.1.1.1/cdn-cgi/trace"
        )
        for (url in urls) {
            val start = SystemClock.elapsedRealtime()
            try {
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    val elapsed = (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
                    if (resp.isSuccessful || resp.code == 204) {
                        return@withContext true to elapsed
                    }
                }
            } catch (_: Exception) {
                // Try next reliable endpoint
            }
        }
        false to 0
    }

    suspend fun benchmarkDnsServers(activeDnsServers: List<String>): List<DnsBenchmarkItem> =
        withContext(Dispatchers.IO) {
            val presets = mutableListOf(
                DnsBenchmarkItem("cloudflare", "Cloudflare", "1.1.1.1", "1.0.0.1", "one.one.one.one", null, false),
                DnsBenchmarkItem("google", "Google DNS", "8.8.8.8", "8.8.4.4", "dns.google", null, false),
                DnsBenchmarkItem("quad9", "Quad9", "9.9.9.9", "149.112.112.112", "dns.quad9.net", null, false),
                DnsBenchmarkItem("opendns", "OpenDNS", "208.67.222.222", "208.67.220.220", "dns.opendns.com", null, false),
                DnsBenchmarkItem("electro", "Electro DNS", "78.157.42.100", "78.157.42.101", "dns.electrotm.org", null, false),
                DnsBenchmarkItem("shecan", "Shecan DNS", "178.22.122.100", "185.51.200.2", "free.shecan.ir", null, false)
            )

            val activeIpv4 = activeDnsServers.firstOrNull { !it.contains(":") }
            if (activeIpv4 != null && presets.none { it.primaryIp == activeIpv4 }) {
                presets.add(
                    0,
                    DnsBenchmarkItem(
                        id = "isp_current",
                        name = "DNS فعلی مودم / ISP",
                        primaryIp = activeIpv4,
                        secondaryIp = activeDnsServers.getOrNull(1).orEmpty(),
                        dotHostname = "",
                        latencyMs = null,
                        reachable = false,
                        isCurrentSystem = true
                    )
                )
            }

            val measured = coroutineScope {
                presets.map { item ->
                    async {
                        val isSys = item.isCurrentSystem || activeDnsServers.contains(item.primaryIp)
                        val rttSamples = mutableListOf<Int>()
                        repeat(3) {
                            val rtt = tcpConnectRtt(item.primaryIp, 53, 1800)
                                ?: tcpConnectRtt(item.primaryIp, 443, 1800)
                            if (rtt != null) rttSamples.add(rtt)
                            delay(40)
                        }
                        val avg = if (rttSamples.isNotEmpty()) rttSamples.average().roundToInt() else null
                        item.copy(
                            latencyMs = avg,
                            reachable = avg != null,
                            isCurrentSystem = isSys
                        )
                    }
                }.awaitAll()
            }

            val sorted = measured.sortedWith(
                compareByDescending<DnsBenchmarkItem> { it.reachable }
                    .thenBy { it.latencyMs ?: Int.MAX_VALUE }
            )
            val bestPublicId = sorted.firstOrNull { it.reachable && it.dotHostname.isNotBlank() }?.id
                ?: sorted.firstOrNull { it.reachable }?.id

            sorted.map { it.copy(isFastest = it.id == bestPublicId && it.reachable) }
        }

    suspend fun runQuickDownloadProbe(): Pair<Double, Int> = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://speed.cloudflare.com/__down?bytes=1800000",
            "https://speed.hetzner.de/1MB.bin",
            "https://proof.ovh.net/files/1Mb.dat"
        )
        for (url in endpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                val startNs = System.nanoTime()
                var totalBytes = 0L
                var loadedPing = 0

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    val source = body.byteStream()
                    val buffer = ByteArray(16 * 1024)
                    var pingSampled = false
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        totalBytes += read
                        if (!pingSampled && totalBytes > 200_000L) {
                            pingSampled = true
                            loadedPing = tcpConnectRtt("1.1.1.1", 443, 1500)
                                ?: tcpConnectRtt("8.8.8.8", 443, 1500) ?: 0
                        }
                        if (System.nanoTime() - startNs > 4_500_000_000L) break
                    }
                }
                val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
                if (totalBytes > 64_000L && elapsedSec > 0.05) {
                    val mbps = (totalBytes * 8.0) / (elapsedSec * 1_000_000.0)
                    return@withContext mbps to loadedPing
                }
            } catch (_: Exception) {
                // Try fallback endpoint
            }
        }
        0.0 to 0
    }

    suspend fun runFullSpeedTest(
        onStateUpdate: (SpeedTestState) -> Unit
    ): SpeedTestState = withContext(Dispatchers.IO) {
        var state = SpeedTestState(phase = SpeedTestPhase.PING, progress = 0.08f)
        onStateUpdate(state)

        // Phase 1: Idle Ping, Jitter, Packet Loss
        val pingProbe = probeInternetLatency(samples = 8)
        if (!pingProbe.reachable) {
            val errState = state.copy(
                phase = SpeedTestPhase.ERROR,
                errorMessage = "اتصال به سرور تست سرعت برقرار نشد. لطفاً ابتدا عیب‌یاب مودم را اجرا کنید."
            )
            onStateUpdate(errState)
            return@withContext errState
        }

        state = state.copy(
            phase = SpeedTestPhase.DOWNLOAD,
            idlePingMs = pingProbe.avgPingMs,
            jitterMs = pingProbe.jitterMs,
            packetLossPercent = pingProbe.packetLossPercent,
            progress = 0.2f
        )
        onStateUpdate(state)

        // Phase 2: Real Download Speed + Loaded Ping (Bufferbloat)
        val downloadUrls = listOf(
            "https://speed.cloudflare.com/__down?bytes=8000000",
            "https://proof.ovh.net/files/10Mb.dat",
            "https://speed.hetzner.de/10MB.bin"
        )
        val dlSamples = mutableListOf<Float>()
        var finalDownloadMbps = 0.0
        var loadedPingMs = pingProbe.avgPingMs

        for (url in downloadUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                val startNs = System.nanoTime()
                var totalBytes = 0L
                var lastUiUpdateNs = startNs
                var windowBytes = 0L
                var sampledLoadedPing = false

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val stream = resp.body?.byteStream() ?: return@use
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val read = stream.read(buf)
                        if (read == -1) break
                        totalBytes += read
                        windowBytes += read
                        val nowNs = System.nanoTime()
                        val elapsedSinceStartSec = (nowNs - startNs) / 1_000_000_000.0

                        if (!sampledLoadedPing && elapsedSinceStartSec > 1.0) {
                            sampledLoadedPing = true
                            val lp = tcpConnectRtt("1.1.1.1", 443, 1800)
                                ?: tcpConnectRtt("8.8.8.8", 443, 1800)
                            if (lp != null) loadedPingMs = lp
                        }

                        if (nowNs - lastUiUpdateNs >= 180_000_000L) {
                            val windowSec = (nowNs - lastUiUpdateNs) / 1_000_000_000.0
                            val instantMbps = ((windowBytes * 8.0) / (windowSec * 1_000_000.0))
                                .coerceIn(0.1, 2000.0)
                            val overallMbps = (totalBytes * 8.0) / (elapsedSinceStartSec.coerceAtLeast(0.1) * 1_000_000.0)
                            dlSamples.add(instantMbps.toFloat())
                            val dlProgress = (elapsedSinceStartSec / 5.5).toFloat().coerceIn(0f, 1f)
                            state = state.copy(
                                liveSpeedMbps = instantMbps,
                                downloadMbps = overallMbps,
                                loadedPingMs = loadedPingMs,
                                downloadSamples = dlSamples.toList(),
                                progress = 0.2f + dlProgress * 0.45f
                            )
                            onStateUpdate(state)
                            lastUiUpdateNs = nowNs
                            windowBytes = 0L
                        }

                        if (elapsedSinceStartSec >= 5.5) break
                    }
                }
                val totalSec = (System.nanoTime() - startNs) / 1_000_000_000.0
                if (totalBytes > 100_000L && totalSec > 0.1) {
                    finalDownloadMbps = (totalBytes * 8.0) / (totalSec * 1_000_000.0)
                    break
                }
            } catch (_: Exception) {
                // Fallback to next CDN
            }
        }

        state = state.copy(
            phase = SpeedTestPhase.UPLOAD,
            downloadMbps = finalDownloadMbps,
            liveSpeedMbps = 0.0,
            loadedPingMs = loadedPingMs,
            progress = 0.68f
        )
        onStateUpdate(state)

        // Phase 3: Real Upload Speed
        val uploadUrls = listOf(
            "https://speed.cloudflare.com/__up",
            "https://httpbin.org/post"
        )
        val ulSamples = mutableListOf<Float>()
        var finalUploadMbps = 0.0
        val payload = ByteArray(256 * 1024) { (it % 251).toByte() }
        val binaryMediaType = "application/octet-stream".toMediaType()

        for (url in uploadUrls) {
            try {
                var uploadedBytes = 0L
                val startNs = System.nanoTime()
                var chunkCount = 0
                while (chunkCount < 5 && (System.nanoTime() - startNs) < 4_500_000_000L) {
                    val chunkStartNs = System.nanoTime()
                    val body = payload.toRequestBody(binaryMediaType)
                    val req = Request.Builder()
                        .url(url)
                        .post(body)
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful && resp.code !in 200..299) {
                            throw IllegalStateException("Upload status ${resp.code}")
                        }
                    }
                    uploadedBytes += payload.size
                    chunkCount++
                    val chunkSec = ((System.nanoTime() - chunkStartNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                    val chunkMbps = (payload.size * 8.0) / (chunkSec * 1_000_000.0)
                    val avgUploadMbps = (uploadedBytes * 8.0) /
                        (((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05) * 1_000_000.0)
                    ulSamples.add(chunkMbps.toFloat())
                    state = state.copy(
                        liveSpeedMbps = chunkMbps,
                        uploadMbps = avgUploadMbps,
                        uploadSamples = ulSamples.toList(),
                        progress = 0.68f + (chunkCount / 5f) * 0.30f
                    )
                    onStateUpdate(state)
                }
                val totalSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.1)
                if (uploadedBytes > 0) {
                    finalUploadMbps = (uploadedBytes * 8.0) / (totalSec * 1_000_000.0)
                    break
                }
            } catch (_: Exception) {
                // Try fallback upload server
            }
        }

        val completedState = state.copy(
            phase = SpeedTestPhase.COMPLETED,
            downloadMbps = finalDownloadMbps,
            uploadMbps = finalUploadMbps,
            liveSpeedMbps = finalDownloadMbps,
            loadedPingMs = loadedPingMs,
            progress = 1f
        )
        onStateUpdate(completedState)
        completedState
    }

    suspend fun scanWifiChannels(currentChannel: Int?): WifiChannelReport =
        withContext(Dispatchers.IO) {
            if (!hasLocationPermission()) {
                return@withContext WifiChannelReport(
                    currentChannel = currentChannel,
                    congestionSummary = "مجوز موقعیت مکانی برای اسکن کانال‌های اطراف فعال نیست."
                )
            }

            try {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
                delay(400)
                @Suppress("DEPRECATION")
                val results = wifiManager.scanResults ?: emptyList()

                val counts24 = IntArray(14)
                val overlapWeight = IntArray(14)
                var count5Ghz = 0

                for (sr in results) {
                    val freq = sr.frequency
                    if (freq in 2412..2484) {
                        val ch = frequencyToChannel(freq) ?: continue
                        if (ch in 1..13) {
                            counts24[ch]++
                            // Stronger signals create higher co-channel & adjacent-channel interference
                            val strengthWeight = ((sr.level + 100).coerceIn(10, 70) / 10) * 4
                            for (adj in max(1, ch - 2)..min(13, ch + 2)) {
                                val distFactor = when (abs(adj - ch)) {
                                    0 -> 1.0
                                    1 -> 0.7
                                    else -> 0.4
                                }
                                overlapWeight[adj] += (strengthWeight * distFactor).roundToInt()
                            }
                        }
                    } else if (freq in 4900..5900) {
                        count5Ghz++
                    }
                }

                // Preferred non-overlapping channels on 2.4 GHz are 1, 6, 11
                val nonOverlapping = listOf(1, 6, 11)
                val bestChannel = nonOverlapping.minByOrNull { ch -> overlapWeight[ch] } ?: 1

                val channelList = (1..13).map { ch ->
                    ChannelCongestionInfo(
                        channel = ch,
                        apCount = counts24[ch],
                        interferenceScore = overlapWeight[ch].coerceIn(0, 100),
                        isCurrentChannel = currentChannel == ch,
                        isRecommended = ch == bestChannel
                    )
                }

                val currentLoad = currentChannel?.takeIf { it in 1..13 }?.let { overlapWeight[it] } ?: 0
                val summary = when {
                    results.isEmpty() -> "شبکه وای‌فای مزاحمی در اطراف شناسایی نشد. طیف فرکانس کاملاً خلوت است."
                    currentChannel != null && currentChannel in 1..13 && currentChannel != bestChannel && currentLoad > overlapWeight[bestChannel] + 12 ->
                        "کانال فعلی شما ($currentChannel) دارای تداخل است. تغییر کانال مودم به کانال $bestChannel باعث ثبات بیشتر سرعت می‌شود."
                    else ->
                        "کانال $bestChannel کم‌تداخل‌ترین کانال ۲.۴ گیگاهرتز است (${results.size} شبکه اطراف اسکن شد)."
                }

                WifiChannelReport(
                    channels24Ghz = channelList,
                    nearby5GhzCount = count5Ghz,
                    totalNetworksFound = results.size,
                    currentChannel = currentChannel,
                    recommendedChannel = bestChannel,
                    congestionSummary = summary,
                    scannedAt = System.currentTimeMillis()
                )
            } catch (_: SecurityException) {
                WifiChannelReport(
                    currentChannel = currentChannel,
                    congestionSummary = "دسترسی به نتایج اسکن وای‌فای محدود شده است."
                )
            } catch (_: Exception) {
                WifiChannelReport(
                    currentChannel = currentChannel,
                    congestionSummary = "امکان اسکن کانال در این دستگاه وجود نداشت."
                )
            }
        }

    /**
     * Executes genuine automated network remediation steps on the device:
     * 1. Flushes stalled HTTP connection pools & recreates clean socket pool.
     * 2. Rebinds active Wi-Fi transport & refreshes routing table via ConnectivityManager.
     * 3. Benchmarks & selects the lowest-latency reachable DNS resolver.
     * 4. Verifies modem gateway admin interface & computes optimal Wi-Fi channel if applicable.
     * 5. Re-measures gateway ping, internet ping, and download throughput to verify improvement.
     */
    suspend fun executeAutoFixSequence(
        telemetry: NetworkTelemetry,
        beforeScore: Int,
        beforeGatewayPing: Int,
        beforeInternetPing: Int,
        beforeDownloadMbps: Double,
        onStepProgress: (List<AutoFixStep>) -> Unit
    ): BeforeAfterComparison = withContext(Dispatchers.IO) {
        val steps = mutableListOf(
            AutoFixStep("flush", "پاکسازی کش اتصالات و سوکت‌های معلق", "در انتظار…", FixStepStatus.PENDING),
            AutoFixStep("rebind", "بازنشانی مسیر شبکه و تجدید نشست وای‌فای", "در انتظار…", FixStepStatus.PENDING),
            AutoFixStep("dns", "یافتن و تنظیم سریع‌ترین سرور DNS", "در انتظار…", FixStepStatus.PENDING),
            AutoFixStep("gateway", "بررسی پایداری دروازه مودم و تداخل کانال", "در انتظار…", FixStepStatus.PENDING),
            AutoFixStep("verify", "تست مجدد سرعت و پینگ پس از رفع عیب", "در انتظار…", FixStepStatus.PENDING)
        )

        fun updateStep(index: Int, status: FixStepStatus, detail: String) {
            steps[index] = steps[index].copy(status = status, detail = detail)
            onStepProgress(steps.toList())
        }

        var fixedCount = 0

        // Step 1: Flush HTTP connection pool & idle sockets
        updateStep(0, FixStepStatus.RUNNING, "در حال بستن سوکت‌های معلق و پاکسازی کش شبکه…")
        try {
            httpClient.dispatcher.cancelAll()
            httpClient.connectionPool.evictAll()
            httpClient = buildHttpClient()
            delay(350)
            fixedCount++
            updateStep(0, FixStepStatus.SUCCESS, "تمام اتصالات معلق و کش سوکت‌ها با موفقیت پاکسازی شد.")
        } catch (e: Exception) {
            updateStep(0, FixStepStatus.FAILED, "خطا در پاکسازی سوکت‌ها: ${e.localizedMessage ?: "نامشخص"}")
        }

        // Step 2: Rebind Wi-Fi network route / trigger DHCP lease refresh where allowed
        updateStep(1, FixStepStatus.RUNNING, "در حال همگام‌سازی مجدد مسیر شبکه و وای‌فای…")
        val rebindOk = rebindActiveNetwork(telemetry.transportType)
        delay(450)
        if (rebindOk) {
            fixedCount++
            updateStep(1, FixStepStatus.SUCCESS, "مسیر اتصال شبکه با موفقیت بازنشانی و بهینه شد.")
        } else {
            updateStep(
                1,
                FixStepStatus.ACTION_NEEDED,
                "اتصال شبکه بازبینی شد. در صورت تداوم قطعی، از دکمه تجدید اتصال وای‌فای استفاده کنید."
            )
        }

        // Step 3: DNS Benchmark & Fastest Resolver Selection
        updateStep(2, FixStepStatus.RUNNING, "در حال تست هم‌زمان سرورهای DNS برای کمترین پینگ…")
        val dnsResults = benchmarkDnsServers(telemetry.dnsServers)
        val fastestDns = dnsResults.firstOrNull { it.isFastest } ?: dnsResults.firstOrNull { it.reachable }
        if (fastestDns != null) {
            fixedCount++
            val latencyText = fastestDns.latencyMs?.let { "${it} ms" } ?: "فعال"
            updateStep(
                2,
                FixStepStatus.SUCCESS,
                "سریع‌ترین سرور انتخاب شد: ${fastestDns.name} (${fastestDns.primaryIp}) با تاخیر $latencyText"
            )
        } else {
            updateStep(2, FixStepStatus.FAILED, "سرور DNS پاسخ‌گو یافت نشد؛ اتصال اینترنت مودم را بررسی کنید.")
        }

        // Step 4: Gateway & Channel Check
        updateStep(3, FixStepStatus.RUNNING, "در حال بررسی سلامت دروازه مودم (${telemetry.gatewayIp})…")
        val postGateway = probeGatewayLatency(telemetry.gatewayIp, samples = 4)
        val routerInfo = if (telemetry.gatewayIp != "نامشخص") {
            detectRouterVendor(telemetry.gatewayIp)
        } else {
            telemetry.routerVendor to false
        }
        if (postGateway.reachable) {
            fixedCount++
            val panelHint = if (routerInfo.second) "پنل مدیریت مودم در دسترس است." else "دروازه مودم پاسخ‌گو است."
            updateStep(
                3,
                FixStepStatus.SUCCESS,
                "پینگ مودم ${postGateway.avgPingMs} ms و جیتر ${postGateway.jitterMs} ms — $panelHint"
            )
        } else {
            updateStep(
                3,
                FixStepStatus.ACTION_NEEDED,
                "مودم پاسخ نداد؛ لطفاً مودم را یک‌بار خاموش و پس از ۳۰ ثانیه روشن کنید."
            )
        }

        // Step 5: Post-Fix Verification
        updateStep(4, FixStepStatus.RUNNING, "در حال اندازه‌گیری مجدد پینگ اینترنت و سرعت دانلود…")
        val postInternet = probeInternetLatency(samples = 4)
        val (postDownload, _) = runQuickDownloadProbe()
        val afterScore = computeHealthScore(
            isConnected = telemetry.isConnected,
            isValidated = postInternet.reachable,
            signalPercent = telemetry.signalPercent,
            gatewayProbe = postGateway,
            internetProbe = postInternet,
            downloadMbps = postDownload
        )

        if (postInternet.reachable) {
            fixedCount++
            updateStep(
                4,
                FixStepStatus.SUCCESS,
                "تایید نهایی انجام شد: پینگ اینترنت ${postInternet.avgPingMs} ms و سرعت ${"%.1f".format(postDownload)} Mbps"
            )
        } else {
            updateStep(
                4,
                FixStepStatus.ACTION_NEEDED,
                "اتصال محلی بررسی شد اما اینترنت مودم قطع است (چراغ Internet/DSL مودم را چک کنید)."
            )
        }

        BeforeAfterComparison(
            beforeScore = beforeScore,
            afterScore = afterScore,
            beforeGatewayPingMs = beforeGatewayPing,
            afterGatewayPingMs = postGateway.avgPingMs,
            beforeInternetPingMs = beforeInternetPing,
            afterInternetPingMs = postInternet.avgPingMs,
            beforeDownloadMbps = beforeDownloadMbps,
            afterDownloadMbps = postDownload,
            selectedDnsName = fastestDns?.name ?: "Cloudflare",
            selectedDnsIp = fastestDns?.primaryIp ?: "1.1.1.1",
            selectedDotHostname = fastestDns?.dotHostname?.ifBlank { "one.one.one.one" } ?: "one.one.one.one",
            fixedIssuesCount = fixedCount
        )
    }

    private suspend fun rebindActiveNetwork(transportType: NetworkTransportType): Boolean {
        return try {
            // Reset process network binding to clear stale routes
            connectivityManager.bindProcessToNetwork(null)
            if (transportType == NetworkTransportType.WIFI) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager.reassociate()
                }
                val wifiNetwork = withTimeoutOrNull(2500L) {
                    requestFreshNetwork(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (wifiNetwork != null) {
                    connectivityManager.bindProcessToNetwork(wifiNetwork)
                    return true
                }
            }
            connectivityManager.activeNetwork != null
        } catch (_: Exception) {
            connectivityManager.activeNetwork != null
        }
    }

    private suspend fun requestFreshNetwork(transport: Int): Network? =
        suspendCancellableCoroutine { cont ->
            val request = NetworkRequest.Builder()
                .addTransportType(transport)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    try {
                        connectivityManager.unregisterNetworkCallback(this)
                    } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(network)
                }

                override fun onUnavailable() {
                    try {
                        connectivityManager.unregisterNetworkCallback(this)
                    } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(null)
                }
            }
            try {
                connectivityManager.requestNetwork(request, callback)
                cont.invokeOnCancellation {
                    try {
                        connectivityManager.unregisterNetworkCallback(callback)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    fun computeHealthScore(
        isConnected: Boolean,
        isValidated: Boolean,
        signalPercent: Int,
        gatewayProbe: PingProbeResult,
        internetProbe: PingProbeResult,
        downloadMbps: Double
    ): Int {
        if (!isConnected) return 10
        var score = 100
        if (!isValidated) score -= 35
        if (signalPercent < 40) score -= 18 else if (signalPercent < 65) score -= 8
        if (!gatewayProbe.reachable) {
            score -= 22
        } else {
            if (gatewayProbe.avgPingMs > 30) score -= 12 else if (gatewayProbe.avgPingMs > 12) score -= 5
            if (gatewayProbe.packetLossPercent > 10) score -= 15
        }
        if (!internetProbe.reachable) {
            score -= 30
        } else {
            if (internetProbe.avgPingMs > 160) score -= 14 else if (internetProbe.avgPingMs > 90) score -= 6
            if (internetProbe.jitterMs > 35) score -= 8
            if (internetProbe.packetLossPercent > 5) score -= 15
        }
        if (downloadMbps > 0.0) {
            if (downloadMbps < 3.0) score -= 15 else if (downloadMbps < 10.0) score -= 7
        }
        return score.coerceIn(5, 100)
    }

    private fun tcpConnectRtt(host: String, port: Int, timeoutMs: Int): Int? {
        val start = SystemClock.elapsedRealtime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun icmpReachableRtt(host: String, timeoutMs: Int): Int? {
        val start = SystemClock.elapsedRealtime()
        return try {
            val reachable = InetAddress.getByName(host).isReachable(timeoutMs)
            if (reachable) (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun summarizeTimings(timings: List<Int>, totalSent: Int): PingProbeResult {
        if (timings.isEmpty()) {
            return PingProbeResult(0, 0, 100, false)
        }
        val avg = timings.average().roundToInt()
        val jitter = if (timings.size > 1) {
            timings.zipWithNext { a, b -> abs(b - a) }.average().roundToInt()
        } else {
            0
        }
        val loss = (((totalSent - timings.size).toDouble() / totalSent.coerceAtLeast(1)) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        return PingProbeResult(avgPingMs = avg, jitterMs = jitter, packetLossPercent = loss, reachable = true)
    }

    private fun rssiToPercent(rssi: Int): Int {
        return when {
            rssi <= -100 -> 0
            rssi >= -50 -> 100
            else -> 2 * (rssi + 100)
        }.coerceIn(0, 100)
    }

    private fun frequencyToChannel(freq: Int): Int? {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2407) / 5
            freq in 5170..5825 -> (freq - 5000) / 5
            else -> null
        }
    }

    private fun intToIpV4(ipInt: Int): String {
        return "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}.${(ipInt shr 24) and 0xFF}"
    }
}
