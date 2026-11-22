package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.V2reyBndApplication
import com.example.core.UniversalCoreConfigGenerator
import com.example.data.VpnRepository
import com.example.model.ConnectionMode
import com.example.model.NetworkTrafficStats
import com.example.model.ProxyNode
import com.example.model.StorePlan
import com.example.model.VpnState
import com.example.network.PingEngine
import com.example.parser.ProxyConfigParser
import com.example.service.V2reyBndVpnService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val iconRes: String) {
    DASHBOARD("اتصال", "dashboard"),
    SERVERS("سرورها", "servers"),
    STORE("خرید اشتراک", "store"),
    SUPPORT("پشتیبانی", "support"),
    SETTINGS("تنظیمات", "settings")
}

class MainViewModel(
    private val repository: VpnRepository = V2reyBndApplication.instance.repository
) : ViewModel() {

    val vpnState: StateFlow<VpnState> = V2reyBndVpnService.vpnState
    val trafficStats: StateFlow<NetworkTrafficStats> = V2reyBndVpnService.trafficStats
    val activeNodeName: StateFlow<String?> = V2reyBndVpnService.currentActiveNodeName
    val activeConnectionMode: StateFlow<ConnectionMode> = V2reyBndVpnService.activeConnectionMode
    val activeSocksPort: StateFlow<Int> = V2reyBndVpnService.activeSocksPort
    val activeHttpPort: StateFlow<Int> = V2reyBndVpnService.activeHttpPort
    val lastError: StateFlow<String?> = V2reyBndVpnService.lastError
    val coreLogs: StateFlow<List<String>> = V2reyBndVpnService.coreLogs

    val nodes: StateFlow<List<ProxyNode>> = repository.nodes
    val selectedNode: StateFlow<ProxyNode?> = repository.selectedNode
    val bypassIran: StateFlow<Boolean> = repository.bypassIran
    val selectedDns: StateFlow<String> = repository.selectedDns
    val connectionMode: StateFlow<ConnectionMode> = repository.connectionMode
    val socksPort: StateFlow<Int> = repository.socksPort
    val httpPort: StateFlow<Int> = repository.httpPort
    val enableTlsFragment: StateFlow<Boolean> = repository.enableTlsFragment
    val enableFakeDns: StateFlow<Boolean> = repository.enableFakeDns
    val subscriptionUrl: StateFlow<String> = repository.subscriptionUrl

    val storePlans: List<StorePlan> = repository.getStorePlans()

    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _isTestingAllPings = MutableStateFlow(false)
    val isTestingAllPings: StateFlow<Boolean> = _isTestingAllPings.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    val xrayVersion: String by lazy { V2reyBndVpnService.queryXrayVersion() }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun toggleConnection(context: Context, onPrepareNeeded: (Intent) -> Unit) {
        val currentState = vpnState.value
        if (currentState == VpnState.CONNECTED || currentState == VpnState.CONNECTING) {
            V2reyBndVpnService.stopConnection(context)
            viewModelScope.launch {
                _uiMessage.emit("اتصال قطع شد")
            }
            return
        }

        val node = selectedNode.value ?: nodes.value.firstOrNull()
        if (node == null) {
            viewModelScope.launch {
                _uiMessage.emit("هیچ سروری اضافه نشده است. لطفاً ابتدا کانفیگ یا سابسکریپشن Xray وارد کنید")
            }
            return
        }

        val mode = connectionMode.value
        if (mode == ConnectionMode.VPN) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                onPrepareNeeded(prepareIntent)
                return
            }
        }

        startXrayCoreInternal(context, node)
    }

    fun onVpnPermissionGranted(context: Context) {
        val node = selectedNode.value ?: nodes.value.firstOrNull() ?: return
        startXrayCoreInternal(context, node)
    }

    private fun startXrayCoreInternal(context: Context, node: ProxyNode) {
        V2reyBndVpnService.clearError()

        val mode = connectionMode.value
        val sPort = socksPort.value
        val hPort = httpPort.value
        val dns = selectedDns.value

        val configFile = try {
            val configJson = UniversalCoreConfigGenerator.generateXrayConfig(
                node = node,
                connectionMode = mode,
                socksPort = sPort,
                httpPort = hPort,
                bypassIran = bypassIran.value,
                dnsServer = dns,
                enableTlsFragment = enableTlsFragment.value,
                enableFakeDns = enableFakeDns.value
            )
            val file = java.io.File(context.filesDir, V2reyBndVpnService.CONFIG_FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(configJson, Charsets.UTF_8)
            check(file.exists() && file.length() > 0L) {
                "فایل پیکربندی ایجاد نشد یا خالی است: ${file.absolutePath}"
            }
            file to configJson
        } catch (e: Exception) {
            val err = "خطا در آماده‌سازی config.json: ${e.message}"
            V2reyBndVpnService.reportError(err)
            viewModelScope.launch {
                _uiMessage.emit(err)
            }
            return
        }

        val (file, configJson) = configFile
        V2reyBndVpnService.startConnection(
            context = context,
            nodeName = node.name,
            protocolName = node.protocol.displayName,
            configJson = configJson,
            configPath = file.absolutePath,
            connectionMode = mode,
            dnsServer = dns,
            socksPort = sPort,
            httpPort = hPort
        )

        viewModelScope.launch {
            _uiMessage.emit("در حال راه‌اندازی Xray (${mode.shortLabel}) برای ${node.name}…")
        }
    }

    fun selectNode(node: ProxyNode) {
        repository.selectNode(node)
    }

    fun testSinglePing(node: ProxyNode) {
        viewModelScope.launch {
            repository.setNodeTesting(node.id, true)
            val ping = PingEngine.testLatency(node)
            repository.updateNodePing(node.id, ping)
        }
    }

    fun testAllPings() {
        if (_isTestingAllPings.value || nodes.value.isEmpty()) return
        viewModelScope.launch {
            _isTestingAllPings.value = true
            PingEngine.testAll(nodes.value) { nodeId, ping ->
                repository.updateNodePing(nodeId, ping)
            }
            _isTestingAllPings.value = false
            _uiMessage.emit("تست پینگ سرورها کامل شد")
        }
    }

    fun sortNodesByPing() {
        repository.sortNodesByPing()
        viewModelScope.launch {
            _uiMessage.emit("سرورها بر اساس پینگ مرتب شدند")
        }
    }

    fun importConfigText(rawText: String): Int {
        val parsed = ProxyConfigParser.parseBulkText(rawText)
        if (parsed.isNotEmpty()) {
            repository.addNodes(parsed)
            viewModelScope.launch {
                _uiMessage.emit("${parsed.size} کانفیگ Xray با موفقیت اضافه شد")
            }
        } else {
            viewModelScope.launch {
                _uiMessage.emit("فرمت کانفیگ پشتیبانی‌شده Xray یافت نشد")
            }
        }
        return parsed.size
    }

    fun importSubscriptionUrl(url: String) {
        if (url.isBlank()) return
        repository.setSubscriptionUrl(url)
        viewModelScope.launch {
            _uiMessage.emit("در حال دریافت کانفیگ‌های سابسکریپشن…")
            val nodes = ProxyConfigParser.fetchSubscription(url)
            if (nodes.isNotEmpty()) {
                repository.addNodes(nodes)
                _uiMessage.emit("${nodes.size} سرور Xray از سابسکریپشن بارگذاری شد")
            } else {
                _uiMessage.emit("خطا در دریافت یا پارس سرورها از لینک سابسکریپشن")
            }
        }
    }

    fun deleteNode(id: String) {
        repository.deleteNode(id)
    }

    fun clearAllNodes() {
        repository.clearAllNodes()
        viewModelScope.launch {
            _uiMessage.emit("لیست سرورها پاک شد")
        }
    }

    fun setBypassIran(bypass: Boolean) {
        repository.setBypassIran(bypass)
    }

    fun setSelectedDns(dns: String) {
        repository.setSelectedDns(dns)
    }

    fun setConnectionMode(mode: ConnectionMode) {
        repository.setConnectionMode(mode)
        viewModelScope.launch {
            _uiMessage.emit("حالت اتصال به ${mode.displayName} تغییر یافت")
        }
    }

    fun savePorts(socksInput: String, httpInput: String): Boolean {
        val sPort = socksInput.trim().toIntOrNull()
        val hPort = httpInput.trim().toIntOrNull()
        if (sPort == null || hPort == null || sPort !in 1024..65535 || hPort !in 1024..65535) {
            viewModelScope.launch {
                _uiMessage.emit("پورت باید عددی بین ۱۰۲۴ تا ۶۵۵۳۵ باشد")
            }
            return false
        }
        if (sPort == hPort) {
            viewModelScope.launch {
                _uiMessage.emit("پورت SOCKS و HTTP نمی‌توانند یکسان باشند")
            }
            return false
        }
        repository.setPorts(sPort, hPort)
        viewModelScope.launch {
            _uiMessage.emit("پورت‌های محلی ذخیره شد (SOCKS:$sPort • HTTP:$hPort)")
        }
        return true
    }

    fun resetPortsToDefault() {
        repository.resetPortsToDefault()
        viewModelScope.launch {
            _uiMessage.emit("پورت‌ها به پیش‌فرض (${VpnRepository.DEFAULT_SOCKS_PORT} / ${VpnRepository.DEFAULT_HTTP_PORT}) بازنشانی شد")
        }
    }

    fun setEnableTlsFragment(enabled: Boolean) {
        repository.setEnableTlsFragment(enabled)
    }

    fun setEnableFakeDns(enabled: Boolean) {
        repository.setEnableFakeDns(enabled)
    }

    fun clearCoreLogs() {
        V2reyBndVpnService.clearLogs()
    }

    fun clearError() {
        V2reyBndVpnService.clearError()
    }

    fun openTelegramUrl(context: Context, fullUrl: String) {
        try {
            val domain = fullUrl.removePrefix("https://t.me/").removePrefix("@").trim()
            val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$domain"))
            telegramIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(telegramIntent)
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}
