package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AccountSource
import com.example.model.ConnectionMode
import com.example.model.NodeOrigin
import com.example.model.ProxyNode
import com.example.model.ProxyProtocol
import com.example.model.StorePlan
import com.example.model.SubscriptionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object BndTelegramLinks {
    const val SUPPORT_ADMIN = "https://t.me/kingV2rey"
    const val SUPPORT_ADMIN_HANDLE = "@kingV2rey"

    const val OFFICIAL_CHANNEL = "https://t.me/vpn8745"
    const val OFFICIAL_CHANNEL_HANDLE = "@vpn8745"

    const val STORE_BOT = "https://t.me/bndteam_bot"
    const val STORE_BOT_HANDLE = "@bndteam_bot"
}

class VpnRepository(context: Context) {

    companion object {
        const val DEFAULT_SOCKS_PORT = 10808
        const val DEFAULT_HTTP_PORT = 10809
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("v2reybnd_prefs", Context.MODE_PRIVATE)

    private val _nodes = MutableStateFlow<List<ProxyNode>>(emptyList())
    val nodes = _nodes.asStateFlow()

    private val _selectedNode = MutableStateFlow<ProxyNode?>(null)
    val selectedNode = _selectedNode.asStateFlow()

    private val _bypassIran = MutableStateFlow(prefs.getBoolean("pref_bypass_iran", true))
    val bypassIran = _bypassIran.asStateFlow()

    private val _selectedDns = MutableStateFlow(prefs.getString("pref_dns", "1.1.1.1") ?: "1.1.1.1")
    val selectedDns = _selectedDns.asStateFlow()

    private val _connectionMode = MutableStateFlow(
        ConnectionMode.fromName(prefs.getString("pref_connection_mode", ConnectionMode.VPN.name))
    )
    val connectionMode = _connectionMode.asStateFlow()

    private val _socksPort = MutableStateFlow(
        prefs.getInt("pref_socks_port", DEFAULT_SOCKS_PORT).takeIf { it in 1024..65535 } ?: DEFAULT_SOCKS_PORT
    )
    val socksPort = _socksPort.asStateFlow()

    private val _httpPort = MutableStateFlow(
        prefs.getInt("pref_http_port", DEFAULT_HTTP_PORT).takeIf { it in 1024..65535 } ?: DEFAULT_HTTP_PORT
    )
    val httpPort = _httpPort.asStateFlow()

    private val _enableTlsFragment = MutableStateFlow(prefs.getBoolean("pref_tls_fragment", false))
    val enableTlsFragment = _enableTlsFragment.asStateFlow()

    private val _enableFakeDns = MutableStateFlow(prefs.getBoolean("pref_fake_dns", false))
    val enableFakeDns = _enableFakeDns.asStateFlow()

    private val _subscriptionUrl = MutableStateFlow(prefs.getString("pref_sub_url", "") ?: "")
    val subscriptionUrl = _subscriptionUrl.asStateFlow()

    private val _loggedInEmail = MutableStateFlow(
        prefs.getString("pref_panel_email", null)?.takeIf { it.isNotBlank() }
    )
    val loggedInEmail = _loggedInEmail.asStateFlow()

    private val _loggedInSubId = MutableStateFlow(
        prefs.getString("pref_panel_sub_id", null)?.takeIf { it.isNotBlank() }
    )
    val loggedInSubId = _loggedInSubId.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(
        !_loggedInEmail.value.isNullOrBlank() && !_loggedInSubId.value.isNullOrBlank()
    )
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<SubscriptionStatus>>(emptyList())
    val subscriptions = _subscriptions.asStateFlow()

    private val _activeSubscriptionId = MutableStateFlow<String?>(
        prefs.getString("active_subscription_id", null)?.takeIf { it.isNotBlank() }
    )
    val activeSubscriptionId = _activeSubscriptionId.asStateFlow()

    private val _panelSubscriptionStatus = MutableStateFlow<SubscriptionStatus?>(null)
    val panelSubscriptionStatus = _panelSubscriptionStatus.asStateFlow()

    private val _manualSubscriptionStatus = MutableStateFlow<SubscriptionStatus?>(null)
    val manualSubscriptionStatus = _manualSubscriptionStatus.asStateFlow()

    private val _activeSubscriptionStatus = MutableStateFlow<SubscriptionStatus?>(null)
    val activeSubscriptionStatus = _activeSubscriptionStatus.asStateFlow()

    init {
        loadSubscriptions()
        loadNodes()
        migrateLegacyNodeOwnership()
        recomputeActiveSubscriptionStatus()
    }

    private fun recomputeActiveSubscriptionStatus() {
        val subs = _subscriptions.value
        val selected = _selectedNode.value
        val selectedNodeSub = selected?.subscriptionId?.let { id -> subs.firstOrNull { it.id == id } }
        val activeIdSub = _activeSubscriptionId.value?.let { id -> subs.firstOrNull { it.id == id } }

        val panel = subs.firstOrNull { it.source == AccountSource.PANEL_LOGIN }
        val manual = when {
            activeIdSub?.source == AccountSource.SUBSCRIPTION_URL -> activeIdSub
            selectedNodeSub?.source == AccountSource.SUBSCRIPTION_URL -> selectedNodeSub
            else -> subs.firstOrNull { it.source == AccountSource.SUBSCRIPTION_URL }
        }

        _panelSubscriptionStatus.value = panel
        _manualSubscriptionStatus.value = manual

        val resolvedActive = when {
            activeIdSub != null -> activeIdSub
            selectedNodeSub != null -> selectedNodeSub
            selected?.origin == NodeOrigin.SUBSCRIPTION && manual != null -> manual
            selected?.origin == NodeOrigin.PANEL_ACCOUNT && panel != null -> panel
            panel != null -> panel
            manual != null -> manual
            else -> subs.firstOrNull()
        }

        _activeSubscriptionStatus.value = resolvedActive
        if (resolvedActive != null && _activeSubscriptionId.value != resolvedActive.id) {
            _activeSubscriptionId.value = resolvedActive.id
            prefs.edit().putString("active_subscription_id", resolvedActive.id).apply()
        } else if (resolvedActive == null && _activeSubscriptionId.value != null) {
            _activeSubscriptionId.value = null
            prefs.edit().remove("active_subscription_id").apply()
        }
    }

    private fun parseSubscriptionJson(obj: JSONObject): SubscriptionStatus {
        val srcRaw = obj.optString("source", AccountSource.SUBSCRIPTION_URL.name)
        val source = AccountSource.entries.firstOrNull { it.name == srcRaw } ?: AccountSource.SUBSCRIPTION_URL
        val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return SubscriptionStatus(
            id = id,
            source = source,
            title = obj.optString("title", ""),
            email = obj.optString("email", ""),
            subId = obj.optString("subId", ""),
            subscriptionUrl = obj.optString("subscriptionUrl", ""),
            uploadBytes = obj.optLong("uploadBytes", 0L),
            downloadBytes = obj.optLong("downloadBytes", 0L),
            totalBytes = obj.optLong("totalBytes", 0L),
            expiryTimeMs = obj.optLong("expiryTimeMs", 0L),
            enabled = obj.optBoolean("enabled", true),
            lastUpdatedMs = obj.optLong("lastUpdatedMs", 0L)
        )
    }

    private fun subscriptionToJson(status: SubscriptionStatus): JSONObject {
        return JSONObject().apply {
            put("id", status.id)
            put("source", status.source.name)
            put("title", status.title)
            put("email", status.email)
            put("subId", status.subId)
            put("subscriptionUrl", status.subscriptionUrl)
            put("uploadBytes", status.uploadBytes)
            put("downloadBytes", status.downloadBytes)
            put("totalBytes", status.totalBytes)
            put("expiryTimeMs", status.expiryTimeMs)
            put("enabled", status.enabled)
            put("lastUpdatedMs", status.lastUpdatedMs)
        }
    }

    private fun loadSubscriptionStatusLegacy(key: String): SubscriptionStatus? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            parseSubscriptionJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    private fun loadSubscriptions() {
        val savedRaw = prefs.getString("saved_subscriptions", null)
        if (!savedRaw.isNullOrBlank()) {
            try {
                val arr = JSONArray(savedRaw)
                val list = mutableListOf<SubscriptionStatus>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(parseSubscriptionJson(obj))
                }
                _subscriptions.value = list
                return
            } catch (_: Exception) {
            }
        }

        // Migrate legacy single-slot subscription records seamlessly
        val migrated = mutableListOf<SubscriptionStatus>()
        loadSubscriptionStatusLegacy("pref_panel_sub_status")?.let { panel ->
            val fixedId = if (panel.email.isNotBlank()) "panel:${panel.email.lowercase()}:${panel.subId}" else panel.id
            migrated.add(panel.copy(id = fixedId))
        }
        val legacyManual = loadSubscriptionStatusLegacy("pref_manual_sub_status")
        if (legacyManual != null) {
            migrated.add(legacyManual)
        } else {
            val legacyUrl = prefs.getString("pref_sub_url", "")?.trim().orEmpty()
            if (legacyUrl.isNotBlank()) {
                val hostTitle = runCatching { java.net.URI(legacyUrl).host }.getOrNull() ?: "اشتراک لینک"
                migrated.add(
                    SubscriptionStatus(
                        source = AccountSource.SUBSCRIPTION_URL,
                        title = hostTitle,
                        subscriptionUrl = legacyUrl
                    )
                )
            }
        }
        _subscriptions.value = migrated
        if (migrated.isNotEmpty()) {
            saveSubscriptions()
        }
    }

