package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.V2reyBndApplication
import com.example.data.BndTelegramLinks
import com.example.data.VpnRepository
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

    val nodes: StateFlow<List<ProxyNode>> = repository.nodes
    val selectedNode: StateFlow<ProxyNode?> = repository.selectedNode
    val bypassIran: StateFlow<Boolean> = repository.bypassIran
    val selectedDns: StateFlow<String> = repository.selectedDns
    val subscriptionUrl: StateFlow<String> = repository.subscriptionUrl

    val storePlans: List<StorePlan> = repository.getStorePlans()

    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _isTestingAllPings = MutableStateFlow(false)
    val isTestingAllPings: StateFlow<Boolean> = _isTestingAllPings.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun toggleConnection(context: Context, onPrepareNeeded: (Intent) -> Unit) {
        val currentState = vpnState.value
        if (currentState == VpnState.CONNECTED || currentState == VpnState.CONNECTING) {
            V2reyBndVpnService.stopVpn(context)
            viewModelScope.launch {
                _uiMessage.emit("اتصال قطع شد")
            }
        } else {
            val node = selectedNode.value ?: nodes.value.firstOrNull()
            if (node == null) {
                viewModelScope.launch {
                    _uiMessage.emit("هیچ سروری اضافه نشده است. لطفاً ابتدا کانفیگ یا لینک سابسکریپشن وارد کنید")
                }
                return
            }

            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                onPrepareNeeded(prepareIntent)
            } else {
                startVpnInternal(context, node)
            }
        }
    }

    fun onVpnPermissionGranted(context: Context) {
        val node = selectedNode.value ?: nodes.value.firstOrNull() ?: return
        startVpnInternal(context, node)
    }

    private fun startVpnInternal(context: Context, node: ProxyNode) {
        V2reyBndVpnService.startVpn(context, node.name, node.protocol.displayName)
        viewModelScope.launch {
            _uiMessage.emit("در حال اتصال به ${node.name}…")
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
                _uiMessage.emit("${parsed.size} کانفیگ با موفقیت اضافه شد")
            }
        } else {
            viewModelScope.launch {
                _uiMessage.emit("فرمت کانفیگ نامعتبر است")
            }
        }
        return parsed.size
    }

    fun importOpenVpnProfile(
        configText: String,
        profileName: String? = null,
        username: String? = null,
        password: String? = null
    ): ProxyNode {
        val node = ProxyConfigParser.parseOpenVpn(configText, profileName, username, password)
        repository.addNode(node)
        viewModelScope.launch {
            _uiMessage.emit("پروفایل OpenVPN (${node.name}) اضافه شد")
        }
        return node
    }

    fun importSubscriptionUrl(url: String) {
        if (url.isBlank()) return
        repository.setSubscriptionUrl(url)
        viewModelScope.launch {
            _uiMessage.emit("در حال دریافت کانفیگ‌های سابسکریپشن…")
            val nodes = ProxyConfigParser.fetchSubscription(url)
            if (nodes.isNotEmpty()) {
                repository.addNodes(nodes)
                _uiMessage.emit("${nodes.size} سرور با موفقیت از سابسکریپشن بارگذاری شد")
            } else {
                _uiMessage.emit("خطا در دریافت سرورها از لینک سابسکریپشن")
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

    fun openTelegramUrl(context: Context, fullUrl: String) {
        try {
            val domain = fullUrl.removePrefix("https://t.me/").removePrefix("@").trim()
            val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$domain"))
            telegramIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(telegramIntent)
        } catch (e: Exception) {
            // Fallback to web browser
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
