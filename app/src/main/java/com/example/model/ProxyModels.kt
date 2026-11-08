package com.example.model

enum class ProxyProtocol(val displayName: String, val badgeColorHex: Long) {
    VLESS("VLESS Reality", 0xFF8A2BE2),
    HYSTERIA2("Hysteria 2", 0xFFDA70D6),
    AMNEZIA_WG("AmneziaWG", 0xFF00E5FF),
    TUIC("TUIC v5", 0xFF818CF8),
    VMESS("VMess", 0xFF38BDF8),
    TROJAN("Trojan", 0xFF10B981),
    SHADOWSOCKS("Shadowsocks", 0xFFF59E0B),
    OPENVPN("OpenVPN", 0xFFEC4899);

    companion object {
        fun fromScheme(scheme: String): ProxyProtocol {
            return when (scheme.lowercase()) {
                "vless" -> VLESS
                "hysteria2", "hy2" -> HYSTERIA2
                "amnezia", "awg" -> AMNEZIA_WG
                "tuic" -> TUIC
                "vmess", "v2ray" -> VMESS
                "trojan" -> TROJAN
                "ss" -> SHADOWSOCKS
                "openvpn", "ovpn" -> OPENVPN
                else -> VLESS
            }
        }
    }
}

data class AmneziaParams(
    val jc: Int = 4,
    val jmin: Int = 40,
    val jmax: Int = 70,
    val s1: Int = 32,
    val s2: Int = 48,
    val h1: String = "1",
    val h2: String = "2",
    val h3: String = "3",
    val h4: String = "4"
)

data class ProxyNode(
    val id: String,
    val name: String,
    val protocol: ProxyProtocol,
    val server: String,
    val port: Int,
    val uuid: String = "",
    val security: String = "tls", // reality, tls, none
    val sni: String = "",
    val flow: String? = null,
    val network: String = "tcp", // grpc, ws, tcp, kcp, udp
    val path: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val fingerprint: String? = "chrome",
    val amneziaParams: AmneziaParams? = null,
    val openVpnConfig: String? = null,
    val openVpnUsername: String? = null,
    val openVpnPassword: String? = null,
    val openVpnRequiresAuth: Boolean = false,
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
