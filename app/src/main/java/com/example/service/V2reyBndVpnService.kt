package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.VpnRepository
import com.example.model.ConnectionMode
import com.example.model.NetworkTrafficStats
import com.example.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libXray.DialerController
import libXray.LibXray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class V2reyBndVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectJob: Job? = null
    private var telemetryJob: Job? = null
    private var startTimeMs: Long = 0L

    private var vpnInterface: ParcelFileDescriptor? = null

    private var currentNodeName: String = "V2reyBND"
    private var currentConnectionMode: ConnectionMode = ConnectionMode.VPN
    private var currentDnsServer: String = "1.1.1.1"

    companion object {
        const val ACTION_CONNECT = "com.example.v2reybnd.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.v2reybnd.ACTION_DISCONNECT"
        const val EXTRA_NODE_NAME = "EXTRA_NODE_NAME"
        const val EXTRA_NODE_PROTOCOL = "EXTRA_NODE_PROTOCOL"
        const val EXTRA_CONFIG_JSON = "EXTRA_CONFIG_JSON"
        const val EXTRA_CONFIG_PATH = "EXTRA_CONFIG_PATH"
        const val EXTRA_CONNECTION_MODE = "EXTRA_CONNECTION_MODE"
        const val EXTRA_DNS_SERVER = "EXTRA_DNS_SERVER"
        const val EXTRA_SOCKS_PORT = "EXTRA_SOCKS_PORT"
        const val EXTRA_HTTP_PORT = "EXTRA_HTTP_PORT"

        const val CONFIG_FILE_NAME = "config.json"
        const val NOTIFICATION_ID = 7891
        const val CHANNEL_ID = "v2reybnd_vpn_channel"

        private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
        val vpnState = _vpnState.asStateFlow()

        private val _currentActiveNodeName = MutableStateFlow<String?>(null)
        val currentActiveNodeName = _currentActiveNodeName.asStateFlow()

        private val _activeConnectionMode = MutableStateFlow(ConnectionMode.VPN)
        val activeConnectionMode = _activeConnectionMode.asStateFlow()

        private val _activeSocksPort = MutableStateFlow(VpnRepository.DEFAULT_SOCKS_PORT)
        val activeSocksPort = _activeSocksPort.asStateFlow()

        private val _activeHttpPort = MutableStateFlow(VpnRepository.DEFAULT_HTTP_PORT)
        val activeHttpPort = _activeHttpPort.asStateFlow()

        private val _trafficStats = MutableStateFlow(NetworkTrafficStats())
        val trafficStats = _trafficStats.asStateFlow()

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError = _lastError.asStateFlow()

        private val _coreLogs = MutableStateFlow<List<String>>(emptyList())
        val coreLogs = _coreLogs.asStateFlow()

        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        fun appendLog(message: String) {
            val line = "[${timeFormat.format(Date())}] $message"
            _coreLogs.value = (_coreLogs.value + line).takeLast(250)
        }

        fun clearLogs() {
            _coreLogs.value = emptyList()
        }

        fun clearError() {
            _lastError.value = null
        }

        fun reportError(message: String) {
            appendLog("ERROR: $message")
            _lastError.value = message
            _vpnState.value = VpnState.FAILED
        }

        fun queryXrayVersion(): String {
            return try {
                val res = parseCallResponse(LibXray.xrayVersion())
                if (res.success && !res.data.isNullOrBlank()) res.data else "Xray-core v26"
            } catch (_: Throwable) {
                "Xray-core v26"
            }
        }

        fun startConnection(
            context: Context,
            nodeName: String,
            protocolName: String,
            configJson: String,
            configPath: String,
            connectionMode: ConnectionMode,
            dnsServer: String,
            socksPort: Int,
            httpPort: Int
        ) {
            val intent = Intent(context, V2reyBndVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_NODE_NAME, nodeName)
                putExtra(EXTRA_NODE_PROTOCOL, protocolName)
                putExtra(EXTRA_CONFIG_JSON, configJson)
                putExtra(EXTRA_CONFIG_PATH, configPath)
                putExtra(EXTRA_CONNECTION_MODE, connectionMode.name)
                putExtra(EXTRA_DNS_SERVER, dnsServer)
                putExtra(EXTRA_SOCKS_PORT, socksPort)
                putExtra(EXTRA_HTTP_PORT, httpPort)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopConnection(context: Context) {
            val intent = Intent(context, V2reyBndVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }

        private data class XrayCallResult(
            val success: Boolean,
            val data: String?,
            val error: String?
        )

        private fun parseCallResponse(base64Resp: String?): XrayCallResult {
            if (base64Resp.isNullOrBlank()) {
                return XrayCallResult(false, null, "No response received from LibXray")
            }
            return try {
                val decoded = String(Base64.decode(base64Resp, Base64.DEFAULT), Charsets.UTF_8)
                val obj = JSONObject(decoded)
                XrayCallResult(
                    success = obj.optBoolean("success", false),
                    data = obj.opt("data")?.toString(),
                    error = obj.optString("error").takeIf { it.isNotBlank() && it != "null" }
                )
            } catch (e: Exception) {
                XrayCallResult(false, null, "Failed to decode LibXray response: ${e.message}")
            }
        }
    }

    private val dialerController = DialerController { fd ->
        if (currentConnectionMode == ConnectionMode.VPN) {
            val protected = protect(fd.toInt())
            if (!protected) {
                appendLog("WARNING: VpnService.protect(fd=$fd) returned false")
            }
            protected
        } else {
            true
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val nodeName = intent.getStringExtra(EXTRA_NODE_NAME) ?: "V2reyBND Node"
                val protocol = intent.getStringExtra(EXTRA_NODE_PROTOCOL) ?: "VLESS"
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON).orEmpty()
                val configPath = intent.getStringExtra(EXTRA_CONFIG_PATH).orEmpty()
                val mode = ConnectionMode.fromName(intent.getStringExtra(EXTRA_CONNECTION_MODE))
                val dnsServer = intent.getStringExtra(EXTRA_DNS_SERVER) ?: "1.1.1.1"
                val socksPort = intent.getIntExtra(EXTRA_SOCKS_PORT, VpnRepository.DEFAULT_SOCKS_PORT)
                val httpPort = intent.getIntExtra(EXTRA_HTTP_PORT, VpnRepository.DEFAULT_HTTP_PORT)

                connectXray(
                    nodeName = nodeName,
                    protocol = protocol,
                    configJson = configJson,
                    providedConfigPath = configPath,
                    mode = mode,
                    dnsServer = dnsServer,
                    socksPort = socksPort,
                    httpPort = httpPort
                )
            }
            ACTION_DISCONNECT -> {
                disconnectCore(reason = null, markFailed = false)
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        disconnectCore(reason = "مجوز VPN توسط سیستم یا برنامه دیگر لغو شد", markFailed = true)
        super.onRevoke()
    }

    private fun connectXray(
        nodeName: String,
        protocol: String,
        configJson: String,
        providedConfigPath: String,
        mode: ConnectionMode,
        dnsServer: String,
        socksPort: Int,
        httpPort: Int
    ) {
        currentNodeName = nodeName
        currentConnectionMode = mode
        currentDnsServer = dnsServer

        _lastError.value = null
        _vpnState.value = VpnState.CONNECTING
        _currentActiveNodeName.value = nodeName
        _activeConnectionMode.value = mode
        _activeSocksPort.value = socksPort
        _activeHttpPort.value = httpPort

        val notification = buildNotification(
            "در حال اتصال به $nodeName…",
            "${mode.shortLabel} • Xray $protocol"
        )
        startForeground(NOTIFICATION_ID, notification)

        connectJob?.cancel()
        connectJob = serviceScope.launch {
            try {
                cleanupCoreResources()

                // 1. Write & verify config.json on disk before starting Xray-core
                val configFile = if (providedConfigPath.isNotBlank()) {
                    File(providedConfigPath)
                } else {
                    File(filesDir, CONFIG_FILE_NAME)
                }

                if (configJson.isNotBlank()) {
                    configFile.parentFile?.mkdirs()
                    configFile.writeText(configJson, Charsets.UTF_8)
                }

                if (!configFile.exists() || configFile.length() <= 0L) {
                    throw IllegalStateException(
                        "Xray config file missing or empty: ${configFile.absolutePath}"
                    )
                }

                val datDir = File(filesDir, "xray_assets").apply { mkdirs() }.absolutePath
                appendLog("Initializing Xray (${queryXrayVersion()}) | Mode: ${mode.shortLabel} | Node: $nodeName ($protocol)")
                appendLog("Config file verified: ${configFile.absolutePath} (${configFile.length()} bytes)")
                appendLog("Local Ports -> SOCKS5: 127.0.0.1:$socksPort | HTTP: 127.0.0.1:$httpPort | DNS: $dnsServer")

                // 2. Register socket protector so Xray outbound dials bypass TUN
                LibXray.registerDialerController(dialerController)
                LibXray.registerListenerController(dialerController)

                // 3. In VPN Mode, open TUN interface first and pass fd via LibXray.setTunFd
                if (mode == ConnectionMode.VPN) {
                    if (prepare(this@V2reyBndVpnService) != null) {
                        throw IllegalStateException("مجوز سیستم VPN صادر نشده است (VpnService.prepare)")
                    }
                    val pfd = openAndroidTun(nodeName = nodeName, dnsServer = dnsServer)
                    vpnInterface = pfd
                    LibXray.setTunFd(pfd.fd)
                    appendLog("TUN established (fd=${pfd.fd}, IP=172.19.0.1/30, MTU=1500, route=0.0.0.0/0)")
                } else {
                    LibXray.setTunFd(-1)
                    appendLog("Proxy Mode active (System TUN disabled; listening on local SOCKS/HTTP ports)")
                }

                // 4. Pass configFile.absolutePath via RunXrayRequest (NOT RunXrayFromJSONRequest)
                val requestBase64 = LibXray.newXrayRunRequest(datDir, configFile.absolutePath)
                val runResult = parseCallResponse(LibXray.runXray(requestBase64))
                if (!runResult.success) {
                    val exactErr = runResult.error ?: "Xray failed to start (no error detail returned)"
                    throw IllegalStateException(exactErr)
                }

                // 5. Verify Xray instance stays alive and is genuinely listening on local SOCKS/HTTP ports
                val socksListening = waitForLocalPortListening(socksPort, timeoutMs = 2500L)
                val httpListening = waitForLocalPortListening(httpPort, timeoutMs = 1500L)
                val xrayRunning = LibXray.getXrayState()

                if (!xrayRunning || !socksListening || !httpListening) {
                    throw IllegalStateException(
                        "Xray did not bind local listeners (state=$xrayRunning, SOCKS:$socksPort=$socksListening, HTTP:$httpPort=$httpListening)"
                    )
                }

                startTimeMs = System.currentTimeMillis()
                _vpnState.value = VpnState.CONNECTED
                appendLog("Xray-core active and listening on 127.0.0.1:$socksPort (SOCKS) & 127.0.0.1:$httpPort (HTTP)")

                val statusDetails = if (mode == ConnectionMode.VPN) {
                    "تونل Xray فعال • SOCKS :$socksPort / HTTP :$httpPort"
                } else {
                    "پراکسی Xray فعال • 127.0.0.1:$socksPort (SOCKS) • :$httpPort (HTTP)"
                }
                updateNotification("متصل به $nodeName", statusDetails)
                startTrafficTelemetryLoop(socksPort)
            } catch (e: Throwable) {
                val errorMessage = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
                appendLog("ERROR: $errorMessage")
                disconnectCore(reason = errorMessage, markFailed = true)
            }
        }
    }

    private suspend fun waitForLocalPortListening(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!LibXray.getXrayState()) {
                return false
            }
            val open = try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress("127.0.0.1", port), 250)
                }
                true
            } catch (_: Exception) {
                false
            }
            if (open) return true
            delay(100)
        }
        return false
    }

    private fun openAndroidTun(nodeName: String, dnsServer: String): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("V2reyBND - $nodeName")
            .setMtu(1500)
            .addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(dnsServer.ifBlank { "1.1.1.1" })

        // Critical loop prevention: V2reyBND's own outbound sockets bypass TUN
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            appendLog("addDisallowedApplication warning: ${e.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        return builder.establish()
            ?: throw IllegalStateException("ایجاد رابط TUN اندروید ناموفق بود (Builder.establish returned null)")
    }

    private fun startTrafficTelemetryLoop(socksPort: Int) {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            val uid = Process.myUid()
            val initialRx = TrafficStats.getUidRxBytes(uid).let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
            val initialTx = TrafficStats.getUidTxBytes(uid).let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
            var prevRx = initialRx
            var prevTx = initialTx

            while (isActive && _vpnState.value == VpnState.CONNECTED) {
                delay(1000)

                if (!LibXray.getXrayState()) {
                    appendLog("ERROR: Xray-core process exited unexpectedly (port $socksPort closed)")
                    disconnectCore(reason = "هسته Xray به طور غیرمنتظره متوقف شد", markFailed = true)
                    break
                }

                val currentRx = TrafficStats.getUidRxBytes(uid).let { if (it == TrafficStats.UNSUPPORTED.toLong()) prevRx else it }
                val currentTx = TrafficStats.getUidTxBytes(uid).let { if (it == TrafficStats.UNSUPPORTED.toLong()) prevTx else it }

                val rxSpeed = (currentRx - prevRx).coerceAtLeast(0L)
                val txSpeed = (currentTx - prevTx).coerceAtLeast(0L)
                prevRx = currentRx
                prevTx = currentTx

                val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000).coerceAtLeast(0L)
                _trafficStats.value = NetworkTrafficStats(
                    downloadSpeedBytes = rxSpeed,
                    uploadSpeedBytes = txSpeed,
                    totalDownloadBytes = (currentRx - initialRx).coerceAtLeast(0L),
                    totalUploadBytes = (currentTx - initialTx).coerceAtLeast(0L),
                    connectedDurationSeconds = durationSec
                )
            }
        }
    }

    private fun cleanupCoreResources() {
        telemetryJob?.cancel()
        telemetryJob = null

        try {
            if (LibXray.getXrayState()) {
                val stopRes = parseCallResponse(LibXray.stopXray())
                if (!stopRes.success && !stopRes.error.isNullOrBlank()) {
                    appendLog("stopXray notice: ${stopRes.error}")
                }
            }
        } catch (_: Throwable) {
        }

        try {
            LibXray.setTunFd(-1)
        } catch (_: Throwable) {
        }

        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
    }

    private fun disconnectCore(reason: String?, markFailed: Boolean) {
        if (reason == null && !markFailed) {
            connectJob?.cancel()
            connectJob = null
        }
        cleanupCoreResources()

        if (markFailed) {
            _lastError.value = reason ?: "خطای نامشخص در هسته Xray"
            _vpnState.value = VpnState.FAILED
        } else {
            _vpnState.value = VpnState.DISCONNECTED
            appendLog("Xray-core disconnected cleanly")
        }

        _currentActiveNodeName.value = null
        _trafficStats.value = NetworkTrafficStats()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cleanupCoreResources()
        if (_vpnState.value == VpnState.CONNECTED || _vpnState.value == VpnState.CONNECTING) {
            _vpnState.value = VpnState.DISCONNECTED
            _currentActiveNodeName.value = null
            _trafficStats.value = NetworkTrafficStats()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سرویس اتصال V2reyBND (Xray)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت اتصال و ترافیک هسته Xray در V2reyBND"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, V2reyBndVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "قطع اتصال", disconnectPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }
}
