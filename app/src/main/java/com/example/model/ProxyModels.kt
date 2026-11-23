package com.example.model

import java.util.UUID
import kotlin.math.ceil

enum class ProxyProtocol(val displayName: String, val badgeColorHex: Long) {
    VLESS("VLESS", 0xFF8A2BE2),
    VMESS("VMess", 0xFF38BDF8),
    TROJAN("Trojan", 0xFF10B981),
    SHADOWSOCKS("Shadowsocks", 0xFFF59E0B),
    SOCKS("SOCKS5", 0xFF818CF8),
    HTTP("HTTP Proxy", 0xFF60A5FA),
    WIREGUARD("WireGuard", 0xFFEC4899),
    HYSTERIA2("Hysteria 2", 0xFFDA70D6),
    CUSTOM_JSON("Xray JSON", 0xFFA855F7);

    companion object {
        fun fromScheme(scheme: String): ProxyProtocol {
            return when (scheme.lowercase()) {
                "vless" -> VLESS
                "vmess", "v2ray" -> VMESS
                "trojan" -> TROJAN
                "ss", "shadowsocks" -> SHADOWSOCKS
                "socks", "socks5" -> SOCKS
                "http", "https" -> HTTP
                "wireguard", "wg" -> WIREGUARD
                "hysteria2", "hy2" -> HYSTERIA2
                "json", "xray" -> CUSTOM_JSON
                else -> VLESS
            }
        }
    }
}

enum class NodeOrigin {
    MANUAL,
    SUBSCRIPTION,
    PANEL_ACCOUNT
}

enum class ConnectionMode(val displayName: String, val shortLabel: String) {
    VPN("VPN Mode (تونل سیستم)", "VPN TUN"),
    PROXY("Proxy Mode (SOCKS / HTTP)", "Proxy Only");

    companion object {
        fun fromName(name: String?): ConnectionMode {
            return entries.firstOrNull { it.name == name } ?: VPN
        }
    }
}

data class ProxyNode(
    val id: String,
    val name: String,
    val protocol: ProxyProtocol,
    val server: String,
    val port: Int,
    val uuid: String = "",
    val username: String = "",
    val password: String = "",
    val method: String = "chacha20-ietf-poly1305",
    val alterId: Int = 0,
    val security: String = "tls", // reality, tls, none, auto
    val sni: String = "",
    val flow: String? = null,
    val network: String = "tcp", // tcp, ws, grpc, xhttp, splithttp, httpupgrade, h2
    val host: String? = null,
    val path: String? = null,
    val xhttpMode: String? = null,
    val xhttpExtra: String? = null,
    val publicKey: String? = null, // Reality pbk or WireGuard peer public key
    val shortId: String? = null,
    val fingerprint: String? = "chrome",
    val alpn: String? = null,
    val allowInsecure: Boolean = false,
    val obfsType: String? = null,
    val obfsPassword: String? = null,
    val wgAddress: String? = null,
    val wgReserved: String? = null,
    val wgMtu: Int = 1420,
    val rawUri: String = "",
    val rawXrayJson: String? = null,
    val origin: NodeOrigin = NodeOrigin.MANUAL,
    val subscriptionId: String? = null,
    val flag: String = "🌐",
    var pingMs: Long? = null, // null = not tested, -1 = timeout/error, >0 = ms
    var isTesting: Boolean = false
)

enum class AccountSource(val badgeLabel: String) {
    PANEL_LOGIN("اکانت کاربری BND"),
    SUBSCRIPTION_URL("لینک اشتراک")
}

data class SubscriptionStatus(
    val id: String = UUID.randomUUID().toString(),
    val source: AccountSource = AccountSource.SUBSCRIPTION_URL,
    val title: String = "",
    val email: String = "",
    val subId: String = "",
    val subscriptionUrl: String = "",
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L, // 0 == Unlimited
    val expiryTimeMs: Long = 0L, // <= 0 == Unlimited
    val enabled: Boolean = true,
    val lastUpdatedMs: Long = 0L
) {
    val usedBytes: Long
        get() = uploadBytes.coerceAtLeast(0L) + downloadBytes.coerceAtLeast(0L)

    val isUnlimitedTraffic: Boolean
        get() = totalBytes <= 0L

    val remainingBytes: Long
        get() = if (isUnlimitedTraffic) Long.MAX_VALUE else (totalBytes - usedBytes).coerceAtLeast(0L)

    val isQuotaExhausted: Boolean
        get() = totalBytes > 0L && (totalBytes - usedBytes) <= 0L

    val isUnlimitedTime: Boolean
        get() = expiryTimeMs <= 0L

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiryTimeMs > 0L && nowMs >= expiryTimeMs

    fun isBlocked(nowMs: Long = System.currentTimeMillis()): Boolean =
        !enabled || isQuotaExhausted || isExpired(nowMs)

    fun remainingDays(nowMs: Long = System.currentTimeMillis()): Int? {
        if (expiryTimeMs <= 0L) return null
        val diff = expiryTimeMs - nowMs
        if (diff <= 0L) return 0
        return ceil(diff / 86_400_000.0).toInt()
    }

    val usageFraction: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

data class NetworkTrafficStats(
    val downloadSpeedBytes: Long = 0,
    val uploadSpeedBytes: Long = 0,
    val totalDownloadBytes: Long = 0,
    val totalUploadBytes: Long = 0,
    val connectedDurationSeconds: Long = 0
)

data class StorePlan(
    val id: String,
    val title: String,
    val durationText: String,
    val trafficText: String,
    val priceTomans: String,
    val priceUsd: String,
    val isPopular: Boolean = false,
    val features: List<String>,
    val badge: String? = null
)
