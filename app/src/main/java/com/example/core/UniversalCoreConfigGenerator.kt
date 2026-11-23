package com.example.core

import android.content.Context
import android.system.Os
import com.example.data.VpnRepository
import com.example.model.ConnectionMode
import com.example.model.ProxyNode
import com.example.model.ProxyProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object UniversalCoreConfigGenerator {

    data class XrayAssetAvailability(
        val dir: File,
        val hasGeosite: Boolean,
        val hasGeoip: Boolean
    )

    fun prepareAssetsDir(context: Context): XrayAssetAvailability {
        val dir = File(context.filesDir, "xray_assets").apply { mkdirs() }
        copyAssetIfBundled(context, "geosite.dat", File(dir, "geosite.dat"))
        copyAssetIfBundled(context, "geoip.dat", File(dir, "geoip.dat"))
        try {
            Os.setenv("XRAY_LOCATION_ASSET", dir.absolutePath, true)
        } catch (_: Throwable) {
        }
        val hasGeosite = File(dir, "geosite.dat").let { it.exists() && it.length() > 0L }
        val hasGeoip = File(dir, "geoip.dat").let { it.exists() && it.length() > 0L }
        return XrayAssetAvailability(dir = dir, hasGeosite = hasGeosite, hasGeoip = hasGeoip)
    }

    private fun copyAssetIfBundled(context: Context, assetName: String, target: File) {
        if (target.exists() && target.length() > 0L) return
        try {
            context.assets.open(assetName).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
            // Bundled asset is optional; missing .dat files are handled gracefully at runtime
        }
    }

    /**
     * Sanitizes ONLY the temporary runtime JSON when geosite.dat or geoip.dat is missing from
     * xray_assets, leaving the stored user config (ProxyNode.rawXrayJson) completely untouched.
     */
    fun sanitizeRuntimeGeodata(
        configJson: String,
        hasGeosite: Boolean,
        hasGeoip: Boolean
    ): String {
        if (hasGeosite && hasGeoip) return configJson
        return try {
            val root = JSONObject(configJson)

            // 1. Clean routing.rules domain/ip arrays
            val routing = root.optJSONObject("routing")
            val rules = routing?.optJSONArray("rules")
            if (rules != null) {
                val cleanedRules = JSONArray()
                for (i in 0 until rules.length()) {
                    val rule = rules.optJSONObject(i) ?: continue
                    var dropRule = false

                    if (!hasGeosite && rule.has("domain")) {
                        val domainArr = rule.optJSONArray("domain")
                        if (domainArr != null) {
                            val kept = JSONArray()
                            var removedAny = false
                            for (j in 0 until domainArr.length()) {
                                val entry = domainArr.optString(j)
                                if (isUnsupportedGeositeEntry(entry)) {
                                    removedAny = true
                                } else {
                                    kept.put(entry)
                                }
                            }
                            if (removedAny) {
                                if (kept.length() > 0) {
                                    rule.put("domain", kept)
                                } else {
                                    dropRule = true
                                }
                            }
                        }
                    }

                    if (!dropRule && !hasGeoip && rule.has("ip")) {
                        val ipArr = rule.optJSONArray("ip")
                        if (ipArr != null) {
                            val kept = JSONArray()
                            var removedAny = false
                            for (j in 0 until ipArr.length()) {
                                val entry = ipArr.optString(j)
                                if (isUnsupportedGeoipEntry(entry)) {
                                    removedAny = true
                                } else {
                                    kept.put(entry)
                                }
                            }
                            if (removedAny) {
                                if (kept.length() > 0) {
                                    rule.put("ip", kept)
                                } else {
                                    dropRule = true
                                }
                            }
                        }
                    }

                    if (!dropRule) {
                        cleanedRules.put(rule)
                    }
                }
                routing.put("rules", cleanedRules)
            }

            // 2. Clean dns.servers[*].domains (and expectIPs) if custom JSON references geosite/geoip
            val dns = root.optJSONObject("dns")
            val servers = dns?.optJSONArray("servers")
            if (servers != null) {
                val candidates = mutableListOf<Pair<Any, Boolean>>()
                for (i in 0 until servers.length()) {
                    val item = servers.get(i)
                    if (item is JSONObject) {
                        var domainBecameEmpty = false
                        if (!hasGeosite && item.has("domains")) {
                            val domainsArr = item.optJSONArray("domains")
                            if (domainsArr != null) {
                                val kept = JSONArray()
                                var removedAny = false
                                for (j in 0 until domainsArr.length()) {
                                    val entry = domainsArr.optString(j)
                                    if (isUnsupportedGeositeEntry(entry)) {
                                        removedAny = true
                                    } else {
                                        kept.put(entry)
                                    }
                                }
                                if (removedAny) {
                                    if (kept.length() > 0) {
                                        item.put("domains", kept)
                                    } else {
                                        item.remove("domains")
                                        domainBecameEmpty = true
                                    }
                                }
                            }
                        }
                        if (!hasGeoip && item.has("expectIPs")) {
                            val expArr = item.optJSONArray("expectIPs")
                            if (expArr != null) {
                                val kept = JSONArray()
                                var removedAny = false
                                for (j in 0 until expArr.length()) {
                                    val entry = expArr.optString(j)
                                    if (isUnsupportedGeoipEntry(entry)) {
                                        removedAny = true
                                    } else {
                                        kept.put(entry)
                                    }
                                }
                                if (removedAny) {
                                    if (kept.length() > 0) item.put("expectIPs", kept) else item.remove("expectIPs")
                                }
                            }
                        }
                        candidates.add(item to domainBecameEmpty)
                    } else {
                        candidates.add(item to false)
                    }
                }
                val hasUnconditionalServer = candidates.any { !it.second }
                val finalServers = JSONArray()
                candidates.forEach { (srv, domainBecameEmpty) ->
                    if (!domainBecameEmpty || !hasUnconditionalServer) {
                        finalServers.put(srv)
                    }
                }
                dns.put("servers", finalServers)
            }

            root.toString(2)
        } catch (_: Exception) {
            configJson
        }
    }

    private fun isUnsupportedGeositeEntry(entry: String): Boolean {
        val trimmed = entry.trim()
        return trimmed.startsWith("geosite:", ignoreCase = true) ||
            trimmed.startsWith("ext:geosite", ignoreCase = true)
    }

    private fun isUnsupportedGeoipEntry(entry: String): Boolean {
        val trimmed = entry.trim()
        return trimmed.startsWith("geoip:", ignoreCase = true) ||
            trimmed.startsWith("ext:geoip", ignoreCase = true)
    }

    private val IRAN_DOMAINS = listOf(
        "domain:ir",
        "domain:shaparak.ir",
        "domain:digikala.com",
        "domain:snapp.ir",
        "domain:divar.ir",
        "domain:aparat.com",
        "domain:torob.com",
        "domain:filimo.com",
        "domain:telewebion.com",
        "domain:bale.ai",
        "domain:eitaa.com",
        "domain:rubika.ir"
    )

    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "127.0.0.0/8",
        "100.64.0.0/10",
        "169.254.0.0/16",
        "224.0.0.0/4",
        "255.255.255.255/32",
        "::1/128",
        "fc00::/7",
        "fe80::/10"
    )

    /**
     * Generates a complete Xray-core configuration JSON respecting:
     * - Connection Mode (VPN TUN + local SOCKS/HTTP vs Proxy-only local SOCKS/HTTP)
     * - Custom SOCKS5 & HTTP inbound ports
     * - Selected DNS server + FakeDNS / Sniffing
     * - Bypass Iran LAN & domestic domains (self-contained rules with zero external .dat files required)
     * - TLS Fragment outbound chaining (`dialerProxy = "fragment"`)
     * - Full Xray protocols: VLESS (Reality/TLS/gRPC/WS/XHTTP/XTLS), VMess, Trojan, Shadowsocks,
     *   SOCKS, HTTP, WireGuard, Hysteria2
     */
    fun generateXrayConfig(
        node: ProxyNode,
        connectionMode: ConnectionMode = ConnectionMode.VPN,
        socksPort: Int = VpnRepository.DEFAULT_SOCKS_PORT,
        httpPort: Int = VpnRepository.DEFAULT_HTTP_PORT,
        bypassIran: Boolean = true,
        dnsServer: String = "1.1.1.1",
        enableTlsFragment: Boolean = false,
        enableFakeDns: Boolean = false
    ): String {
        if (node.protocol == ProxyProtocol.CUSTOM_JSON && !node.rawXrayJson.isNullOrBlank()) {
            return generateFromCustomXrayJson(
                rawJson = node.rawXrayJson,
                node = node,
                connectionMode = connectionMode,
                socksPort = socksPort,
                httpPort = httpPort,
                bypassIran = bypassIran,
                dnsServer = dnsServer,
                enableFakeDns = enableFakeDns
            )
        }

        val root = JSONObject()

        root.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        // FakeDNS IP pool when enabled
        if (enableFakeDns) {
            root.put("fakedns", JSONArray().apply {
                put(JSONObject().apply {
                    put("ipPool", "198.18.0.0/15")
                })
            })
        }

        // DNS configuration:
        // - Resolve the proxy server's own hostname via localhost so there is never a DNS loop!
        // - Optionally resolve Iranian domains via localhost when bypassIran is active.
        // - Route all other queries through the remote encrypted/public DNS via the proxy outbound.
        root.put("dns", buildDnsConfig(node, dnsServer, bypassIran, enableFakeDns))

        // Inbounds
        val sniffingDestOverride = JSONArray().apply {
            put("http")
            put("tls")
            put("quic")
            if (enableFakeDns) put("fakedns")
        }
        val sniffingConfig = JSONObject().apply {
            put("enabled", enableFakeDns || connectionMode == ConnectionMode.VPN)
            put("destOverride", sniffingDestOverride)
            put("routeOnly", false)
        }

        val inbounds = JSONArray().apply {
            if (connectionMode == ConnectionMode.VPN) {
                put(JSONObject().apply {
                    put("tag", "tun-in")
                    put("protocol", "tun")
                    put("settings", JSONObject().apply {
                        put("name", "tun0")
                        put("mtu", 1500)
                        put("gateway", JSONArray().put("172.19.0.1/30"))
                        put("userLevel", 0)
                    })
                    put("sniffing", sniffingConfig)
                })
            }

            put(JSONObject().apply {
                put("tag", "socks-in")
                put("listen", "127.0.0.1")
                put("port", socksPort)
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
                put("sniffing", sniffingConfig)
            })

            put(JSONObject().apply {
                put("tag", "http-in")
                put("listen", "127.0.0.1")
                put("port", httpPort)
                put("protocol", "http")
                put("sniffing", sniffingConfig)
            })
        }
        root.put("inbounds", inbounds)

        // Outbounds
        val useFragment = enableTlsFragment && supportsTlsFragment(node)
        val outbounds = JSONArray().apply {
            put(createProxyOutbound(node, useFragment))
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject().apply {
                    put("domainStrategy", "UseIPv4")
                })
            })
            put(JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
                put("settings", JSONObject().apply {
                    put("response", JSONObject().apply {
                        put("type", "http")
                    })
                })
            })
            put(JSONObject().apply {
                put("tag", "dns-out")
                put("protocol", "dns")
            })
            if (useFragment) {
                put(JSONObject().apply {
                    put("tag", "fragment")
                    put("protocol", "freedom")
                    put("settings", JSONObject().apply {
                        put("domainStrategy", "UseIPv4")
                        put("fragment", JSONObject().apply {
                            put("packets", "tlshello")
                            put("length", "10-20")
                            put("interval", "10-20")
                        })
                    })
                })
            }
        }
        root.put("outbounds", outbounds)

        // Routing rules
        root.put("routing", buildRoutingConfig(node, connectionMode, bypassIran))

        return root.toString(2)
    }

    private fun generateFromCustomXrayJson(
        rawJson: String,
        node: ProxyNode,
        connectionMode: ConnectionMode,
        socksPort: Int,
        httpPort: Int,
        bypassIran: Boolean,
        dnsServer: String,
        enableFakeDns: Boolean
    ): String {
        val root = JSONObject(rawJson)

        if (!root.has("log")) {
            root.put("log", JSONObject().apply {
                put("loglevel", "warning")
            })
        }

        if (enableFakeDns && !root.has("fakedns")) {
            root.put("fakedns", JSONArray().apply {
                put(JSONObject().apply {
                    put("ipPool", "198.18.0.0/15")
                })
            })
        }

        if (!root.has("dns")) {
            root.put("dns", buildDnsConfig(node, dnsServer, bypassIran, enableFakeDns))
        }

        val sniffingDestOverride = JSONArray().apply {
            put("http")
            put("tls")
            put("quic")
            if (enableFakeDns) put("fakedns")
        }
        val sniffingConfig = JSONObject().apply {
            put("enabled", enableFakeDns || connectionMode == ConnectionMode.VPN)
            put("destOverride", sniffingDestOverride)
            put("routeOnly", false)
        }

        // Replace local/TUN inbounds so Android VPN TUN and active SOCKS/HTTP ports are always bound
        val inbounds = JSONArray().apply {
            if (connectionMode == ConnectionMode.VPN) {
                put(JSONObject().apply {
                    put("tag", "tun-in")
                    put("protocol", "tun")
                    put("settings", JSONObject().apply {
                        put("name", "tun0")
                        put("mtu", 1500)
                        put("gateway", JSONArray().put("172.19.0.1/30"))
                        put("userLevel", 0)
                    })
                    put("sniffing", sniffingConfig)
                })
            }
            put(JSONObject().apply {
                put("tag", "socks-in")
                put("listen", "127.0.0.1")
                put("port", socksPort)
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
                put("sniffing", sniffingConfig)
            })
            put(JSONObject().apply {
                put("tag", "http-in")
                put("listen", "127.0.0.1")
                put("port", httpPort)
                put("protocol", "http")
                put("sniffing", sniffingConfig)
            })
        }
        root.put("inbounds", inbounds)

        // Ensure dns-out / direct outbounds exist when VPN TUN mode intercepts port 53
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
        var hasDnsOut = false
        var hasDirect = false
        for (i in 0 until outbounds.length()) {
            val tag = outbounds.optJSONObject(i)?.optString("tag")
            if (tag == "dns-out") hasDnsOut = true
            if (tag == "direct") hasDirect = true
        }
        if (!hasDirect) {
            outbounds.put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject().apply {
                    put("domainStrategy", "UseIPv4")
                })
            })
        }
        if (!hasDnsOut) {
            outbounds.put(JSONObject().apply {
                put("tag", "dns-out")
                put("protocol", "dns")
            })
        }
        root.put("outbounds", outbounds)

        if (connectionMode == ConnectionMode.VPN) {
            val routing = root.optJSONObject("routing") ?: JSONObject().apply {
                put("domainStrategy", "IPIfNonMatch")
            }
            val existingRules = routing.optJSONArray("rules") ?: JSONArray()
            val mergedRules = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "field")
                    put("inboundTag", JSONArray().put("tun-in"))
                    put("port", "53")
                    put("outboundTag", "dns-out")
                })
                for (i in 0 until existingRules.length()) {
                    put(existingRules.get(i))
                }
            }
            routing.put("rules", mergedRules)
            root.put("routing", routing)
        }

        // Remove non-standard top-level metadata fields like remark/remarks that Xray might reject
        root.remove("remarks")
        root.remove("remark")
        root.remove("name")
        root.remove("ps")

        return root.toString(2)
    }

    private fun buildDnsConfig(
        node: ProxyNode,
        dnsServer: String,
        bypassIran: Boolean,
        enableFakeDns: Boolean
    ): JSONObject {
        val cleanDns = dnsServer.ifBlank { "1.1.1.1" }
        val servers = JSONArray()

        if (enableFakeDns) {
            servers.put("fakedns")
        }

        // Primary remote DNS (routed through proxy outbound)
        servers.put(cleanDns)

        // Localhost DNS for proxy server hostname resolution & optional Iran domains
        val localDomains = mutableListOf<String>()
        if (node.server.isNotBlank() && !isIpAddress(node.server)) {
            localDomains.add("full:${node.server}")
        }
        if (bypassIran) {
            localDomains.addAll(IRAN_DOMAINS)
        }
        if (localDomains.isNotEmpty()) {
            servers.put(JSONObject().apply {
                put("address", "localhost")
                put("domains", JSONArray(localDomains))
                put("skipFallback", true)
            })
        } else {
            servers.put("localhost")
        }

        return JSONObject().apply {
            put("servers", servers)
            put("queryStrategy", "UseIPv4")
            put("disableFallbackIfMatch", true)
        }
    }

    private fun buildRoutingConfig(
        node: ProxyNode,
        connectionMode: ConnectionMode,
        bypassIran: Boolean
    ): JSONObject {
        val rules = JSONArray()

        // 1. Intercept DNS traffic arriving on TUN inbound -> Xray DNS handler
        if (connectionMode == ConnectionMode.VPN) {
            rules.put(JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().put("tun-in"))
                put("port", "53")
                put("outboundTag", "dns-out")
            })
        }

        // 2. Keep Private/LAN CIDRs direct
        rules.put(JSONObject().apply {
            put("type", "field")
            put("ip", JSONArray(PRIVATE_CIDRS))
            put("outboundTag", "direct")
        })

        // 3. Ensure proxy server's own domain/IP always bypasses routing loops if resolved externally
        if (node.server.isNotBlank() && !isIpAddress(node.server)) {
            // Note: Xray's own outbound dials bypass routing rules automatically,
            // while UDP/TCP NTP/connectivity probes remain safely routed.
        }

        // 4. Bypass Iranian domestic sites when enabled
        if (bypassIran) {
            rules.put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray(IRAN_DOMAINS))
                put("outboundTag", "direct")
            })
        }

        // 5. Default catch-all: route all TCP & UDP traffic through the selected Xray proxy outbound
        rules.put(JSONObject().apply {
            put("type", "field")
            put("network", "tcp,udp")
            put("outboundTag", "proxy")
        })

        return JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", rules)
        }
    }

    private fun supportsTlsFragment(node: ProxyNode): Boolean {
        if (node.protocol == ProxyProtocol.WIREGUARD ||
            node.protocol == ProxyProtocol.HYSTERIA2 ||
            node.protocol == ProxyProtocol.SHADOWSOCKS ||
            node.protocol == ProxyProtocol.SOCKS
        ) {
            return false
        }
        val net = node.network.lowercase()
        return node.security != "none" && (net == "tcp" || net == "raw" || net == "ws" || net == "httpupgrade" || net == "xhttp" || net == "splithttp")
    }

    private fun createProxyOutbound(node: ProxyNode, useFragment: Boolean): JSONObject {
        val out = JSONObject()
        out.put("tag", "proxy")

        when (node.protocol) {
            ProxyProtocol.VLESS -> {
                out.put("protocol", "vless")
                out.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", node.server)
                            put("port", node.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", node.uuid)
                                    put("encryption", "none")
                                    node.flow?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
                                })
                            })
                        })
                    })
                })
                out.put("streamSettings", buildStreamSettings(node, useFragment))
            }

            ProxyProtocol.VMESS -> {
                out.put("protocol", "vmess")
                out.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", node.server)
                            put("port", node.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", node.uuid)
                                    put("alterId", node.alterId.coerceAtLeast(0))
                                    put("security", node.method.ifBlank { "auto" })
                                })
                            })
                        })
                    })
                })
                out.put("streamSettings", buildStreamSettings(node, useFragment))
            }

            ProxyProtocol.TROJAN -> {
                out.put("protocol", "trojan")
                out.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", node.server)
                            put("port", node.port)
                            put("password", node.password.ifEmpty { node.uuid })
                        })
                    })
                })
                out.put("streamSettings", buildStreamSettings(node, useFragment))
            }

            ProxyProtocol.SHADOWSOCKS -> {
                out.put("protocol", "shadowsocks")
                out.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", node.server)
                            put("port", node.port)
                            put("method", node.method.ifBlank { "chacha20-ietf-poly1305" })
                            put("password", node.password.ifEmpty { node.uuid })
                        })
                    })
                })
                out.put("streamSettings", JSONObject().apply {
                    put("network", "tcp")
                })
            }

            ProxyProtocol.SOCKS -> {
                out.put("protocol", "socks")
                out.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", node.server)
                            put("port", node.port)
                            if (node.username.isNotBlank() || node.password.isNotBlank()) {
                                put("users", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("user", node.username)
                                        put("pass", node.password)
                                    })
                                })
                            }
                        })
                    })
                })
            }

            ProxyProtocol.HTTP -> {
                out.put("protocol", "http")
                out.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", node.server)
                            put("port", node.port)
                            if (node.username.isNotBlank() || node.password.isNotBlank()) {
                                put("users", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("user", node.username)
                                        put("pass", node.password)
                                    })
                                })
                            }
                        })
                    })
                })
                if (node.security == "tls") {
                    out.put("streamSettings", buildStreamSettings(node, useFragment))
                }
            }

            ProxyProtocol.WIREGUARD -> {
                out.put("protocol", "wireguard")
                val addresses = (node.wgAddress ?: "10.0.0.2/32")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val reservedBytes = (node.wgReserved ?: "")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }

                out.put("settings", JSONObject().apply {
                    put("secretKey", node.password.ifEmpty { node.uuid })
                    put("address", JSONArray(addresses))
                    put("mtu", node.wgMtu.coerceIn(576, 1500))
                    if (reservedBytes.isNotEmpty()) {
                        put("reserved", JSONArray(reservedBytes))
                    }
                    put("peers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("endpoint", "${node.server}:${node.port}")
                            put("publicKey", node.publicKey.orEmpty())
                            put("allowedIPs", JSONArray().apply {
                                put("0.0.0.0/0")
                                put("::/0")
                            })
                        })
                    })
                })
            }

            ProxyProtocol.HYSTERIA2 -> {
                out.put("protocol", "hysteria")
                out.put("settings", JSONObject().apply {
                    put("version", 2)
                    put("address", node.server)
                    put("port", node.port)
                })
                out.put("streamSettings", JSONObject().apply {
                    put("network", "hysteria")
                    put("hysteriaSettings", JSONObject().apply {
                        put("version", 2)
                        put("auth", node.password.ifEmpty { node.uuid })
                    })
                    put("security", "tls")
                    put("tlsSettings", JSONObject().apply {
                        put("serverName", node.sni.ifEmpty { node.server })
                        put("allowInsecure", node.allowInsecure || node.security == "insecure")
                        val alpnList = (node.alpn ?: "h3").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (alpnList.isNotEmpty()) {
                            put("alpn", JSONArray(alpnList))
                        }
                    })
                    if (node.obfsType?.equals("salamander", ignoreCase = true) == true && !node.obfsPassword.isNullOrBlank()) {
                        put("finalmask", JSONObject().apply {
                            put("udp", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "salamander")
                                    put("settings", JSONObject().apply {
                                        put("password", node.obfsPassword)
                                    })
                                })
                            })
                        })
                    }
                })
            }

            ProxyProtocol.CUSTOM_JSON -> {
                // Handled directly via generateFromCustomXrayJson
            }
        }

        return out
    }

    private fun buildStreamSettings(node: ProxyNode, useFragment: Boolean): JSONObject {
        val stream = JSONObject()
        val net = when (node.network.lowercase()) {
            "raw", "" -> "tcp"
            "splithttp" -> "xhttp"
            else -> node.network.lowercase()
        }
        stream.put("network", net)

        // Transport-specific settings
        when (net) {
            "ws" -> {
                stream.put("wsSettings", JSONObject().apply {
                    put("path", node.path?.ifBlank { "/" } ?: "/")
                    val hostHeader = node.host?.takeIf { it.isNotBlank() } ?: node.sni.takeIf { it.isNotBlank() }
                    if (!hostHeader.isNullOrBlank()) {
                        put("host", hostHeader)
                    }
                })
            }

            "grpc" -> {
                stream.put("grpcSettings", JSONObject().apply {
                    put("serviceName", node.path.orEmpty())
                    if (!node.host.isNullOrBlank()) {
                        put("authority", node.host)
                    }
                })
            }

            "xhttp" -> {
                stream.put("xhttpSettings", JSONObject().apply {
                    put("path", node.path?.ifBlank { "/" } ?: "/")
                    val hostHeader = node.host?.takeIf { it.isNotBlank() }
                    if (!hostHeader.isNullOrBlank()) {
                        put("host", hostHeader)
                    }
                    put("mode", node.xhttpMode?.takeIf { it.isNotBlank() } ?: "auto")
                    if (!node.xhttpExtra.isNullOrBlank()) {
                        try {
                            put("extra", JSONObject(node.xhttpExtra))
                        } catch (_: Exception) {
                        }
                    }
                })
            }

            "httpupgrade" -> {
                stream.put("httpupgradeSettings", JSONObject().apply {
                    put("path", node.path?.ifBlank { "/" } ?: "/")
                    val hostHeader = node.host?.takeIf { it.isNotBlank() } ?: node.sni.takeIf { it.isNotBlank() }
                    if (!hostHeader.isNullOrBlank()) {
                        put("host", hostHeader)
                    }
                })
            }

            "h2", "http" -> {
                stream.put("httpSettings", JSONObject().apply {
                    put("path", node.path?.ifBlank { "/" } ?: "/")
                    val hostHeader = node.host?.takeIf { it.isNotBlank() } ?: node.sni.takeIf { it.isNotBlank() }
                    if (!hostHeader.isNullOrBlank()) {
                        put("host", JSONArray().put(hostHeader))
                    }
                })
            }
        }

        // Security (TLS / Reality / None)
        when (node.security.lowercase()) {
            "reality" -> {
                stream.put("security", "reality")
                stream.put("realitySettings", JSONObject().apply {
                    put("serverName", node.sni.ifEmpty { node.server })
                    put("fingerprint", node.fingerprint?.takeIf { it.isNotBlank() } ?: "chrome")
                    put("publicKey", node.publicKey.orEmpty())
                    put("shortId", node.shortId.orEmpty())
                    put("spiderX", "/")
                })
            }

            "tls", "insecure" -> {
                stream.put("security", "tls")
                stream.put("tlsSettings", JSONObject().apply {
                    put("serverName", node.sni.ifEmpty { node.server })
                    put("allowInsecure", node.allowInsecure || node.security == "insecure")
                    put("fingerprint", node.fingerprint?.takeIf { it.isNotBlank() } ?: "chrome")
                    val alpnRaw = node.alpn?.takeIf { it.isNotBlank() }
                    if (!alpnRaw.isNullOrBlank()) {
                        val alpnArray = JSONArray()
                        alpnRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                            alpnArray.put(it)
                        }
                        if (alpnArray.length() > 0) {
                            put("alpn", alpnArray)
                        }
                    }
                })
            }

            else -> {
                stream.put("security", "none")
            }
        }

        // Fragment dialer proxy chaining + domainStrategy
        if (useFragment) {
            stream.put("sockopt", JSONObject().apply {
                put("dialerProxy", "fragment")
            })
        }

        return stream
    }

    private fun isIpAddress(host: String): Boolean {
        val ipv4Regex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        return ipv4Regex.matches(host) || host.contains(":")
    }
}
