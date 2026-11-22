package com.example.model

enum class ProxyProtocol(val displayName: String, val badgeColorHex: Long) {
    VLESS("VLESS", 0xFF8A2BE2),
    VMESS("VMess", 0xFF38BDF8),
    TROJAN("Trojan", 0xFF10B981),
    SHADOWSOCKS("Shadowsocks", 0xFFF59E0B),
    SOCKS("SOCKS5", 0xFF818CF8),
    HTTP("HTTP Proxy", 0xFF60A5FA),
    WIREGUARD("WireGuard", 0xFFEC4899),
    HYSTERIA2("Hysteria 2", 0xFFDA70D6);

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
                else -> VLESS
            }
        }
    }
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
    val flag: String = "🌐",
    var pingMs: Long? = null, // null = not tested, -1 = timeout/error, >0 = ms
    var isTesting: Boolean = false
)

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
