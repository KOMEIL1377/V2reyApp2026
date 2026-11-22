package com.example.parser

import com.example.model.ProxyNode
import com.example.model.ProxyProtocol
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64
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
        val path: String?,
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

        // UserInfo & Authority (host:port/path)
        var userInfo: String? = null
        val atIdx = remaining.lastIndexOf('@')
        if (atIdx != -1) {
            userInfo = remaining.substring(0, atIdx)
            remaining = remaining.substring(atIdx + 1)
        }

        var path: String? = null
        val slashIdx = remaining.indexOf('/')
        if (slashIdx != -1) {
            path = remaining.substring(slashIdx)
            remaining = remaining.substring(0, slashIdx)
        }

        // Host and Port (supports IPv6 [::1]:443)
        var host = remaining
        var port = -1
        if (remaining.startsWith("[")) {
            val closeBracket = remaining.indexOf(']')
            if (closeBracket != -1) {
                host = remaining.substring(1, closeBracket)
                val rest = remaining.substring(closeBracket + 1)
                if (rest.startsWith(":")) {
                    port = rest.substring(1).toIntOrNull() ?: -1
                }
            }
        } else {
            val colonIdx = remaining.lastIndexOf(':')
            if (colonIdx != -1) {
                val potentialPort = remaining.substring(colonIdx + 1)
                port = potentialPort.toIntOrNull() ?: -1
                host = remaining.substring(0, colonIdx)
            }
        }

        host = host.trim('/')

        return SimpleUri(
            scheme = scheme,
            userInfo = userInfo,
            host = if (host.isNotEmpty()) host else null,
            port = port,
            path = path,
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
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("shadowsocks://", ignoreCase = true) ||
                    trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("socks://", ignoreCase = true) ||
                    trimmed.startsWith("socks5://", ignoreCase = true) -> parseSocks(trimmed)
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> parseHttpProxy(trimmed)
                trimmed.startsWith("wireguard://", ignoreCase = true) ||
                    trimmed.startsWith("wg://", ignoreCase = true) -> parseWireGuard(trimmed)
                trimmed.startsWith("hysteria2://", ignoreCase = true) ||
                    trimmed.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(trimmed)
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
        val fragment = decodeSafe(uri.fragment ?: "VLESS Node").ifBlank { host }

        val security = uri.getQueryParameter("security")?.lowercase()?.ifBlank { "none" } ?: "none"
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: ""
        val flow = uri.getQueryParameter("flow")?.takeIf { it.isNotBlank() }
        val network = uri.getQueryParameter("type")?.lowercase()?.ifBlank { "tcp" } ?: "tcp"
        val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName")
        val headerHost = uri.getQueryParameter("host")
        val pbk = uri.getQueryParameter("pbk")
        val sid = uri.getQueryParameter("sid")
        val fp = uri.getQueryParameter("fp")?.takeIf { it.isNotBlank() } ?: "chrome"
        val alpn = uri.getQueryParameter("alpn")?.takeIf { it.isNotBlank() }
        val xhttpMode = uri.getQueryParameter("mode")?.takeIf { it.isNotBlank() }
        val xhttpExtra = uri.getQueryParameter("extra")?.takeIf { it.isNotBlank() }
        val allowInsecure = isTruthy(uri.getQueryParameter("allowInsecure")) ||
            isTruthy(uri.getQueryParameter("insecure"))

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = fragment,
            protocol = ProxyProtocol.VLESS,
            server = host,
            port = port,
            uuid = decodeSafe(userInfo),
            security = security,
            sni = sni.ifBlank { headerHost ?: host },
            flow = flow,
            network = network,
            host = headerHost,
            path = path,
            xhttpMode = xhttpMode,
            xhttpExtra = xhttpExtra,
            publicKey = pbk,
            shortId = sid,
            fingerprint = fp,
            alpn = alpn,
            allowInsecure = allowInsecure,
            rawUri = uriStr,
            flag = detectFlag(fragment, host)
        )
    }

    private fun parseVmess(uriStr: String): ProxyNode? {
        val rawPayload = uriStr.substringAfter("://").trim()
        val jsonStr = decodeBase64Flexible(rawPayload)
        if (jsonStr != null && jsonStr.trimStart().startsWith("{")) {
            val json = JSONObject(jsonStr)
            val host = json.optString("add", "").trim()
            if (host.isEmpty()) return null
            val port = json.opt("port")?.toString()?.toIntOrNull() ?: 443
            val uuid = json.optString("id", "").trim()
            if (uuid.isEmpty()) return null
            val aid = json.opt("aid")?.toString()?.toIntOrNull() ?: 0
            val name = json.optString("ps", "VMess Node").ifBlank { host }
            val net = json.optString("net", "tcp").lowercase().ifBlank { "tcp" }
            val tls = json.optString("tls", "none").lowercase().ifBlank { "none" }
            val headerHost = json.optString("host", "").takeIf { it.isNotBlank() }
            val sni = json.optString("sni", "").ifBlank { headerHost ?: host }
            val path = json.optString("path", "").takeIf { it.isNotBlank() }
            val scy = json.optString("scy", "auto").ifBlank { "auto" }
            val fp = json.optString("fp", "chrome").takeIf { it.isNotBlank() } ?: "chrome"
            val alpn = json.optString("alpn", "").takeIf { it.isNotBlank() }

            return ProxyNode(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = ProxyProtocol.VMESS,
                server = host,
                port = port,
                uuid = uuid,
                method = scy,
                alterId = aid,
                security = tls,
                sni = sni,
                network = net,
                host = headerHost,
                path = path,
                fingerprint = fp,
                alpn = alpn,
                rawUri = uriStr,
                flag = detectFlag(name, host)
            )
        }

        // URL-style vmess://uuid@host:port?...#name
        val uri = parseSimpleUri(uriStr) ?: return null
        val host = uri.host ?: return null
        val uuid = uri.userInfo ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeSafe(uri.fragment ?: "VMess Node").ifBlank { host }
        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.VMESS,
            server = host,
            port = port,
            uuid = decodeSafe(uuid),
            security = uri.getQueryParameter("tls") ?: uri.getQueryParameter("security") ?: "none",
            sni = uri.getQueryParameter("sni") ?: host,
            network = uri.getQueryParameter("type") ?: "tcp",
            host = uri.getQueryParameter("host"),
            path = uri.getQueryParameter("path"),
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseTrojan(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeSafe(uri.fragment ?: "Trojan Node").ifBlank { host }
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
        val network = uri.getQueryParameter("type")?.lowercase() ?: "tcp"
        val headerHost = uri.getQueryParameter("host")
        val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName")
        val security = uri.getQueryParameter("security")?.lowercase() ?: "tls"
        val fp = uri.getQueryParameter("fp")?.takeIf { it.isNotBlank() } ?: "chrome"
        val alpn = uri.getQueryParameter("alpn")?.takeIf { it.isNotBlank() }
        val allowInsecure = isTruthy(uri.getQueryParameter("allowInsecure")) ||
            isTruthy(uri.getQueryParameter("insecure"))

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.TROJAN,
            server = host,
            port = port,
            password = decodeSafe(password),
            security = security,
            sni = sni,
            network = network,
            host = headerHost,
            path = path,
            fingerprint = fp,
            alpn = alpn,
            allowInsecure = allowInsecure,
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseHysteria2(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val password = decodeSafe(uri.userInfo ?: "")
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeSafe(uri.fragment ?: "Hysteria 2 Node").ifBlank { host }
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
        val insecure = isTruthy(uri.getQueryParameter("insecure")) ||
            isTruthy(uri.getQueryParameter("allowInsecure"))
        val obfs = uri.getQueryParameter("obfs")?.takeIf { it.isNotBlank() && it != "none" }
        val obfsPass = uri.getQueryParameter("obfs-password")?.takeIf { it.isNotBlank() }
        val alpn = uri.getQueryParameter("alpn")?.takeIf { it.isNotBlank() }

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.HYSTERIA2,
            server = host,
            port = port,
            password = password,
            security = if (insecure) "insecure" else "tls",
            sni = sni,
            network = "udp",
            alpn = alpn,
            allowInsecure = insecure,
            obfsType = obfs,
            obfsPassword = obfsPass,
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseShadowsocks(uriStr: String): ProxyNode? {
        val normalized = if (uriStr.startsWith("shadowsocks://", ignoreCase = true)) {
            "ss://" + uriStr.substring("shadowsocks://".length)
        } else {
            uriStr
        }

        val uri = parseSimpleUri(normalized) ?: return null
        val rawFragment = decodeSafe(uri.fragment ?: "Shadowsocks Node")

        // Case 1: ss://BASE64(method:password@host:port)#name
        if (uri.userInfo == null && uri.host != null) {
            val decoded = decodeBase64Flexible(uri.host)
            if (decoded != null && decoded.contains("@")) {
                val reparsed = parseSimpleUri("ss://$decoded#${uri.fragment.orEmpty()}")
                if (reparsed != null && reparsed.host != null) {
                    val (method, password) = splitPair(reparsed.userInfo.orEmpty(), defaultFirst = "chacha20-ietf-poly1305")
                    val name = rawFragment.ifBlank { reparsed.host }
                    return ProxyNode(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        protocol = ProxyProtocol.SHADOWSOCKS,
                        server = reparsed.host,
                        port = if (reparsed.port > 0) reparsed.port else 8388,
                        password = password,
                        method = method,
                        security = "none",
                        rawUri = uriStr,
                        flag = detectFlag(name, reparsed.host)
                    )
                }
            }
        }

        // Case 2: SIP002 ss://BASE64(method:password)@host:port#name OR ss://method:password@host:port#name
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 8388
        val rawUserInfo = uri.userInfo ?: ""
        val decodedUserInfo = if (rawUserInfo.contains(":")) {
            decodeSafe(rawUserInfo)
        } else {
            decodeBase64Flexible(rawUserInfo) ?: decodeSafe(rawUserInfo)
        }
        val (method, password) = splitPair(decodedUserInfo, defaultFirst = "chacha20-ietf-poly1305")
        val name = rawFragment.ifBlank { host }

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.SHADOWSOCKS,
            server = host,
            port = port,
            password = password,
            method = method,
            security = "none",
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseSocks(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val rawFragment = decodeSafe(uri.fragment ?: "SOCKS5 Proxy")

        // Support base64-encoded user:pass or base64(user:pass@host:port)
        if (uri.userInfo == null && uri.host != null && uri.port <= 0) {
            val decoded = decodeBase64Flexible(uri.host)
            if (decoded != null && decoded.contains(":")) {
                val reparsed = parseSimpleUri("socks://$decoded#${uri.fragment.orEmpty()}")
                if (reparsed != null && reparsed.host != null) {
                    val (user, pass) = if (!reparsed.userInfo.isNullOrBlank()) {
                        splitPair(reparsed.userInfo, defaultFirst = "")
                    } else Pair("", "")
                    val name = rawFragment.ifBlank { reparsed.host }
                    return ProxyNode(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        protocol = ProxyProtocol.SOCKS,
                        server = reparsed.host,
                        port = if (reparsed.port > 0) reparsed.port else 1080,
                        username = user,
                        password = pass,
                        security = "none",
                        rawUri = uriStr,
                        flag = detectFlag(name, reparsed.host)
                    )
                }
            }
        }

        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 1080
        val rawUser = uri.userInfo
        val (user, pass) = if (!rawUser.isNullOrBlank()) {
            val decoded = if (rawUser.contains(":")) decodeSafe(rawUser) else (decodeBase64Flexible(rawUser) ?: decodeSafe(rawUser))
            splitPair(decoded, defaultFirst = "")
        } else {
            Pair("", "")
        }
        val name = rawFragment.ifBlank { host }

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.SOCKS,
            server = host,
            port = port,
            username = user,
            password = pass,
            security = uri.getQueryParameter("security") ?: "none",
            sni = uri.getQueryParameter("sni") ?: host,
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseHttpProxy(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val host = uri.host ?: return null
        // Avoid treating plain web/subscription URLs without port or auth as HTTP proxy outbounds
        val looksLikeProxy = uri.port > 0 || !uri.userInfo.isNullOrBlank() || !uri.fragment.isNullOrBlank()
        if (!looksLikeProxy) return null

        val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 8080
        val (user, pass) = if (!uri.userInfo.isNullOrBlank()) {
            splitPair(decodeSafe(uri.userInfo), defaultFirst = "")
        } else {
            Pair("", "")
        }
        val name = decodeSafe(uri.fragment ?: "HTTP Proxy").ifBlank { host }
        val isTls = uri.scheme == "https"

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.HTTP,
            server = host,
            port = port,
            username = user,
            password = pass,
            security = if (isTls) "tls" else "none",
            sni = uri.getQueryParameter("sni") ?: host,
            allowInsecure = isTruthy(uri.getQueryParameter("allowInsecure")) ||
                isTruthy(uri.getQueryParameter("insecure")),
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseWireGuard(uriStr: String): ProxyNode? {
        val uri = parseSimpleUri(uriStr) ?: return null
        val secretKey = decodeSafe(uri.userInfo ?: "").trim()
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 51820
        val name = decodeSafe(uri.fragment ?: "WireGuard Node").ifBlank { host }
        val peerPublicKey = uri.getQueryParameter("publickey")
            ?: uri.getQueryParameter("pbk")
            ?: uri.getQueryParameter("publicKey")
            ?: ""
        val address = uri.getQueryParameter("address") ?: "10.0.0.2/32"
        val reserved = uri.getQueryParameter("reserved")
        val mtu = uri.getQueryParameter("mtu")?.toIntOrNull() ?: 1420

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.WIREGUARD,
            server = host,
            port = port,
            password = secretKey,
            publicKey = peerPublicKey,
            wgAddress = address,
            wgReserved = reserved,
            wgMtu = mtu,
            security = "none",
            network = "udp",
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun parseV2ray(uriStr: String): ProxyNode? {
        val payload = uriStr.substringAfter("://").trim()
        val decoded = decodeBase64Flexible(payload)
        if (decoded != null) {
            if (decoded.trimStart().startsWith("{")) {
                return parseVmess("vmess://$payload")
            }
            if (decoded.contains("://")) {
                parseLine(decoded)?.let { return it }
            }
        }
        if (payload.contains("://")) {
            parseLine(payload)?.let { return it }
        }

        val uri = parseSimpleUri(uriStr) ?: return null
        val userInfo = uri.userInfo ?: ""
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeSafe(uri.fragment ?: "V2Ray Node").ifBlank { host }
        val security = uri.getQueryParameter("security") ?: uri.getQueryParameter("tls") ?: "auto"
        val network = uri.getQueryParameter("type") ?: "tcp"
        val sni = uri.getQueryParameter("sni") ?: host
        val path = uri.getQueryParameter("path")
        val headerHost = uri.getQueryParameter("host")

        return ProxyNode(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProxyProtocol.VMESS,
            server = host,
            port = port,
            uuid = decodeSafe(userInfo),
            security = security,
            sni = sni,
            network = network,
            host = headerHost,
            path = path,
            rawUri = uriStr,
            flag = detectFlag(name, host)
        )
    }

    private fun splitPair(input: String, defaultFirst: String): Pair<String, String> {
        val colonIdx = input.indexOf(':')
        return if (colonIdx != -1) {
            val first = input.substring(0, colonIdx).ifBlank { defaultFirst }
            val second = input.substring(colonIdx + 1)
            Pair(first, second)
        } else {
            Pair(defaultFirst, input)
        }
    }

    fun parseBulkText(text: String): List<ProxyNode> {
        val nodes = mutableListOf<ProxyNode>()
        val trimmed = text.trim()
        val decoded = decodeBase64Flexible(trimmed)
        val contentToParse = if (decoded != null && decoded.contains("://")) {
            decoded
        } else {
            trimmed
        }

        contentToParse.lines().forEach { line ->
            parseLine(line)?.let { nodes.add(it) }
        }
        return nodes
    }

    suspend fun fetchSubscription(url: String): List<ProxyNode> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url.trim())
                    .header("User-Agent", "v2rayNG/1.9.10 (Android; Xray-core)")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                parseBulkText(body)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private fun decodeBase64Flexible(input: String): String? {
        val cleaned = input.trim().replace("\n", "").replace("\r", "")
        if (cleaned.isEmpty()) return null
        val padded = when (cleaned.length % 4) {
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned
        }
        return try {
            String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun isTruthy(value: String?): Boolean {
        return value != null && (value == "1" || value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true))
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
            lower.contains("germany") || lower.contains("de-") || lower.contains("frankfurt") || lower.contains("آلمان") -> "🇩🇪"
            lower.contains("netherlands") || lower.contains("nl-") || lower.contains("amsterdam") || lower.contains("هلند") -> "🇳🇱"
            lower.contains("finland") || lower.contains("fi-") || lower.contains("helsinki") || lower.contains("فنلاند") -> "🇫🇮"
            lower.contains("uk") || lower.contains("united kingdom") || lower.contains("london") || lower.contains("انگلیس") -> "🇬🇧"
            lower.contains("usa") || lower.contains("america") || lower.contains("us-") || lower.contains("آمریکا") -> "🇺🇸"
            lower.contains("france") || lower.contains("fr-") || lower.contains("paris") || lower.contains("فرانسه") -> "🇫🇷"
            lower.contains("turkey") || lower.contains("tr-") || lower.contains("istanbul") || lower.contains("ترکیه") -> "🇹🇷"
            lower.contains("japan") || lower.contains("jp-") || lower.contains("tokyo") || lower.contains("ژاپن") -> "🇯🇵"
            lower.contains("singapore") || lower.contains("sg-") || lower.contains("سنگاپور") -> "🇸🇬"
            lower.contains("switzerland") || lower.contains("ch-") || lower.contains("سوئیس") -> "🇨🇭"
            lower.contains("canada") || lower.contains("ca-") || lower.contains("کانادا") -> "🇨🇦"
            lower.contains("austria") || lower.contains("at-") || lower.contains("اتریش") -> "🇦🇹"
            else -> "⚡"
        }
    }
}
