package com.example.network

import com.example.BuildConfig
import com.example.model.AccountSource
import com.example.model.NodeOrigin
import com.example.model.ProxyNode
import com.example.model.ProxyProtocol
import com.example.model.SubscriptionStatus
import com.example.parser.ProxyConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed class PanelSyncResult {
    data class Success(
        val status: SubscriptionStatus,
        val nodes: List<ProxyNode>
    ) : PanelSyncResult()

    data class Error(
        val message: String,
        val isAuthError: Boolean = false,
        val isDisabled: Boolean = false
    ) : PanelSyncResult()
}

class SanaeiPanelClient(
    private val baseUrl: String = BuildConfig.SANAEI_PANEL_BASE_URL,
    private val apiToken: String = BuildConfig.SANAEI_PANEL_API_TOKEN,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/')

    private fun buildPanelRequest(url: String): Request {
        val token = apiToken.trim()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("X-API-Key", token)
            .header("token", token)
            .header("Accept", "application/json")
            .build()
    }

    suspend fun authenticateAndFetchAccount(
        email: String,
        subId: String,
        rejectIfDisabled: Boolean = true
    ): PanelSyncResult = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        val trimmedSubId = subId.trim()
        if (trimmedEmail.isEmpty()) {
            return@withContext PanelSyncResult.Error("لطفاً نام کاربری (Email) را وارد کنید")
        }
        if (trimmedSubId.isEmpty()) {
            return@withContext PanelSyncResult.Error("لطفاً رمز عبور / Subscription ID را وارد کنید")
        }

        val cleanBase = normalizedBaseUrl()
        if (cleanBase.isEmpty() || apiToken.isBlank()) {
            return@withContext PanelSyncResult.Error("تنظیمات اتصال به پنل پنل پیکربندی نشده است")
        }

        try {
            val listUrl = "$cleanBase/panel/api/inbounds/list"
            val listResponse = httpClient.newCall(buildPanelRequest(listUrl)).execute()
            val listCode = listResponse.code
            val listBody = listResponse.body?.string().orEmpty()

            if (listCode == 401 || listCode == 403) {
                return@withContext PanelSyncResult.Error(
                    "توکن احراز هویت پنل معتبر نیست یا دسترسی رد شد (کد $listCode)",
                    isAuthError = true
                )
            }
            if (!listResponse.isSuccessful) {
                return@withContext PanelSyncResult.Error(
                    "خطای سرور پنل هنگام دریافت اطلاعات (کد $listCode)"
                )
            }

            // Optionally query live client traffic endpoint for up-to-the-second stats
            val liveClientStat = fetchClientTrafficFallback(cleanBase, trimmedEmail)

            val parsed = parseInboundsListResponse(
                responseJson = listBody,
                panelBaseUrl = cleanBase,
                inputEmail = trimmedEmail,
                inputSubId = trimmedSubId,
                rejectIfDisabled = rejectIfDisabled,
                overrideClientStat = liveClientStat
            )

            if (parsed is PanelSyncResult.Success) {
                val directUrls = extractExplicitSubUrls(listBody, trimmedEmail, trimmedSubId)
                val panelSettingUrls = fetchPanelSettingSubUrls(cleanBase, trimmedSubId)
                val subPayload = fetchPanelSubscriptionPayload(
                    cleanBase = cleanBase,
                    subId = trimmedSubId,
                    prioritizedUrls = directUrls + panelSettingUrls
                )
                if (subPayload != null && subPayload.nodes.isNotEmpty()) {
                    val headerInfo = subPayload.userInfoStatus
                    val mergedTotal = if (parsed.status.totalBytes > 0L) parsed.status.totalBytes else (headerInfo?.totalBytes ?: 0L)
                    val mergedExpiry = if (parsed.status.expiryTimeMs > 0L) parsed.status.expiryTimeMs else (headerInfo?.expiryTimeMs ?: 0L)
                    val mergedUp = if (parsed.status.uploadBytes > 0L) parsed.status.uploadBytes else (headerInfo?.uploadBytes ?: 0L)
                    val mergedDown = if (parsed.status.downloadBytes > 0L) parsed.status.downloadBytes else (headerInfo?.downloadBytes ?: 0L)
                    return@withContext PanelSyncResult.Success(
                        status = parsed.status.copy(
                            subscriptionUrl = subPayload.subscriptionUrl,
                            uploadBytes = mergedUp,
                            downloadBytes = mergedDown,
                            totalBytes = mergedTotal,
                            expiryTimeMs = mergedExpiry,
                            lastUpdatedMs = System.currentTimeMillis()
                        ),
                        nodes = subPayload.nodes
                    )
                }
            }

            parsed
        } catch (e: UnknownHostException) {
            PanelSyncResult.Error("خطای شبکه: آدرس سرور پنل یافت نشد (${e.message ?: "DNS error"})")
        } catch (e: SocketTimeoutException) {
            PanelSyncResult.Error("مهلت اتصال به سرور پنل به پایان رسید (Timeout)")
        } catch (e: ConnectException) {
            PanelSyncResult.Error("امکان برقراری ارتباط با پنل وجود ندارد (${e.message ?: "Connection refused"})")
        } catch (e: SSLException) {
            PanelSyncResult.Error("خطای گواهی امنیتی SSL در اتصال به پنل: ${e.message}")
        } catch (e: IOException) {
            PanelSyncResult.Error("خطای ارتباط شبکه با پنل: ${e.localizedMessage ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            PanelSyncResult.Error("خطای غیرمنتظره در ارتباط با پنل: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun fetchClientTrafficFallback(cleanBase: String, email: String): JSONObject? {
        return try {
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val req = buildPanelRequest("$cleanBase/panel/api/inbounds/getClientTraffics/$encodedEmail")
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                if (!root.optBoolean("success", false)) return null
                root.optJSONObject("obj")
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class FetchedSubPayload(
        val subscriptionUrl: String,
        val nodes: List<ProxyNode>,
        val userInfoStatus: SubscriptionStatus?
    )

    internal fun buildCandidateSubUrlsFromPanelSettings(
        panelBaseUrl: String,
        subId: String,
        panelSettingsObj: JSONObject?
    ): List<String> {
        val urls = mutableListOf<String>()
        val encodedSubId = URLEncoder.encode(subId, "UTF-8").replace("+", "%20")

        if (panelSettingsObj != null) {
            val explicitSubUri = panelSettingsObj.optString("subURI", "").trim()
            if (explicitSubUri.isNotBlank()) {
                val normalized = explicitSubUri.trimEnd('/')
                urls.add("$normalized/$encodedSubId")
            }

            val subPort = panelSettingsObj.optInt("subPort", 0)
            val subDomain = panelSettingsObj.optString("subDomain", "").trim()
            val rawSubPath = panelSettingsObj.optString("subPath", "/sub/").trim().ifBlank { "/sub/" }
            val normPath = "/" + rawSubPath.trim('/') + "/"
            val hasSubCert = panelSettingsObj.optString("subCertFile", "").isNotBlank() ||
                panelSettingsObj.optString("webCertFile", "").isNotBlank()

            val panelUri = runCatching { URI(panelBaseUrl) }.getOrNull()
            val host = subDomain.ifBlank { panelUri?.host.orEmpty() }
            if (host.isNotBlank() && subPort in 1..65535) {
                val primaryScheme = if (hasSubCert || panelUri?.scheme == "https") "https" else "http"
                val portSuffix = if ((primaryScheme == "https" && subPort == 443) || (primaryScheme == "http" && subPort == 80)) "" else ":$subPort"
                urls.add("$primaryScheme://$host$portSuffix$normPath$encodedSubId")
                val altScheme = if (primaryScheme == "https") "http" else "https"
                urls.add("$altScheme://$host:$subPort$normPath$encodedSubId")
            }
        }

        val cleanBase = panelBaseUrl.trim().trimEnd('/')
        if (cleanBase.isNotBlank()) {
            urls.add("$cleanBase/sub/$encodedSubId")
        }
        runCatching {
            val uri = URI(cleanBase)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                val baseScheme = uri.scheme ?: "https"
                urls.add("$baseScheme://$host/sub/$encodedSubId")
                urls.add("$baseScheme://$host:2096/sub/$encodedSubId")
                urls.add("http://$host:2096/sub/$encodedSubId")
                urls.add("$baseScheme://$host:2095/sub/$encodedSubId")
                urls.add("http://$host:2095/sub/$encodedSubId")
                if (uri.port > 0) {
                    urls.add("$baseScheme://$host:${uri.port}/sub/$encodedSubId")
                }
            }
        }

        return urls.distinct()
    }

    private fun extractExplicitSubUrls(
        inboundsJson: String,
        targetEmail: String,
        targetSubId: String
    ): List<String> {
        val found = mutableListOf<String>()
        runCatching {
            val root = JSONObject(inboundsJson)
            listOf("subUrl", "subLink", "subscriptionUrl", "subscription").forEach { key ->
                root.optString(key, "").trim().takeIf { it.startsWith("http", true) }?.let { found.add(it) }
            }
            val inbounds = root.optJSONArray("obj") ?: return@runCatching
            for (i in 0 until inbounds.length()) {
                val inbound = inbounds.optJSONObject(i) ?: continue
                val clients = runCatching {
                    JSONObject(inbound.optString("settings", "{}")).optJSONArray("clients")
                }.getOrNull() ?: continue
                for (j in 0 until clients.length()) {
                    val client = clients.optJSONObject(j) ?: continue
                    val emailMatch = client.optString("email", "").trim().equals(targetEmail, true)
                    val subMatch = client.optString("subId", "").trim() == targetSubId
                    if (emailMatch && subMatch) {
                        listOf("subUrl", "subLink", "subscriptionUrl", "subscription").forEach { key ->
                            client.optString(key, "").trim().takeIf { it.startsWith("http", true) }?.let { found.add(it) }
                        }
                    }
                }
            }
        }
        return found.distinct()
    }

    private fun fetchPanelSettingSubUrls(cleanBase: String, subId: String): List<String> {
        val emptyJsonBody = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val endpoints = listOf(
            Request.Builder()
                .url("$cleanBase/panel/setting/all")
                .post(emptyJsonBody)
                .header("Authorization", "Bearer ${apiToken.trim()}")
                .header("X-API-Key", apiToken.trim())
                .header("token", apiToken.trim())
                .header("Accept", "application/json")
                .build(),
            buildPanelRequest("$cleanBase/panel/setting/all")
        )
        var settingsObj: JSONObject? = null
        for (req in endpoints) {
            try {
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val root = JSONObject(body)
                        if (root.optBoolean("success", true)) {
                            val obj = root.optJSONObject("obj")
                            if (obj != null) {
                                settingsObj = obj
                                return@use
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            if (settingsObj != null) break
        }
        return buildCandidateSubUrlsFromPanelSettings(cleanBase, subId, settingsObj)
    }

    private fun fetchPanelSubscriptionPayload(
        cleanBase: String,
        subId: String,
        prioritizedUrls: List<String>
    ): FetchedSubPayload? {
        val candidates = (prioritizedUrls + buildCandidateSubUrlsFromPanelSettings(cleanBase, subId, null)).distinct()
        for (subUrl in candidates) {
            val publicReq = Request.Builder()
                .url(subUrl)
                .header("User-Agent", "v2rayNG/1.9.10 (Android; Xray-core)")
                .build()
            val attempts = listOf(publicReq, buildPanelRequest(subUrl).newBuilder().header("User-Agent", "v2rayNG/1.9.10 (Android; Xray-core)").build())
            for (req in attempts) {
                try {
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val nodes = ProxyConfigParser.parseBulkTextWithValidation(
                                body,
                                NodeOrigin.PANEL_ACCOUNT
                            ).nodes
                            if (nodes.isNotEmpty()) {
                                val userInfo = ProxyConfigParser.parseSubscriptionUserInfoHeader(
                                    url = subUrl,
                                    userInfoHeader = resp.header("subscription-userinfo"),
                                    profileTitleHeader = resp.header("profile-title")
                                )
                                return FetchedSubPayload(
                                    subscriptionUrl = subUrl,
                                    nodes = nodes,
                                    userInfoStatus = userInfo
                                )
                            }
                        } else if (resp.code != 401 && resp.code != 403) {
                            break
                        }
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return null
    }

    internal fun parseInboundsListResponse(
        responseJson: String,
        panelBaseUrl: String,
        inputEmail: String,
        inputSubId: String,
        rejectIfDisabled: Boolean,
        overrideClientStat: JSONObject? = null
    ): PanelSyncResult {
        val root = try {
            JSONObject(responseJson)
        } catch (e: Exception) {
            return PanelSyncResult.Error("پاسخ نامعتبر از سرور پنل (JSON parse error)")
        }

        if (root.has("success") && !root.optBoolean("success", true)) {
            val msg = root.optString("msg").takeIf { it.isNotBlank() } ?: "درخواست توسط پنل رد شد"
            return PanelSyncResult.Error(msg)
        }

        val inbounds = root.optJSONArray("obj")
            ?: return PanelSyncResult.Error("فهرست سرویس‌های پنل در پاسخ یافت نشد")

        val trimmedEmail = inputEmail.trim()
        val trimmedSubId = inputSubId.trim()
        val panelHost = runCatching { URI(panelBaseUrl).host }.getOrNull().orEmpty()

        var emailFound = false
        var matchedPrimaryInbound: JSONObject? = null
        var matchedPrimaryClient: JSONObject? = null
        var matchedPrimaryStat: JSONObject? = null

        val matchingSubClients = mutableListOf<Pair<JSONObject, JSONObject>>()

        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            val settingsStr = inbound.optString("settings", "")
            val settingsJson = runCatching { JSONObject(settingsStr) }.getOrNull() ?: continue
            val clients = settingsJson.optJSONArray("clients") ?: continue
            val clientStats = inbound.optJSONArray("clientStats")

            for (j in 0 until clients.length()) {
                val client = clients.optJSONObject(j) ?: continue
                val cEmail = client.optString("email", "").trim()
                val cSubId = client.optString("subId", "").trim()

                if (cEmail.equals(trimmedEmail, ignoreCase = true)) {
                    emailFound = true
                    if (cSubId == trimmedSubId) {
                        if (matchedPrimaryClient == null) {
                            matchedPrimaryInbound = inbound
                            matchedPrimaryClient = client
                            matchedPrimaryStat = findClientStat(clientStats, cEmail)
                        }
                        matchingSubClients.add(inbound to client)
                    }
                } else if (cSubId.isNotEmpty() && cSubId == trimmedSubId) {
                    // Additional inbound node belonging to same subscription ID
                    matchingSubClients.add(inbound to client)
                }
            }
        }

        if (!emailFound) {
            return PanelSyncResult.Error(
                "کاربری با نام کاربری '$trimmedEmail' در پنل یافت نشد",
                isAuthError = true
            )
        }

        if (matchedPrimaryClient == null || matchedPrimaryInbound == null) {
            return PanelSyncResult.Error(
                "رمز عبور / Subscription ID با نام کاربری مطابقت ندارد",
                isAuthError = true
            )
        }

        val effectiveStat = overrideClientStat ?: matchedPrimaryStat
        val clientEnable = if (matchedPrimaryClient.has("enable")) {
            matchedPrimaryClient.optBoolean("enable", true)
        } else true
        val statEnable = if (effectiveStat != null && effectiveStat.has("enable")) {
            effectiveStat.optBoolean("enable", true)
        } else true
        val inboundEnable = matchedPrimaryInbound.optBoolean("enable", true)
        val isEnabled = clientEnable && statEnable && inboundEnable

        if (rejectIfDisabled && !isEnabled) {
            return PanelSyncResult.Error(
                "حساب کاربری '$trimmedEmail' غیرفعال (Disabled) است",
                isDisabled = true
            )
        }

        val up = effectiveStat?.optLong("up", 0L) ?: 0L
        val down = effectiveStat?.optLong("down", 0L) ?: 0L
        val statTotal = effectiveStat?.optLong("total", -1L) ?: -1L
        val clientTotal = matchedPrimaryClient.optLong("totalGB", 0L)
        val total = when {
            statTotal > 0L -> statTotal
            clientTotal > 0L -> clientTotal
            statTotal == 0L -> 0L
            else -> 0L
        }

        val statExpiry = effectiveStat?.optLong("expiryTime", 0L) ?: 0L
        val clientExpiry = matchedPrimaryClient.optLong("expiryTime", 0L)
        val inboundExpiry = matchedPrimaryInbound.optLong("expiryTime", 0L)
        val expiryTime = when {
            statExpiry > 0L -> statExpiry
            clientExpiry > 0L -> clientExpiry
            inboundExpiry > 0L -> inboundExpiry
            else -> 0L
        }

        val nodes = mutableListOf<ProxyNode>()
        matchingSubClients.forEach { (inbound, client) ->
            val inbEnable = inbound.optBoolean("enable", true)
            val cliEnable = if (client.has("enable")) client.optBoolean("enable", true) else true
            if (inbEnable && cliEnable) {
                nodes.addAll(buildNodesFromInbound(inbound, client, panelHost))
            }
        }

        val displayEmail = matchedPrimaryClient.optString("email").ifBlank { trimmedEmail }
        val status = SubscriptionStatus(
            source = AccountSource.PANEL_LOGIN,
            title = displayEmail,
            email = displayEmail,
            subId = trimmedSubId,
            uploadBytes = up,
            downloadBytes = down,
            totalBytes = total,
            expiryTimeMs = expiryTime,
            enabled = isEnabled,
            lastUpdatedMs = System.currentTimeMillis()
        )

        return PanelSyncResult.Success(status = status, nodes = nodes)
    }

    private fun findClientStat(clientStats: JSONArray?, email: String): JSONObject? {
        if (clientStats == null) return null
        for (i in 0 until clientStats.length()) {
            val obj = clientStats.optJSONObject(i) ?: continue
            if (obj.optString("email", "").trim().equals(email, ignoreCase = true)) {
                return obj
            }
        }
        return null
    }

    private fun buildNodesFromInbound(
        inbound: JSONObject,
        client: JSONObject,
        panelHost: String
    ): List<ProxyNode> {
        val protoRaw = inbound.optString("protocol", "").lowercase()
        val protocol = when (protoRaw) {
            "vless" -> ProxyProtocol.VLESS
            "vmess" -> ProxyProtocol.VMESS
            "trojan" -> ProxyProtocol.TROJAN
            "shadowsocks" -> ProxyProtocol.SHADOWSOCKS
            else -> return emptyList()
        }

        val remark = inbound.optString("remark").ifBlank {
            client.optString("email").ifBlank { protocol.displayName }
        }
        val basePort = inbound.optInt("port", 443)
        val settingsJson = runCatching { JSONObject(inbound.optString("settings", "{}")) }.getOrElse { JSONObject() }
        val streamJson = runCatching { JSONObject(inbound.optString("streamSettings", "{}")) }.getOrElse { JSONObject() }

        val network = streamJson.optString("network", "tcp").lowercase().ifBlank { "tcp" }
        val baseSecurity = streamJson.optString("security", "none").lowercase().ifBlank { "none" }

        // Extract transport specifics
        var hostHeader: String? = null
        var path: String? = null
        var xhttpMode: String? = null
        var xhttpExtra: String? = null

        when (network) {
            "ws" -> {
                val ws = streamJson.optJSONObject("wsSettings")
                path = ws?.optString("path")?.takeIf { it.isNotBlank() } ?: "/"
                hostHeader = ws?.optString("host")?.takeIf { it.isNotBlank() }
                    ?: ws?.optJSONObject("headers")?.optString("Host")?.takeIf { it.isNotBlank() }
            }
            "grpc" -> {
                val grpc = streamJson.optJSONObject("grpcSettings")
                path = grpc?.optString("serviceName")?.takeIf { it.isNotBlank() }
                hostHeader = grpc?.optString("authority")?.takeIf { it.isNotBlank() }
            }
            "xhttp", "splithttp" -> {
                val xh = streamJson.optJSONObject("xhttpSettings") ?: streamJson.optJSONObject("splithttpSettings")
                path = xh?.optString("path")?.takeIf { it.isNotBlank() } ?: "/"
                hostHeader = xh?.optString("host")?.takeIf { it.isNotBlank() }
                    ?: xh?.optJSONObject("headers")?.optString("Host")?.takeIf { it.isNotBlank() }
                xhttpMode = xh?.optString("mode")?.takeIf { it.isNotBlank() }
                xhttpExtra = xh?.optJSONObject("extra")?.toString()
            }
            "httpupgrade" -> {
                val hu = streamJson.optJSONObject("httpupgradeSettings")
                path = hu?.optString("path")?.takeIf { it.isNotBlank() } ?: "/"
                hostHeader = hu?.optString("host")?.takeIf { it.isNotBlank() }
                    ?: hu?.optJSONObject("headers")?.optString("Host")?.takeIf { it.isNotBlank() }
            }
            "h2", "http" -> {
                val httpSet = streamJson.optJSONObject("httpSettings")
                path = httpSet?.optString("path")?.takeIf { it.isNotBlank() } ?: "/"
                hostHeader = httpSet?.optJSONArray("host")?.optString(0)?.takeIf { it.isNotBlank() }
            }
        }

        // Extract TLS / Reality details
        var sni = ""
        var fingerprint = "chrome"
        var alpn: String? = null
        var allowInsecure = false
        var publicKey: String? = null
        var shortId: String? = null

        if (baseSecurity == "reality") {
            val reality = streamJson.optJSONObject("realitySettings")
            val rSettings = reality?.optJSONObject("settings")
            sni = reality?.optJSONArray("serverNames")?.optString(0).orEmpty()
            fingerprint = rSettings?.optString("fingerprint")?.takeIf { it.isNotBlank() } ?: "chrome"
            publicKey = rSettings?.optString("publicKey")?.takeIf { it.isNotBlank() }
                ?: reality?.optString("publicKey")?.takeIf { it.isNotBlank() }
            shortId = reality?.optJSONArray("shortIds")?.optString(0)?.takeIf { it.isNotBlank() }
        } else if (baseSecurity == "tls") {
            val tls = streamJson.optJSONObject("tlsSettings")
            val tSettings = tls?.optJSONObject("settings")
            sni = tls?.optString("serverName").orEmpty()
            allowInsecure = tSettings?.optBoolean("allowInsecure", false)
                ?: tls?.optBoolean("allowInsecure", false) ?: false
            fingerprint = tSettings?.optString("fingerprint")?.takeIf { it.isNotBlank() }
                ?: tls?.optString("fingerprint")?.takeIf { it.isNotBlank() } ?: "chrome"
            val alpnArray = tls?.optJSONArray("alpn")
            if (alpnArray != null && alpnArray.length() > 0) {
                alpn = (0 until alpnArray.length()).mapNotNull { alpnArray.optString(it).takeIf { s -> s.isNotBlank() } }
                    .joinToString(",")
            }
        }

        val uuid = client.optString("id", "")
        val password = client.optString("password", "").ifBlank { settingsJson.optString("password", "") }
        val flow = client.optString("flow").takeIf { it.isNotBlank() }
        val method = client.optString("method").ifBlank {
            settingsJson.optString("method", "chacha20-ietf-poly1305")
        }

        // Support 3X-UI externalProxy destinations if configured, else fallback to panelHost:basePort
        val externalProxies = streamJson.optJSONArray("externalProxy")
        val endpoints = mutableListOf<ExternalEndpoint>()
        if (externalProxies != null && externalProxies.length() > 0) {
            for (k in 0 until externalProxies.length()) {
                val ep = externalProxies.optJSONObject(k) ?: continue
                val dest = ep.optString("dest").trim()
                if (dest.isNotEmpty()) {
                    val rawEpPort = ep.optInt("port", 0)
                    val epPort = if (rawEpPort > 0) rawEpPort else basePort
                    val epRemark = ep.optString("remark").ifBlank { remark }
                    val forceTls = ep.optString("forceTls", "same").lowercase()
                    val epSec = when (forceTls) {
                        "tls" -> "tls"
                        "none" -> "none"
                        else -> baseSecurity
                    }
                    endpoints.add(ExternalEndpoint(dest, epPort, epRemark, epSec))
                }
            }
        }
        if (endpoints.isEmpty() && panelHost.isNotBlank()) {
            endpoints.add(ExternalEndpoint(panelHost, basePort, remark, baseSecurity))
        }

        return endpoints.map { ep ->
            val nodeName = ep.remark
            val effectiveSni = if (ep.security == "tls" || ep.security == "reality") {
                sni.ifBlank { hostHeader ?: ep.destHost }
            } else {
                sni
            }
            val rawUri = buildShareUri(
                protocol = protocol,
                name = nodeName,
                host = ep.destHost,
                port = ep.destPort,
                uuid = uuid,
                password = password,
                method = method,
                security = ep.security,
                network = network,
                sni = effectiveSni,
                flow = flow,
                hostHeader = hostHeader,
                path = path,
                publicKey = publicKey,
                shortId = shortId,
                fingerprint = fingerprint,
                alpn = alpn
            )

            ProxyNode(
                id = UUID.randomUUID().toString(),
                name = nodeName,
                protocol = protocol,
                server = ep.destHost,
                port = ep.destPort,
                uuid = uuid,
                password = password,
                method = method,
                security = ep.security,
                sni = effectiveSni,
                flow = flow,
                network = network,
                host = hostHeader,
                path = path,
                xhttpMode = xhttpMode,
                xhttpExtra = xhttpExtra,
                publicKey = publicKey,
                shortId = shortId,
                fingerprint = fingerprint,
                alpn = alpn,
                allowInsecure = allowInsecure,
                rawUri = rawUri,
                origin = NodeOrigin.PANEL_ACCOUNT,
                flag = ProxyConfigParser.detectFlag(nodeName, ep.destHost)
            )
        }
    }

    private data class ExternalEndpoint(
        val destHost: String,
        val destPort: Int,
        val remark: String,
        val security: String
    )

    private fun buildShareUri(
        protocol: ProxyProtocol,
        name: String,
        host: String,
        port: Int,
        uuid: String,
        password: String,
        method: String,
        security: String,
        network: String,
        sni: String,
        flow: String?,
        hostHeader: String?,
        path: String?,
        publicKey: String?,
        shortId: String?,
        fingerprint: String,
        alpn: String?
    ): String {
        val encName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return when (protocol) {
            ProxyProtocol.VLESS -> {
                val params = mutableListOf("type=$network", "security=$security")
                if (sni.isNotBlank()) params.add("sni=$sni")
                if (!flow.isNullOrBlank()) params.add("flow=$flow")
                if (!hostHeader.isNullOrBlank()) params.add("host=${URLEncoder.encode(hostHeader, "UTF-8")}")
                if (!path.isNullOrBlank()) params.add("path=${URLEncoder.encode(path, "UTF-8")}")
                if (!publicKey.isNullOrBlank()) params.add("pbk=$publicKey")
                if (!shortId.isNullOrBlank()) params.add("sid=$shortId")
                if (fingerprint.isNotBlank()) params.add("fp=$fingerprint")
                if (!alpn.isNullOrBlank()) params.add("alpn=${URLEncoder.encode(alpn, "UTF-8")}")
                "vless://$uuid@$host:$port?${params.joinToString("&")}#$encName"
            }
            ProxyProtocol.TROJAN -> {
                val params = mutableListOf("type=$network", "security=$security")
                if (sni.isNotBlank()) params.add("sni=$sni")
                if (!hostHeader.isNullOrBlank()) params.add("host=${URLEncoder.encode(hostHeader, "UTF-8")}")
                if (!path.isNullOrBlank()) params.add("path=${URLEncoder.encode(path, "UTF-8")}")
                "trojan://$password@$host:$port?${params.joinToString("&")}#$encName"
            }
            ProxyProtocol.VMESS -> {
                val vmessObj = JSONObject().apply {
                    put("v", "2")
                    put("ps", name)
                    put("add", host)
                    put("port", port)
                    put("id", uuid)
                    put("aid", 0)
                    put("net", network)
                    put("type", "none")
                    put("host", hostHeader.orEmpty())
                    put("path", path.orEmpty())
                    put("tls", if (security == "tls") "tls" else "")
                    put("sni", sni)
                }
                val b64 = android.util.Base64.encodeToString(
                    vmessObj.toString().toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                "vmess://$b64"
            }
            ProxyProtocol.SHADOWSOCKS -> {
                val userPass = "$method:$password"
                val b64 = android.util.Base64.encodeToString(
                    userPass.toByteArray(Charsets.UTF_8),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                )
                "ss://$b64@$host:$port#$encName"
            }
            else -> "$host:$port"
        }
    }
}
