package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ConnectionMode
import com.example.model.ProxyNode
import com.example.model.ProxyProtocol
import com.example.model.StorePlan
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

    init {
        loadNodes()
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

    fun addNodes(newNodes: List<ProxyNode>) {
        if (newNodes.isEmpty()) return
        val current = _nodes.value.toMutableList()
        val existingUris = current.map { it.rawUri }.filter { it.isNotEmpty() }.toSet()
        val filtered = newNodes.filter { it.rawUri.isEmpty() || !existingUris.contains(it.rawUri) }
        current.addAll(0, filtered)
        _nodes.value = current
        saveNodes()
        if (_selectedNode.value == null && current.isNotEmpty()) {
            selectNode(current.first())
        }
    }

    fun deleteNode(id: String) {
        val current = _nodes.value.toMutableList()
        current.removeAll { it.id == id }
        _nodes.value = current
        if (_selectedNode.value?.id == id) {
            _selectedNode.value = current.firstOrNull()
        }
        saveNodes()
    }

    fun clearAllNodes() {
        _nodes.value = emptyList()
        _selectedNode.value = null
        saveNodes()
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