    private fun saveSubscriptions() {
        try {
            val array = JSONArray()
            _subscriptions.value.forEach { sub ->
                array.put(subscriptionToJson(sub))
            }
            prefs.edit().putString("saved_subscriptions", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun migrateLegacyNodeOwnership() {
        val currentNodes = _nodes.value
        val subs = _subscriptions.value
        if (currentNodes.isEmpty() || subs.isEmpty()) return

        val panelSubId = subs.firstOrNull { it.source == AccountSource.PANEL_LOGIN }?.id
        val firstManualSubId = subs.firstOrNull { it.source == AccountSource.SUBSCRIPTION_URL }?.id
        var changed = false

        val migratedNodes = currentNodes.map { node ->
            when {
                node.subscriptionId == null && node.origin == NodeOrigin.PANEL_ACCOUNT && panelSubId != null -> {
                    changed = true
                    node.copy(subscriptionId = panelSubId)
                }
                node.subscriptionId == null && node.origin == NodeOrigin.SUBSCRIPTION && firstManualSubId != null -> {
                    changed = true
                    node.copy(subscriptionId = firstManualSubId)
                }
                else -> node
            }
        }
        if (changed) {
            _nodes.value = migratedNodes
            saveNodes()
        }
    }

    private fun loadNodes() {
        val jsonStr = prefs.getString("saved_nodes", null)
        val loadedList = mutableListOf<ProxyNode>()

        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val protoRaw = obj.optString("protocol", "VLESS")
                    val protocol = ProxyProtocol.entries.firstOrNull { it.name == protoRaw } ?: continue
                    val originRaw = obj.optString("origin", NodeOrigin.MANUAL.name)
                    val origin = NodeOrigin.entries.firstOrNull { it.name == originRaw } ?: NodeOrigin.MANUAL

                    val pingVal = if (obj.has("pingMs") && !obj.isNull("pingMs")) obj.getLong("pingMs") else null

                    loadedList.add(
                        ProxyNode(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "Node"),
                            protocol = protocol,
                            server = obj.optString("server", ""),
                            port = obj.optInt("port", 443),
                            uuid = obj.optString("uuid", ""),
                            username = obj.optString("username", ""),
                            password = obj.optString("password", ""),
                            method = obj.optString("method", "chacha20-ietf-poly1305"),
                            alterId = obj.optInt("alterId", 0),
                            security = obj.optString("security", "tls"),
                            sni = obj.optString("sni", ""),
                            flow = obj.optString("flow").takeIf { it.isNotBlank() && it != "null" },
                            network = obj.optString("network", "tcp"),
                            host = obj.optString("host").takeIf { it.isNotBlank() && it != "null" },
                            path = obj.optString("path").takeIf { it.isNotBlank() && it != "null" },
                            xhttpMode = obj.optString("xhttpMode").takeIf { it.isNotBlank() && it != "null" },
                            xhttpExtra = obj.optString("xhttpExtra").takeIf { it.isNotBlank() && it != "null" },
                            publicKey = obj.optString("publicKey").takeIf { it.isNotBlank() && it != "null" },
                            shortId = obj.optString("shortId").takeIf { it.isNotBlank() && it != "null" },
                            fingerprint = obj.optString("fingerprint", "chrome").takeIf { it.isNotBlank() && it != "null" },
                            alpn = obj.optString("alpn").takeIf { it.isNotBlank() && it != "null" },
                            allowInsecure = obj.optBoolean("allowInsecure", false),
                            obfsType = obj.optString("obfsType").takeIf { it.isNotBlank() && it != "null" },
                            obfsPassword = obj.optString("obfsPassword").takeIf { it.isNotBlank() && it != "null" },
                            wgAddress = obj.optString("wgAddress").takeIf { it.isNotBlank() && it != "null" },
                            wgReserved = obj.optString("wgReserved").takeIf { it.isNotBlank() && it != "null" },
                            wgMtu = obj.optInt("wgMtu", 1420),
                            rawUri = obj.optString("rawUri", ""),
                            rawXrayJson = obj.optString("rawXrayJson").takeIf { it.isNotBlank() && it != "null" },
                            origin = origin,
                            subscriptionId = obj.optString("subscriptionId").takeIf { it.isNotBlank() && it != "null" },
                            flag = obj.optString("flag", "⚡"),
                            pingMs = pingVal
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Strictly Clean / Empty client mode: no dummy or hardcoded servers loaded by default!
        _nodes.value = loadedList
        val savedSelectedId = prefs.getString("selected_node_id", null)
        _selectedNode.value = loadedList.firstOrNull { it.id == savedSelectedId } ?: loadedList.firstOrNull()
    }

    private fun saveNodes() {
        try {
            val array = JSONArray()
            _nodes.value.forEach { node ->
                val obj = JSONObject().apply {
                    put("id", node.id)
                    put("name", node.name)
                    put("protocol", node.protocol.name)
                    put("server", node.server)
                    put("port", node.port)
                    put("uuid", node.uuid)
                    put("username", node.username)
                    put("password", node.password)
                    put("method", node.method)
                    put("alterId", node.alterId)
                    put("security", node.security)
                    put("sni", node.sni)
                    put("flow", node.flow)
                    put("network", node.network)
                    put("host", node.host)
                    put("path", node.path)
                    put("xhttpMode", node.xhttpMode)
                    put("xhttpExtra", node.xhttpExtra)
                    put("publicKey", node.publicKey)
                    put("shortId", node.shortId)
                    put("fingerprint", node.fingerprint)
                    put("alpn", node.alpn)
                    put("allowInsecure", node.allowInsecure)
                    put("obfsType", node.obfsType)
                    put("obfsPassword", node.obfsPassword)
                    put("wgAddress", node.wgAddress)
                    put("wgReserved", node.wgReserved)
                    put("wgMtu", node.wgMtu)
                    put("rawUri", node.rawUri)
                    put("rawXrayJson", node.rawXrayJson)
                    put("origin", node.origin.name)
                    put("subscriptionId", node.subscriptionId)
                    put("flag", node.flag)
                    node.pingMs?.let { put("pingMs", it) }
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_nodes", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun selectNode(node: ProxyNode) {
        _selectedNode.value = node
        prefs.edit().putString("selected_node_id", node.id).apply()
        if (!node.subscriptionId.isNullOrBlank()) {
            _activeSubscriptionId.value = node.subscriptionId
            prefs.edit().putString("active_subscription_id", node.subscriptionId).apply()
        }
        recomputeActiveSubscriptionStatus()
    }

    fun selectSubscription(subscriptionId: String) {
        val target = _subscriptions.value.firstOrNull { it.id == subscriptionId } ?: return
        _activeSubscriptionId.value = target.id
        prefs.edit().putString("active_subscription_id", target.id).apply()
        if (target.source == AccountSource.SUBSCRIPTION_URL && target.subscriptionUrl.isNotBlank()) {
            setSubscriptionUrl(target.subscriptionUrl)
        }
        val currentSelected = _selectedNode.value
        if (currentSelected?.subscriptionId != target.id) {
            val firstSubNode = _nodes.value.firstOrNull { it.subscriptionId == target.id }
            if (firstSubNode != null) {
                _selectedNode.value = firstSubNode
                prefs.edit().putString("selected_node_id", firstSubNode.id).apply()
            }
        }
        recomputeActiveSubscriptionStatus()
    }

    fun addNode(node: ProxyNode) {
        val current = _nodes.value.toMutableList()
        current.add(0, node)
        _nodes.value = current
        saveNodes()
        if (_selectedNode.value == null) {
            selectNode(node)
        }
    }

    private fun nodeIdentityKey(node: ProxyNode): String =
        "${node.origin.name}|${node.protocol.name}|${node.server}|${node.port}|${node.name}|${node.rawUri}"

    fun addNodes(newNodes: List<ProxyNode>) {
        if (newNodes.isEmpty()) return
        val current = _nodes.value.toMutableList()
        val existingKeys = current
            .filter { it.rawUri.isNotEmpty() }
            .map { nodeIdentityKey(it) }
            .toSet()
        val filtered = newNodes.filter {
            it.rawUri.isEmpty() || !existingKeys.contains(nodeIdentityKey(it))
        }
        current.addAll(0, filtered)
        _nodes.value = current
        saveNodes()
        if (_selectedNode.value == null && current.isNotEmpty()) {
            selectNode(current.first())
        }
    }

    fun upsertManualSubscription(
        url: String,
        status: SubscriptionStatus?,
        syncedNodes: List<ProxyNode>,
        targetSubscriptionId: String? = null,
        makeActive: Boolean = true
    ): SubscriptionStatus {
        val trimmedUrl = url.trim()
        val currentSubs = _subscriptions.value.toMutableList()
        val existingIdx = when {
            !targetSubscriptionId.isNullOrBlank() ->
                currentSubs.indexOfFirst { it.id == targetSubscriptionId }
            trimmedUrl.isNotBlank() ->
                currentSubs.indexOfFirst {
                    it.source == AccountSource.SUBSCRIPTION_URL &&
                        it.subscriptionUrl.trim().equals(trimmedUrl, ignoreCase = true)
                }
            else -> -1
        }

        val existing = if (existingIdx >= 0) currentSubs[existingIdx] else null
        val subIdToUse = existing?.id ?: UUID.randomUUID().toString()
        val fallbackHostTitle = runCatching { java.net.URI(trimmedUrl).host }
            .getOrNull()?.takeIf { !it.isNullOrBlank() } ?: "اشتراک لینک"

        val baseStatus = status ?: existing ?: SubscriptionStatus(source = AccountSource.SUBSCRIPTION_URL)
        val resolvedTitle = baseStatus.title
            .takeIf { it.isNotBlank() && it != "اشتراک لینک" }
            ?: existing?.title?.takeIf { it.isNotBlank() }
            ?: fallbackHostTitle

        val finalStatus = baseStatus.copy(
            id = subIdToUse,
            source = AccountSource.SUBSCRIPTION_URL,
            title = resolvedTitle,
            subscriptionUrl = trimmedUrl.ifBlank { existing?.subscriptionUrl.orEmpty() },
            lastUpdatedMs = System.currentTimeMillis()
        )

        if (existingIdx >= 0) {
            currentSubs[existingIdx] = finalStatus
        } else {
            currentSubs.add(0, finalStatus)
        }
        _subscriptions.value = currentSubs
        saveSubscriptions()

        val taggedNodes = syncedNodes.map {
            it.copy(origin = NodeOrigin.SUBSCRIPTION, subscriptionId = subIdToUse)
        }

        // Preserve ALL manual configs, panel configs, and OTHER subscriptions' nodes untouched
        val currentNodes = _nodes.value
        val previousSelected = _selectedNode.value
        val keptNodes = currentNodes.filter {
            it.subscriptionId != subIdToUse &&
                !(existingIdx >= 0 && it.origin == NodeOrigin.SUBSCRIPTION && it.subscriptionId == null && currentSubs.count { s -> s.source == AccountSource.SUBSCRIPTION_URL } == 1)
        }
        val updatedNodes = if (existingIdx >= 0) {
            keptNodes + taggedNodes
        } else {
            taggedNodes + keptNodes
        }
        _nodes.value = updatedNodes
        saveNodes()

        if (makeActive || _activeSubscriptionId.value == null || _activeSubscriptionId.value == subIdToUse) {
            _activeSubscriptionId.value = subIdToUse
            prefs.edit().putString("active_subscription_id", subIdToUse).apply()
            if (finalStatus.subscriptionUrl.isNotBlank()) {
                setSubscriptionUrl(finalStatus.subscriptionUrl)
            }
        }

        val nextSelected = when {
            previousSelected != null && previousSelected.subscriptionId == subIdToUse ->
                taggedNodes.firstOrNull {
                    it.rawUri.isNotEmpty() && it.rawUri == previousSelected.rawUri && it.name == previousSelected.name
                } ?: taggedNodes.firstOrNull() ?: updatedNodes.firstOrNull()
            makeActive && taggedNodes.isNotEmpty() ->
                taggedNodes.first()
            previousSelected != null && updatedNodes.any { it.id == previousSelected.id } ->
                updatedNodes.first { it.id == previousSelected.id }
            else -> updatedNodes.firstOrNull()
        }
        _selectedNode.value = nextSelected
        prefs.edit().putString("selected_node_id", nextSelected?.id).apply()
        recomputeActiveSubscriptionStatus()
        return finalStatus
    }

    fun replacePanelAccountNodes(panelNodes: List<ProxyNode>, panelSubscriptionId: String? = null) {
        val targetSubId = panelSubscriptionId
            ?: _subscriptions.value.firstOrNull { it.source == AccountSource.PANEL_LOGIN }?.id
        val taggedPanelNodes = if (targetSubId != null) {
            panelNodes.map { it.copy(origin = NodeOrigin.PANEL_ACCOUNT, subscriptionId = targetSubId) }
        } else {
            panelNodes.map { it.copy(origin = NodeOrigin.PANEL_ACCOUNT) }
        }

        val keptNodes = _nodes.value.filter { it.origin != NodeOrigin.PANEL_ACCOUNT }
        val updated = taggedPanelNodes + keptNodes
        val previousSelected = _selectedNode.value
        _nodes.value = updated
        saveNodes()

        val reselected = when {
            previousSelected != null && previousSelected.origin != NodeOrigin.PANEL_ACCOUNT ->
                updated.firstOrNull { it.id == previousSelected.id }
            previousSelected != null && previousSelected.origin == NodeOrigin.PANEL_ACCOUNT ->
                taggedPanelNodes.firstOrNull {
                    it.name == previousSelected.name && it.server == previousSelected.server && it.port == previousSelected.port
                } ?: taggedPanelNodes.firstOrNull() ?: updated.firstOrNull()
            else -> updated.firstOrNull()
        }
        _selectedNode.value = reselected
        prefs.edit().putString("selected_node_id", reselected?.id).apply()
        recomputeActiveSubscriptionStatus()
    }

    fun replaceSubscriptionNodes(subNodes: List<ProxyNode>) {
        val activeManual = _subscriptions.value.firstOrNull {
            it.source == AccountSource.SUBSCRIPTION_URL && it.id == _activeSubscriptionId.value
        } ?: _subscriptions.value.firstOrNull { it.source == AccountSource.SUBSCRIPTION_URL }

        if (activeManual != null) {
            upsertManualSubscription(
                url = activeManual.subscriptionUrl.ifBlank { _subscriptionUrl.value },
                status = activeManual,
                syncedNodes = subNodes,
                targetSubscriptionId = activeManual.id,
                makeActive = true
            )
            return
        }

        val url = _subscriptionUrl.value.trim()
        if (url.isNotBlank()) {
            upsertManualSubscription(url = url, status = null, syncedNodes = subNodes, makeActive = true)
            return
        }

        val keptNodes = _nodes.value.filter { it.origin != NodeOrigin.SUBSCRIPTION }
        val updated = keptNodes + subNodes
        val previousSelected = _selectedNode.value
        _nodes.value = updated
        saveNodes()

        val reselected = when {
            previousSelected != null && previousSelected.origin != NodeOrigin.SUBSCRIPTION ->
                updated.firstOrNull { it.id == previousSelected.id }
            previousSelected != null && previousSelected.origin == NodeOrigin.SUBSCRIPTION ->
                subNodes.firstOrNull {
                    it.rawUri.isNotEmpty() && it.rawUri == previousSelected.rawUri
                } ?: updated.firstOrNull()
            else -> updated.firstOrNull()
        }
        _selectedNode.value = reselected
        prefs.edit().putString("selected_node_id", reselected?.id).apply()
        recomputeActiveSubscriptionStatus()
    }

    fun savePanelSession(email: String, subId: String, status: SubscriptionStatus, nodes: List<ProxyNode>) {
        _loggedInEmail.value = email
        _loggedInSubId.value = subId
        _isLoggedIn.value = true
        prefs.edit()
            .putString("pref_panel_email", email)
            .putString("pref_panel_sub_id", subId)
            .apply()

        val currentSubs = _subscriptions.value.toMutableList()
        val existingIdx = currentSubs.indexOfFirst { it.source == AccountSource.PANEL_LOGIN }
        val panelEntryId = if (existingIdx >= 0) {
            currentSubs[existingIdx].id
        } else {
            "panel:${email.trim().lowercase()}:${subId.trim()}"
        }
        val finalStatus = status.copy(
            id = panelEntryId,
            source = AccountSource.PANEL_LOGIN
        )
        if (existingIdx >= 0) {
            currentSubs[existingIdx] = finalStatus
        } else {
            currentSubs.add(0, finalStatus)
        }
        _subscriptions.value = currentSubs
        saveSubscriptions()

        if (_activeSubscriptionId.value == null || existingIdx < 0) {
            _activeSubscriptionId.value = panelEntryId
            prefs.edit().putString("active_subscription_id", panelEntryId).apply()
        }
        replacePanelAccountNodes(nodes, panelEntryId)
    }

    fun updateManualSubscriptionStatus(status: SubscriptionStatus?) {
        if (status == null) {
            val activeManual = _subscriptions.value.firstOrNull { it.source == AccountSource.SUBSCRIPTION_URL }
            if (activeManual != null) {
                deleteSubscription(activeManual.id)
            }
            return
        }
        val currentSubs = _subscriptions.value.toMutableList()
        val existingIdx = currentSubs.indexOfFirst {
            it.id == status.id ||
                (it.source == AccountSource.SUBSCRIPTION_URL &&
                    status.subscriptionUrl.isNotBlank() &&
                    it.subscriptionUrl.equals(status.subscriptionUrl, ignoreCase = true))
        }
        if (existingIdx >= 0) {
            val preservedId = currentSubs[existingIdx].id
            currentSubs[existingIdx] = status.copy(id = preservedId, source = AccountSource.SUBSCRIPTION_URL)
        } else {
            currentSubs.add(0, status.copy(source = AccountSource.SUBSCRIPTION_URL))
        }
        _subscriptions.value = currentSubs
        saveSubscriptions()
        recomputeActiveSubscriptionStatus()
    }

    data class DeleteSubscriptionOutcome(
        val removed: SubscriptionStatus?,
        val affectedSelectedNode: Boolean,
        val removedNodeNames: Set<String>
    )

    fun deleteSubscription(subscriptionId: String): DeleteSubscriptionOutcome {
        val currentSubs = _subscriptions.value.toMutableList()
        val target = currentSubs.firstOrNull { it.id == subscriptionId }
            ?: return DeleteSubscriptionOutcome(null, false, emptySet())

        val isOwnedByTarget: (ProxyNode) -> Boolean = { node ->
            node.subscriptionId == subscriptionId ||
                (node.subscriptionId == null && target.source == AccountSource.PANEL_LOGIN && node.origin == NodeOrigin.PANEL_ACCOUNT)
        }

        val removedNodes = _nodes.value.filter(isOwnedByTarget)
        val removedNames = removedNodes.map { it.name }.toSet()
        val previousSelected = _selectedNode.value
        val affectedSelected = previousSelected != null && isOwnedByTarget(previousSelected)

        val remainingNodes = _nodes.value.filterNot(isOwnedByTarget)
        _nodes.value = remainingNodes
        saveNodes()

        currentSubs.removeAll { it.id == subscriptionId }
        _subscriptions.value = currentSubs
        saveSubscriptions()

        if (target.source == AccountSource.PANEL_LOGIN &&
            currentSubs.none { it.source == AccountSource.PANEL_LOGIN }
        ) {
            _loggedInEmail.value = null
            _loggedInSubId.value = null
            _isLoggedIn.value = false
            prefs.edit()
                .remove("pref_panel_email")
                .remove("pref_panel_sub_id")
                .remove("pref_panel_sub_status")
                .apply()
        }

        val nextSelected = if (affectedSelected) {
            remainingNodes.firstOrNull()
        } else {
            previousSelected?.let { prev -> remainingNodes.firstOrNull { it.id == prev.id } }
                ?: remainingNodes.firstOrNull()
        }
        _selectedNode.value = nextSelected
        prefs.edit().putString("selected_node_id", nextSelected?.id).apply()

        if (_activeSubscriptionId.value == subscriptionId) {
            val nextSubId = nextSelected?.subscriptionId ?: currentSubs.firstOrNull()?.id
            _activeSubscriptionId.value = nextSubId
            if (nextSubId != null) {
                prefs.edit().putString("active_subscription_id", nextSubId).apply()
            } else {
                prefs.edit().remove("active_subscription_id").apply()
            }
        }

        val nextManualUrl = currentSubs.firstOrNull { it.source == AccountSource.SUBSCRIPTION_URL }
            ?.subscriptionUrl.orEmpty()
        setSubscriptionUrl(nextManualUrl)
        recomputeActiveSubscriptionStatus()

        return DeleteSubscriptionOutcome(
            removed = target,
            affectedSelectedNode = affectedSelected,
            removedNodeNames = removedNames
        )
    }

    fun clearPanelAccountSession() {
        val panelSub = _subscriptions.value.firstOrNull { it.source == AccountSource.PANEL_LOGIN }
        if (panelSub != null) {
            deleteSubscription(panelSub.id)
            return
        }
        _loggedInEmail.value = null
        _loggedInSubId.value = null
        _isLoggedIn.value = false
        prefs.edit()
            .remove("pref_panel_email")
            .remove("pref_panel_sub_id")
            .remove("pref_panel_sub_status")
            .apply()

        val remaining = _nodes.value.filter { it.origin != NodeOrigin.PANEL_ACCOUNT }
        _nodes.value = remaining
        saveNodes()

        val prev = _selectedNode.value
        val nextSelected = if (prev != null && prev.origin != NodeOrigin.PANEL_ACCOUNT) {
            remaining.firstOrNull { it.id == prev.id } ?: remaining.firstOrNull()
        } else {
            remaining.firstOrNull()
        }
        _selectedNode.value = nextSelected
        prefs.edit().putString("selected_node_id", nextSelected?.id).apply()
        recomputeActiveSubscriptionStatus()
    }

    fun deleteNode(id: String) {
        val current = _nodes.value.toMutableList()
        current.removeAll { it.id == id }
        _nodes.value = current
        if (_selectedNode.value?.id == id) {
            _selectedNode.value = current.firstOrNull()
            prefs.edit().putString("selected_node_id", _selectedNode.value?.id).apply()
        }
        saveNodes()
        recomputeActiveSubscriptionStatus()
    }

    fun clearAllNodes() {
        _nodes.value = emptyList()
        _selectedNode.value = null
        prefs.edit().remove("selected_node_id").apply()
        saveNodes()
        recomputeActiveSubscriptionStatus()
    }

    fun updateNodePing(id: String, ping: Long) {
        val current = _nodes.value.map { node ->
            if (node.id == id) node.copy(pingMs = ping, isTesting = false) else node
        }
        _nodes.value = current
        if (_selectedNode.value?.id == id) {
            _selectedNode.value = _selectedNode.value?.copy(pingMs = ping, isTesting = false)
        }
        saveNodes()
    }

    fun setNodeTesting(id: String, testing: Boolean) {
        _nodes.value = _nodes.value.map {
            if (it.id == id) it.copy(isTesting = testing) else it
        }
    }

    fun sortNodesByPing() {
        val current = _nodes.value.sortedWith(
            compareBy<ProxyNode> {
                when {
                    it.pingMs == null -> 99999L
                    it.pingMs!! < 0 -> 88888L
                    else -> it.pingMs!!
                }
            }
        )
        _nodes.value = current
        saveNodes()
    }

    fun setBypassIran(bypass: Boolean) {
        _bypassIran.value = bypass
        prefs.edit().putBoolean("pref_bypass_iran", bypass).apply()
    }

    fun setSelectedDns(dns: String) {
        _selectedDns.value = dns
        prefs.edit().putString("pref_dns", dns).apply()
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _connectionMode.value = mode
        prefs.edit().putString("pref_connection_mode", mode.name).apply()
    }

    fun setPorts(socksPort: Int, httpPort: Int): Boolean {
        if (socksPort !in 1024..65535 || httpPort !in 1024..65535 || socksPort == httpPort) {
            return false
        }
        _socksPort.value = socksPort
        _httpPort.value = httpPort
        prefs.edit()
            .putInt("pref_socks_port", socksPort)
            .putInt("pref_http_port", httpPort)
            .apply()
        return true
    }

    fun resetPortsToDefault() {
        _socksPort.value = DEFAULT_SOCKS_PORT
        _httpPort.value = DEFAULT_HTTP_PORT
        prefs.edit()
            .putInt("pref_socks_port", DEFAULT_SOCKS_PORT)
            .putInt("pref_http_port", DEFAULT_HTTP_PORT)
            .apply()
    }

    fun setEnableTlsFragment(enabled: Boolean) {
        _enableTlsFragment.value = enabled
        prefs.edit().putBoolean("pref_tls_fragment", enabled).apply()
    }

    fun setEnableFakeDns(enabled: Boolean) {
        _enableFakeDns.value = enabled
        prefs.edit().putBoolean("pref_fake_dns", enabled).apply()
    }

    fun setSubscriptionUrl(url: String) {
        _subscriptionUrl.value = url
        prefs.edit().putString("pref_sub_url", url).apply()
    }

    fun getStorePlans(): List<StorePlan> {
        return listOf(
            StorePlan(
                id = "plan_1m_50g",
                title = "پلن ۱ ماهه پایه",
                durationText = "۳۰ روزه",
                trafficText = "۵۰ گیگابایت ترافیک",
                priceTomans = "۱۴۹,۰۰۰ تومان",
                priceUsd = "$2.5",
                features = listOf(
                    "۱ کاربر همزمان بدون افت سرعت",
                    "پشتیبانی از پروتکل‌های VLESS Reality، XHTTP و Hysteria 2",
                    "سرورهای اختصاصی آلمان و هلند",
                    "آی‌پی ثابت مخصوص یوتیوب، اینستاگرام و وب‌گردی",
                    "ضمانت بازگشت وجه کامل در صورت قطعی"
                )
            ),
            StorePlan(
                id = "plan_3m_120g",
                title = "پلن ۳ ماهه VIP (محبوب‌ترین)",
                durationText = "۹۰ روزه",
                trafficText = "۱۲۰ گیگابایت ترافیک",
                priceTomans = "۳۳۰,۰۰۰ تومان",
                priceUsd = "$5.5",
                isPopular = true,
                badge = "پیشنهاد تیم BND",
                features = listOf(
                    "۲ کاربر همزمان مجزا",
                    "پینگ پایین زیر ۸۰ میلی‌ثانیه مخصوص گیمینگ و ترید",
                    "دسترسی به تمامی لوکیشن‌های پرسرعت اروپا و آسیا",
                    "پشتیبانی از تانل VLESS Reality و XHTTP ضد فیلترینگ",
                    "پشتیبانی اختصاصی ۲۴ ساعته در تلگرام"
                )
            ),
            StorePlan(
                id = "plan_6m_unlimited",
                title = "پلن نامحدود طلایی ۶ ماهه",
                durationText = "۱۸۰ روزه",
                trafficText = "ترافیک کاملاً نامحدود",
                priceTomans = "۵۸۰,۰۰۰ تومان",
                priceUsd = "$9.9",
                badge = "۱۰ گیگابیت اختصاصی",
                features = listOf(
                    "۳ کاربر همزمان",
                    "ترافیک کاملاً نامحدود مصرف منصفانه",
                    "سرورهای پریمیوم با پهنای باند ۱۰ گیگابیت بر ثانیه",
                    "سازگار با تمام اپراتورها (همراه اول، ایرانسل، مخابرات، رایتل)",
                    "کانفیگ‌های رزرو خودکار هنگام اختلال سراسری اینترنت"
                )
            ),
            StorePlan(
                id = "plan_family_300g",
                title = "پلن خانوادگی ۵ کاربره",
                durationText = "۹۰ روزه",
                trafficText = "۳۰۰ گیگابایت ترافیک",
                priceTomans = "۶۹۰,۰۰۰ تومان",
                priceUsd = "$12.0",
                features = listOf(
                    "۵ کاربر همزمان با اتصال پایدار",
                    "پشتیبانی از تمامی سیستم‌عامل‌ها (اندروید، iOS، ویندوز)",
                    "الگوریتم ضد مسدودی هوشمند DPI"
                )
            )
        )
    }
}
