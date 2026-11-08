package com.example.core

import com.example.model.ProxyNode
import com.example.model.ProxyProtocol
import org.json.JSONArray
import org.json.JSONObject

object UniversalCoreConfigGenerator {

    /**
     * Generates a fully compliant Sing-box core configuration JSON
     */
    fun generateSingBoxConfig(node: ProxyNode, bypassIran: Boolean = true, dnsServer: String = "1.1.1.1"): String {
        val root = JSONObject()

        // Log config
        val log = JSONObject().apply {
            put("disabled", false)
            put("level", "info")
            put("timestamp", true)
        }
        root.put("log", log)

        // DNS config
        val dns = JSONObject().apply {
            val servers = JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "remote-dns")
                    put("address", "tcp://$dnsServer")
                    put("detour", "proxy")
                })
                put(JSONObject().apply {
                    put("tag", "local-dns")
                    put("address", "https://1.1.1.1/dns-query")
                    put("detour", "direct")
                })
            }
            put("servers", servers)
            put("final", "remote-dns")
        }
        root.put("dns", dns)

        // Inbound (TUN interface)
        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", "tun0")
                put("inet4_address", "172.19.0.1/30")
                put("mtu", 9000)
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "system")
                put("sniff", true)
            })
        }
        root.put("inbounds", inbounds)

        // Outbound for current node
        val nodeOutbound = createSingBoxOutbound(node)

        val outbounds = JSONArray().apply {
            put(nodeOutbound)
            put(JSONObject().apply {
                put("type", "direct")
                put("tag", "direct")
            })
            put(JSONObject().apply {
                put("type", "block")
                put("tag", "block")
            })
            put(JSONObject().apply {
                put("type", "dns")
                put("tag", "dns-out")
            })
        }
        root.put("outbounds", outbounds)

        // Routing Rules
        val route = JSONObject().apply {
            val rules = JSONArray().apply {
                put(JSONObject().apply {
                    put("protocol", "dns")
                    put("outbound", "dns-out")
                })
                if (bypassIran) {
                    put(JSONObject().apply {
                        val geoip = JSONArray().apply { put("ir") }
                        val geosite = JSONArray().apply { put("ir") }
                        put("geoip", geoip)
                        put("geosite", geosite)
                        put("outbound", "direct")
                    })
                }
            }
            put("rules", rules)
            put("auto_detect_interface", true)
            put("final", "proxy")
        }
        root.put("route", route)

        return root.toString(2)
    }

    private fun createSingBoxOutbound(node: ProxyNode): JSONObject {
        val out = JSONObject()
        out.put("tag", "proxy")

        when (node.protocol) {
            ProxyProtocol.VLESS -> {
                out.put("type", "vless")
                out.put("server", node.server)
                out.put("server_port", node.port)
                out.put("uuid", node.uuid)
                node.flow?.let { out.put("flow", it) }

                val tls = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", node.sni.ifEmpty { node.server })
                    put("insecure", false)
                    if (node.security == "reality") {
                        val reality = JSONObject().apply {
                            put("enabled", true)
                            put("public_key", node.publicKey.orEmpty())
                            put("short_id", node.shortId.orEmpty())
                        }
                        put("reality", reality)
                    }
                    val utls = JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", node.fingerprint ?: "chrome")
                    }
                    put("utls", utls)
                }
                out.put("tls", tls)

                if (node.network == "grpc") {
                    out.put("transport", JSONObject().apply {
                        put("type", "grpc")
                        put("service_name", node.path.orEmpty())
                    })
                } else if (node.network == "ws") {
                    out.put("transport", JSONObject().apply {
                        put("type", "ws")
                        put("path", node.path ?: "/")
                    })
                }
            }

            ProxyProtocol.HYSTERIA2 -> {
                out.put("type", "hysteria2")
                out.put("server", node.server)
                out.put("server_port", node.port)
                out.put("password", node.uuid)
                val tls = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", node.sni.ifEmpty { node.server })
                    put("insecure", node.security == "insecure")
                }
                out.put("tls", tls)
            }

            ProxyProtocol.TUIC -> {
                out.put("type", "tuic")
                out.put("server", node.server)
                out.put("server_port", node.port)
                out.put("uuid", node.uuid)
                out.put("congestion_controller", "bbr")
                val tls = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", node.sni.ifEmpty { node.server })
                    val alpn = JSONArray().apply { put("h3") }
                    put("alpn", alpn)
                }
                out.put("tls", tls)
            }

            ProxyProtocol.AMNEZIA_WG -> {
                out.put("type", "wireguard")
                out.put("server", node.server)
                out.put("server_port", node.port)
                node.amneziaParams?.let { ap ->
                    // AmneziaWG obfuscation extensions
                    val awg = JSONObject().apply {
                        put("jc", ap.jc)
                        put("jmin", ap.jmin)
                        put("jmax", ap.jmax)
                        put("s1", ap.s1)
                        put("s2", ap.s2)
                        put("h1", ap.h1)
                        put("h2", ap.h2)
                        put("h3", ap.h3)
                        put("h4", ap.h4)
                    }
                    out.put("amnezia_wg", awg)
                }
            }

            ProxyProtocol.TROJAN -> {
                out.put("type", "trojan")
                out.put("server", node.server)
                out.put("server_port", node.port)
                out.put("password", node.uuid)
                val tls = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", node.sni.ifEmpty { node.server })
                }
                out.put("tls", tls)
            }

            ProxyProtocol.VMESS -> {
                out.put("type", "vmess")
                out.put("server", node.server)
                out.put("server_port", node.port)
                out.put("uuid", node.uuid)
                out.put("security", "auto")
            }

            ProxyProtocol.SHADOWSOCKS -> {
                out.put("type", "shadowsocks")
                out.put("server", node.server)
                out.put("server_port", node.port)
                out.put("method", "chacha20-ietf-poly1305")
                out.put("password", node.uuid)
            }

            ProxyProtocol.OPENVPN -> {
                out.put("type", "openvpn")
                out.put("server", node.server)
                out.put("server_port", node.port)
            }
        }
        return out
    }

    /**
     * Generates Amnezia WireGuard INI config representation
     */
    fun generateAmneziaWgConfig(node: ProxyNode): String {
        val ap = node.amneziaParams ?: return ""
        return """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.7.0.2/24
            DNS = 1.1.1.1, 8.8.8.8
            Jc = ${ap.jc}
            Jmin = ${ap.jmin}
            Jmax = ${ap.jmax}
            S1 = ${ap.s1}
            S2 = ${ap.s2}
            H1 = ${ap.h1}
            H2 = ${ap.h2}
            H3 = ${ap.h3}
            H4 = ${ap.h4}

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = ${node.server}:${node.port}
            AllowedIPs = 0.0.0.0/0
            PersistentKeepalive = 25
        """.trimIndent()
    }

    /**
     * Generates OpenVPN profile
     */
    fun generateOpenVpnProfile(node: ProxyNode): String {
        return node.openVpnConfig ?: """
            client
            dev tun
            proto tcp
            remote ${node.server} ${node.port}
            resolv-retry infinite
            nobind
            persist-key
            persist-tun
            remote-cert-tls server
            cipher AES-256-GCM
            auth SHA256
            verb 3
            <ca>
            -----BEGIN CERTIFICATE-----
            MIIBszCCARqgAwIBAgIU...V2reyBND-CA...
            -----END CERTIFICATE-----
            </ca>
        """.trimIndent()
    }
}
