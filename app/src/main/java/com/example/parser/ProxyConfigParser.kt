package com.example.parser

import android.util.Base64
import com.example.model.AmneziaParams
import com.example.model.ProxyNode
import com.example.model.ProxyProtocol
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit

object ProxyConfigParser {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class SimpleUri(
        val scheme: String,
        val userInfo: String?,
        val host: String?,
        val port: Int,
        val queryParams: Map<String, String>,
        val fragment: String?
    ) {
        fun getQueryParameter(key: String): String? = queryParams[key]
    }

    fun parseSimpleUri(uriString: String): SimpleUri? {
        val trimmed = uriString.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd == -1) return null

        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        var remaining = trimmed.substring(schemeEnd + 3)

        // Fragment
        var fragment: String? = null
        val hashIdx = remaining.indexOf('#')
        if (hashIdx != -1) {
            fragment = remaining.substring(hashIdx + 1)
            remaining = remaining.substring(0, hashIdx)
        }

        // Query params
        val queryParams = mutableMapOf<String, String>()
        val queryIdx = remaining.indexOf('?')
        if (queryIdx != -1) {
            val queryString = remaining.substring(queryIdx + 1)
            remaining = remaining.substring(0, queryIdx)
            queryString.split('&').forEach { param ->
                val eqIdx = param.indexOf('=')
                if (eqIdx != -1) {
                    val k = decodeSafe(param.substring(0, eqIdx))
                    val v = decodeSafe(param.substring(eqIdx + 1))
                    queryParams[k] = v
                } else if (param.isNotEmpty()) {
                    queryParams[decodeSafe(param)] = ""
                }
            }
        }

        // UserInfo & Authority (host:port)
        var userInfo: String? = null
        val atIdx = remaining.lastIndexOf('@')
        if (atIdx != -1) {
            userInfo = remaining.substring(0, atIdx)
            remaining = remaining.substring(atIdx + 1)
        }

        // Host and Port
        var host = remaining
        var port = -1
        val colonIdx = remaining.lastIndexOf(':')
        if (colonIdx != -1 && !remaining.endsWith("]")) {
            val potentialPort = remaining.substring(colonIdx + 1)
            port = potentialPort.toIntOrNull() ?: -1
            host = remaining.substring(0, colonIdx)
        }

        // Strip leading/trailing slashes from host if any
        host = host.trim('/')

