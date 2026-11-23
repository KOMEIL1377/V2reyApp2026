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
import com.example.model.NodeOrigin
import com.example.model.ProxyNode
import com.example.model.StorePlan
import com.example.model.SubscriptionStatus
import com.example.model.VpnState
import com.example.network.PanelSyncResult
import com.example.network.PingEngine
import com.example.network.SanaeiPanelClient
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
    private val repository: VpnRepository = V2reyBndApplication.instance.repository,
    private val panelClient: SanaeiPanelClient = SanaeiPanelClient()
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

    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedIn
    val loggedInEmail: StateFlow<String?> = repository.loggedInEmail
    val subscriptions: StateFlow<List<SubscriptionStatus>> = repository.subscriptions
    val activeSubscriptionId: StateFlow<String?> = repository.activeSubscriptionId
    val panelSubscriptionStatus: StateFlow<SubscriptionStatus?> = repository.panelSubscriptionStatus
    val manualSubscriptionStatus: StateFlow<SubscriptionStatus?> = repository.manualSubscriptionStatus
    val activeSubscriptionStatus: StateFlow<SubscriptionStatus?> = repository.activeSubscriptionStatus

    val storePlans: List<StorePlan> = repository.getStorePlans()

    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _isTestingAllPings = MutableStateFlow(false)
    val isTestingAllPings: StateFlow<Boolean> = _isTestingAllPings.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isRefreshingSubscription = MutableStateFlow(false)
    val isRefreshingSubscription: StateFlow<Boolean> = _isRefreshingSubscription.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    val xrayVersion: String by lazy { V2reyBndVpnService.queryXrayVersion() }

    init {
        // Silently sync active logged-in panel session on startup if credentials exist
        val savedEmail = repository.loggedInEmail.value
        val savedSubId = repository.loggedInSubId.value
        if (!savedEmail.isNullOrBlank() && !savedSubId.isNullOrBlank()) {
            viewModelScope.launch {
                val res = panelClient.authenticateAndFetchAccount(
                    email = savedEmail,
                    subId = savedSubId,
                    rejectIfDisabled = false
                )
                if (res is PanelSyncResult.Success) {
                    repository.savePanelSession(savedEmail, savedSubId, res.status, res.nodes)
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun isSubscriptionBlockedForNode(node: ProxyNode?): SubscriptionStatus? {
        val statusBySubId = node?.subscriptionId?.let { subId ->
            subscriptions.value.firstOrNull { it.id == subId }
        }
        val statusToCheck = statusBySubId ?: when (node?.origin) {
            NodeOrigin.PANEL_ACCOUNT -> panelSubscriptionStatus.value
            NodeOrigin.SUBSCRIPTION -> manualSubscriptionStatus.value
            NodeOrigin.MANUAL -> null
            null -> activeSubscriptionStatus.value
        }
        return statusToCheck?.takeIf { it.isBlocked() }
    }

    fun selectSubscription(subscriptionId: String) {
        repository.selectSubscription(subscriptionId)
    }

    fun deleteSubscription(context: Context, subscriptionId: String) {
        val currentState = vpnState.value
        val activeName = activeNodeName.value
        val outcome = repository.deleteSubscription(subscriptionId)
        val removed = outcome.removed ?: return

        val wasRunningDeletedSub = (currentState == VpnState.CONNECTED ||
            currentState == VpnState.CONNECTING ||
            currentState == VpnState.RECONNECTING) &&
            (outcome.affectedSelectedNode || (activeName != null && outcome.removedNodeNames.contains(activeName)))

        if (wasRunningDeletedSub) {
            V2reyBndVpnService.stopConnection(context)
            V2reyBndVpnService.clearError()
        }

        val label = removed.title.ifBlank {
            if (removed.email.isNotBlank()) removed.email else "اشتراک"
        }
        viewModelScope.launch {
            _uiMessage.emit("اشتراک «$label» و سرورهای آن حذف شد")
        }
    }

    fun loginWithCredentials(
        email: String,
        subId: String,
        onSuccess: () -> Unit = {}
    ) {
        if (_isLoggingIn.value) return
        val cleanEmail = email.trim()
        val cleanSubId = subId.trim()
        if (cleanEmail.isEmpty() || cleanSubId.isEmpty()) {
            _loginError.value = "نام کاربری (Email) و Subscription ID هر دو الزامی هستند"
            return
        }

        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value = null
            when (val res = panelClient.authenticateAndFetchAccount(cleanEmail, cleanSubId, rejectIfDisabled = true)) {
                is PanelSyncResult.Error -> {
                    _loginError.value = res.message
                }
                is PanelSyncResult.Success -> {
                    repository.savePanelSession(cleanEmail, cleanSubId, res.status, res.nodes)
                    if (res.status.isBlocked()) {
                        _uiMessage.emit("ورود انجام شد • وضعیت اشتراک: منقضی شده")
                    } else {
                        _uiMessage.emit("خوش آمدید $cleanEmail • ${res.nodes.size} سرور همگام‌سازی شد")
                    }
                    onSuccess()
                }
            }
            _isLoggingIn.value = false
        }
    }

    fun refreshSubscriptionAndServers(context: Context) {
        if (_isRefreshingSubscription.value) return
        val email = repository.loggedInEmail.value
        val subId = repository.loggedInSubId.value
        val manualSubs = subscriptions.value.filter {
            it.source == com.example.model.AccountSource.SUBSCRIPTION_URL && it.subscriptionUrl.isNotBlank()
        }
        val legacySubUrl = subscriptionUrl.value.trim()
        val hasManualUrls = manualSubs.isNotEmpty() || legacySubUrl.isNotBlank()

        if (email.isNullOrBlank() && !hasManualUrls) {
            viewModelScope.launch {
                _uiMessage.emit("هیچ حساب کاربری یا لینک اشتراکی برای بروزرسانی ثبت نشده است")
            }
            return
        }

        viewModelScope.launch {
            _isRefreshingSubscription.value = true
            val messages = mutableListOf<String>()

            // 1. Refresh authenticated 3X-UI panel account if logged in
            if (!email.isNullOrBlank() && !subId.isNullOrBlank()) {
                when (val res = panelClient.authenticateAndFetchAccount(email, subId, rejectIfDisabled = false)) {
                    is PanelSyncResult.Success -> {
                        repository.savePanelSession(email, subId, res.status, res.nodes)
                        if (res.status.isBlocked()) {
                            disconnectIfNodeBlocked(context, NodeOrigin.PANEL_ACCOUNT, panelSubscriptionStatus.value?.id)
                            messages.add("اشتراک حساب $email منقضی شده است")
                        } else {
                            messages.add("حساب $email بروزرسانی شد (${res.nodes.size} سرور)")
                        }
                    }
                    is PanelSyncResult.Error -> {
                        messages.add("بروزرسانی حساب: ${res.message}")
                    }
                }
            }

            // 2. Refresh all added subscription URLs independently without overwriting each other
            val targets = if (manualSubs.isNotEmpty()) {
                manualSubs
            } else if (legacySubUrl.isNotBlank()) {
                listOf(SubscriptionStatus(source = com.example.model.AccountSource.SUBSCRIPTION_URL, subscriptionUrl = legacySubUrl))
            } else {
                emptyList()
            }

            val currentActiveSubId = activeSubscriptionId.value
            for (sub in targets) {
                val url = sub.subscriptionUrl.trim()
                if (url.isBlank()) continue
                val subResult = ProxyConfigParser.fetchSubscriptionWithInfo(url)
                if (subResult.nodes.isNotEmpty()) {
                    val updated = repository.upsertManualSubscription(
                        url = url,
                        status = subResult.status ?: sub,
                        syncedNodes = subResult.nodes,
                        targetSubscriptionId = sub.id.takeIf { manualSubs.isNotEmpty() },
                        makeActive = (sub.id == currentActiveSubId)
                    )
                    if (updated.isBlocked()) {
                        disconnectIfNodeBlocked(context, NodeOrigin.SUBSCRIPTION, updated.id)
                    }
                    messages.add("${updated.title}: ${subResult.nodes.size} سرور")
                } else if (subResult.status != null) {
                    repository.updateManualSubscriptionStatus(subResult.status.copy(id = sub.id))
                    if (subResult.status.isBlocked()) {
                        disconnectIfNodeBlocked(context, NodeOrigin.SUBSCRIPTION, sub.id)
                    }
                } else if (!subResult.errorMessage.isNullOrBlank()) {
                    messages.add("${sub.title.ifBlank { "اشتراک" }}: ${subResult.errorMessage}")
                }
            }

            _isRefreshingSubscription.value = false
            if (messages.isNotEmpty()) {
                _uiMessage.emit(messages.joinToString(" | "))
            }
        }
    }

    private fun disconnectIfNodeBlocked(
        context: Context,
        blockedOrigin: NodeOrigin,
        blockedSubId: String? = null
    ) {
        val state = vpnState.value
        val activeSelected = selectedNode.value
        val matchesSub = when {
            blockedSubId != null && activeSelected?.subscriptionId != null ->
                activeSelected.subscriptionId == blockedSubId
            else -> activeSelected?.origin == blockedOrigin
        }
        val shouldDisconnect = (state == VpnState.CONNECTED || state == VpnState.CONNECTING || state == VpnState.RECONNECTING) && matchesSub
        if (shouldDisconnect) {
            V2reyBndVpnService.stopConnection(context)
            V2reyBndVpnService.reportError("منقضی شده: حجم یا تاریخ اشتراک به پایان رسیده یا کاربر غیرفعال شده است")
        }
    }

    fun logout(context: Context) {
        val currentState = vpnState.value
        if (currentState == VpnState.CONNECTED || currentState == VpnState.CONNECTING || currentState == VpnState.RECONNECTING) {
            V2reyBndVpnService.stopConnection(context)
        }
        V2reyBndVpnService.clearError()
        _loginError.value = null
        repository.clearPanelAccountSession()
        viewModelScope.launch {
            _uiMessage.emit("از حساب کاربری خارج شدید (کانفیگ‌های دستی حفظ شدند)")
        }
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
                _uiMessage.emit("هیچ سروری اضافه نشده است. لطفاً ابتدا وارد شوید یا کانفیگ/سابسکریپشن وارد کنید")
            }
            return
        }

        val blockedStatus = isSubscriptionBlockedForNode(node)
        if (blockedStatus != null) {
            val err = "منقضی شده: اشتراک '${blockedStatus.title}' به پایان رسیده، حجم آن تمام شده یا غیرفعال است"
            V2reyBndVpnService.reportError(err)
            viewModelScope.launch {
                _uiMessage.emit("منقضی شده • امکان اتصال وجود ندارد")
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
        val blockedStatus = isSubscriptionBlockedForNode(node)
        if (blockedStatus != null) {
            val err = "منقضی شده: اشتراک '${blockedStatus.title}' قابل اتصال نیست"
            V2reyBndVpnService.reportError(err)
            viewModelScope.launch {
                _uiMessage.emit("منقضی شده • امکان اتصال وجود ندارد")
            }
            return
        }
        startXrayCoreInternal(context, node)
    }

    private fun startXrayCoreInternal(context: Context, node: ProxyNode) {
        V2reyBndVpnService.clearError()

        val mode = connectionMode.value
        val sPort = socksPort.value
        val hPort = httpPort.value
        val dns = selectedDns.value

        val configFile = try {
            val assets = UniversalCoreConfigGenerator.prepareAssetsDir(context)
            val generatedJson = UniversalCoreConfigGenerator.generateXrayConfig(
                node = node,
                connectionMode = mode,
                socksPort = sPort,
                httpPort = hPort,
                bypassIran = bypassIran.value,
                dnsServer = dns,
                enableTlsFragment = enableTlsFragment.value,
                enableFakeDns = enableFakeDns.value
            )
            val runtimeJson = UniversalCoreConfigGenerator.sanitizeRuntimeGeodata(
                configJson = generatedJson,
                hasGeosite = assets.hasGeosite,
                hasGeoip = assets.hasGeoip
            )
            val file = java.io.File(context.filesDir, V2reyBndVpnService.CONFIG_FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(runtimeJson, Charsets.UTF_8)
            check(file.exists() && file.length() > 0L) {
                "فایل پیکربندی ایجاد نشد یا خالی است: ${file.absolutePath}"
            }
            file to runtimeJson
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
        val result = ProxyConfigParser.parseBulkTextWithValidation(rawText, NodeOrigin.MANUAL)
        if (result.nodes.isNotEmpty()) {
            repository.addNodes(result.nodes)
            val jsonCount = result.nodes.count { it.protocol == com.example.model.ProxyProtocol.CUSTOM_JSON }
            val msg = if (jsonCount > 0 && jsonCount == result.nodes.size) {
                "${result.nodes.size} کانفیگ Xray JSON با موفقیت اعتبارسنجی و اضافه شد"
            } else {
                "${result.nodes.size} کانفیگ Xray با موفقیت اضافه شد"
            }
            viewModelScope.launch {
                _uiMessage.emit(msg)
            }
        } else {
            viewModelScope.launch {
                _uiMessage.emit(result.errorMessage ?: "فرمت کانفیگ پشتیبانی‌شده Xray یافت نشد")
            }
        }
        return result.nodes.size
    }

    fun importSubscriptionUrl(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return
        repository.setSubscriptionUrl(trimmedUrl)
        viewModelScope.launch {
            _isRefreshingSubscription.value = true
            _uiMessage.emit("در حال دریافت کانفیگ‌های سابسکریپشن…")
            val result = ProxyConfigParser.fetchSubscriptionWithInfo(trimmedUrl)
            if (result.nodes.isNotEmpty()) {
                val saved = repository.upsertManualSubscription(
                    url = trimmedUrl,
                    status = result.status,
                    syncedNodes = result.nodes,
                    makeActive = true
                )
                _uiMessage.emit("${result.nodes.size} سرور Xray از اشتراک «${saved.title}» بارگذاری شد")
            } else {
                _uiMessage.emit(result.errorMessage ?: "خطا در دریافت یا پارس سرورها از لینک سابسکریپشن")
            }
            _isRefreshingSubscription.value = false
        }
    }

    fun importScannedQrContent(rawQrText: String): ProxyConfigParser.QrImportType {
        val classified = ProxyConfigParser.classifyQrPayload(rawQrText)
        when (classified.type) {
            ProxyConfigParser.QrImportType.SUBSCRIPTION_URL -> {
                importSubscriptionUrl(classified.normalizedPayload)
            }
            ProxyConfigParser.QrImportType.CONFIG -> {
                repository.addNodes(classified.nodes)
                viewModelScope.launch {
                    _uiMessage.emit("${classified.nodes.size} کانفیگ Xray از QR کد اضافه شد")
                }
            }
            ProxyConfigParser.QrImportType.INVALID -> {
                viewModelScope.launch {
                    _uiMessage.emit(classified.errorMessage ?: "فرمت QR کد معتبر نیست")
                }
            }
        }
        return classified.type
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