        return SimpleUri(
            scheme = scheme,
            userInfo = userInfo,
            host = if (host.isNotEmpty()) host else null,
            port = port,
            queryParams = queryParams,
            fragment = fragment
        )
    }

    fun parseLine(raw: String): ProxyNode? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
            return null
        }

        return try {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
                trimmed.startsWith("v2ray://", ignoreCase = true) -> parseV2ray(trimmed)
                trimmed.startsWith("tuic://", ignoreCase = true) -> parseTuic(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("hysteria2://", ignoreCase = true) ||
                trimmed.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(trimmed)
                trimmed.startsWith("amnezia://", ignoreCase = true) ||
                trimmed.startsWith("awg://", ignoreCase = true) -> parseAmnezia(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("openvpn://", ignoreCase = true) ||
                trimmed.startsWith("client\n") || trimmed.contains("dev tun") -> parseOpenVpn(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVless(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val userInfo = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val fragment = decodeSafe(uri.fragment ?: "VLESS Node")

        val security = uri.getQueryParameter("security") ?: "reality"
        val sni = uri.getQueryParameter("sni") ?: host
        val flow = uri.getQueryParameter("flow")
        val network = uri.getQueryParameter("type") ?: "tcp"
        val path = uri.getQueryParameter("path")
        val pbk = uri.getQueryParameter("pbk")
        val sid = uri.getQueryParameter("sid")
        val fp = uri.getQueryParameter("fp") ?: "chrome"

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = fragment,
            protocol = ProxyProtocol.VLESS,
            server = host,
            port = port,
            uuid = userInfo,
            security = security,
            sni = sni,
            flow = flow,
            network = network,
            path = path,
            publicKey = pbk,
            shortId = sid,
            fingerprint = fp,
            rawUri = uriStr,
            flag = detectFlag(fragment, host)
        )
    }

    private fun parseVmess(uriStr: String): ProxyNode? {
        val base64Payload = uriStr.removePrefix("vmess://").trim()
        val jsonStr = try {
            String(java.util.Base64.getUrlDecoder().decode(base64Payload))
        } catch (e: Exception) {
            try {
                String(java.util.Base64.getDecoder().decode(base64Payload))
            } catch (ex: Exception) {
                return null
            }
        }

        val json = JSONObject(jsonStr)
        val host = json.optString("add", "")
        val port = json.optInt("port", 443)
        val uuid = json.optString("id", "")
        val name = json.optString("ps", "VMess Node")
        val net = json.optString("net", "tcp")
        val tls = json.optString("tls", "none")
        val sni = json.optString("sni", host)
        val path = json.optString("path", "")

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.VMESS,
            server = host,
            port = port,
            uuid = uuid,
            security = if (tls.isNotEmpty()) tls else "none",
            sni = sni,
            network = net,
            path = path,
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseTrojan(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeSafe(uri.fragment ?: "Trojan VIP")
        val sni = uri.getQueryParameter("sni") ?: host
        val network = uri.getQueryParameter("type") ?: "tcp"

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.TROJAN,
            server = host,
            port = port,
            uuid = password,
            security = "tls",
            sni = sni,
            network = network,
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseHysteria2(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val password = uri.userInfo ?: ""
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeSafe(uri.fragment ?: "Hysteria 2 Ultra")
        val sni = uri.getQueryParameter("sni") ?: host
        val insecure = uri.getQueryParameter("insecure") == "1"

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.HYSTERIA2,
            server = host,
            port = port,
            uuid = password,
            security = if (insecure) "insecure" else "tls",
            sni = sni,
            network = "udp",
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseAmnezia(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val host = uri.host ?: "vpn.amnezia.org"
        val port = if (uri.port > 0) uri.port else 51820
        val name = decodeSafe(uri.fragment ?: "AmneziaWG Obfuscated")
        val jc = uri.getQueryParameter("Jc")?.toIntOrNull() ?: 4
        val jmin = uri.getQueryParameter("Jmin")?.toIntOrNull() ?: 40
        val jmax = uri.getQueryParameter("Jmax")?.toIntOrNull() ?: 70
        val s1 = uri.getQueryParameter("S1")?.toIntOrNull() ?: 32
        val s2 = uri.getQueryParameter("S2")?.toIntOrNull() ?: 48
        val h1 = uri.getQueryParameter("H1") ?: "1"
        val h2 = uri.getQueryParameter("H2") ?: "2"
        val h3 = uri.getQueryParameter("H3") ?: "3"
        val h4 = uri.getQueryParameter("H4") ?: "4"

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.AMNEZIA_WG,
            server = host,
            port = port,
            amneziaParams = AmneziaParams(jc, jmin, jmax, s1, s2, h1, h2, h3, h4),
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseShadowsocks(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val name = decodeSafe(uri.fragment ?: "Shadowsocks Node")
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 8388

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.SHADOWSOCKS,
            server = host,
            port = port,
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    fun isOpenVpnConfig(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("openvpn://", ignoreCase = true) ||
               trimmed.contains("dev tun", ignoreCase = true) ||
               trimmed.contains("dev tap", ignoreCase = true) ||
               trimmed.contains("<ca>", ignoreCase = true) ||
               (trimmed.contains("client", ignoreCase = true) && trimmed.contains("remote "))
    }

    fun checkOpenVpnRequiresAuth(configText: String): Boolean {
        val hasInlineCredentials = Regex("(?s)<auth-user-pass>\\s*\\S+\\s+\\S+.*?/auth-user-pass>").containsMatchIn(configText)
        if (hasInlineCredentials) return false

        return Regex("(?m)^\\s*auth-user-pass\\b").containsMatchIn(configText)
    }

    fun embedOpenVpnCredentials(configText: String, username: String, pass: String): String {
        val blockRegex = Regex("(?s)<auth-user-pass>.*?</auth-user-pass>")
        val credentialBlock = "<auth-user-pass>\n$username\n$pass\n</auth-user-pass>"
        return if (blockRegex.containsMatchIn(configText)) {
            configText.replace(blockRegex, credentialBlock)
        } else {
            val directiveRegex = Regex("(?m)^\\s*auth-user-pass(\\s+.*)?$")
            val updated = if (directiveRegex.containsMatchIn(configText)) {
                configText.replace(directiveRegex, "auth-user-pass")
            } else {
                "$configText\nauth-user-pass"
            }
            "$updated\n$credentialBlock\n"
        }
    }

    fun parseOpenVpn(
        configOrUri: String,
        customName: String? = null,
        username: String? = null,
        password: String? = null
    ): ProxyNode {
        val requiresAuth = checkOpenVpnRequiresAuth(configOrUri)
        val finalConfig = if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            embedOpenVpnCredentials(configOrUri, username, password)
        } else {
            configOrUri
        }

        val name = customName ?: if (configOrUri.startsWith("openvpn://")) {
            val uri = parseSimpleUri(configOrUri)
            decodeSafe(uri?.fragment ?: "OpenVPN Profile")
        } else {
            "OpenVPN Direct Profile"
        }

        var host = "openvpn.v2reybnd.vip"
        var port = 1194
        var proto = "udp"
        val lines = finalConfig.lines()
        for (line in lines) {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3 && parts[0] == "remote") {
                host = parts[1]
                port = parts[2].toIntOrNull() ?: 1194
                if (parts.size >= 4) {
                    proto = parts[3].lowercase()
                }
            } else if (parts.size >= 2 && parts[0] == "proto") {
                proto = parts[1].lowercase()
            }
        }

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.OPENVPN,
            server = host,
            port = port,
            network = proto,
            openVpnConfig = finalConfig,
            openVpnUsername = username,
            openVpnPassword = password,
            openVpnRequiresAuth = requiresAuth,
            rawUri = if (configOrUri.startsWith("openvpn://")) configOrUri else "",
            flag = detectFlag(name, host)
        )
    }

    private fun parseTuic(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val userInfo = uri.userInfo ?: ""
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeSafe(uri.fragment ?: "TUIC Node")
        val sni = uri.getQueryParameter("sni") ?: host
        val alpn = uri.getQueryParameter("alpn") ?: "h3"

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.TUIC,
            server = host,
            port = port,
            uuid = userInfo,
            security = "tls",
            sni = sni,
            network = "udp",
            path = alpn,
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseV2ray(uriStr: String): ProxyNode? {
        val payload = uriStr.removePrefix("v2ray://").trim()
        val asVmess = parseVmess("vmess://$payload")
        if (asVmess != null) return asVmess

        val uri = parseSimpleUri(uriStr) ?: return null
        val userInfo = uri.userInfo ?: ""
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeSafe(uri.fragment ?: "V2Ray Node")

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.VMESS,
            server = host,
            port = port,
            uuid = userInfo,
            security = "auto",
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    fun parseBulkText(text: String): List<ProxyNode> {
        val nodes = mutableListOf<ProxyNode>()
        val decodedText = try {
            val decoded = String(java.util.Base64.getDecoder().decode(text.trim()))
            if (decoded.contains("://") || decoded.contains("vless") || decoded.contains("vmess")) {
                decoded
            } else {
                text
            }
        } catch (e: Exception) {
            text
        }

        decodedText.lines().forEach { line ->
            parseLine(line)?.let { nodes.add(it) }
        }
        return nodes
    }

    suspend fun fetchSubscription(url: String): List<ProxyNode> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "v2rayng/1.8.12 (Android 14; Sing-box Xray)")
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                parseBulkText(body)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private fun decodeSafe(str: String): String {
        return try {
            URLDecoder.decode(str, "UTF-8")
        } catch (e: Exception) {
            str
        }
    }

    fun detectFlag(name: String, host: String): String {
        val lower = "$name $host".lowercase()
        return when {
            lower.contains("germany") || lower.contains("de") || lower.contains("frankfurt") || lower.contains("آلمان") -> "🇩🇪"
            lower.contains("netherlands") || lower.contains("nl") || lower.contains("amsterdam") || lower.contains("هلند") -> "🇳🇱"
            lower.contains("finland") || lower.contains("fi") || lower.contains("helsinki") || lower.contains("فنلاند") -> "🇫🇮"
            lower.contains("uk") || lower.contains("united kingdom") || lower.contains("london") || lower.contains("انگلیس") -> "🇬🇧"
            lower.contains("us") || lower.contains("usa") || lower.contains("america") || lower.contains("آمریکا") -> "🇺🇸"
            lower.contains("france") || lower.contains("fr") || lower.contains("paris") || lower.contains("فرانسه") -> "🇫🇷"
            lower.contains("turkey") || lower.contains("tr") || lower.contains("istanbul") || lower.contains("ترکیه") -> "🇹🇷"
            lower.contains("japan") || lower.contains("jp") || lower.contains("tokyo") || lower.contains("ژاپن") -> "🇯🇵"
            lower.contains("singapore") || lower.contains("sg") || lower.contains("سنگاپور") -> "🇸🇬"
            lower.contains("switzerland") || lower.contains("ch") || lower.contains("سوئیس") -> "🇨🇭"
            lower.contains("canada") || lower.contains("ca") || lower.contains("کانادا") -> "🇨🇦"
            lower.contains("austria") || lower.contains("at") || lower.contains("اتریش") -> "🇦🇹"
            else -> "⚡"
        }
    }
}
