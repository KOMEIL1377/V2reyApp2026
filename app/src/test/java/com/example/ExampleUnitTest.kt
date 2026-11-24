package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.UniversalCoreConfigGenerator
import com.example.data.VpnRepository
import com.example.model.ConnectionMode
import com.example.model.ProxyProtocol
import com.example.parser.ProxyConfigParser
import com.example.service.V2reyBndVpnService
import com.example.viewmodel.MainViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

  @Test
  fun verify_vless_reality_xhttp_parsing_and_config() {
    val uri =
      "vless://b82b9920-a615-4cf5-b1a9-980b18fa9182@de.v2reybnd.vip:443?type=xhttp&mode=packet-up&path=%2Fxhttp&host=cdn.example.com&security=reality&sni=speedtest.net&pbk=xyz123&sid=ab12&fp=chrome#Frankfurt_XHTTP"
    val node = ProxyConfigParser.parseLine(uri)
    assertNotNull(node)
    assertEquals(ProxyProtocol.VLESS, node?.protocol)
    assertEquals("de.v2reybnd.vip", node?.server)
    assertEquals(443, node?.port)
    assertEquals("xhttp", node?.network)
    assertEquals("packet-up", node?.xhttpMode)
    assertEquals("reality", node?.security)
    assertEquals("xyz123", node?.publicKey)

    val json = UniversalCoreConfigGenerator.generateXrayConfig(
      node = node!!,
      connectionMode = ConnectionMode.VPN,
      socksPort = 10888,
      httpPort = 10889,
      bypassIran = true,
      dnsServer = "1.1.1.1",
      enableTlsFragment = true,
      enableFakeDns = true
    )

    val root = JSONObject(json)
    val inbounds = root.getJSONArray("inbounds")
    val tunInbound = inbounds.getJSONObject(0)
    assertEquals("tun", tunInbound.getString("protocol"))
    assertEquals(1500, tunInbound.getJSONObject("settings").getInt("mtu"))
    assertEquals("172.19.0.1/30", tunInbound.getJSONObject("settings").getJSONArray("gateway").getString(0))
    assertEquals("socks", inbounds.getJSONObject(1).getString("protocol"))
    assertEquals(10888, inbounds.getJSONObject(1).getInt("port"))
    assertEquals("http", inbounds.getJSONObject(2).getString("protocol"))
    assertEquals(10889, inbounds.getJSONObject(2).getInt("port"))

    val dnsRule = root.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
    assertEquals("53", dnsRule.getString("port"))
    assertEquals("dns-out", dnsRule.getString("outboundTag"))

    assertTrue(root.has("fakedns"))
    val outbounds = root.getJSONArray("outbounds")
    val proxy = outbounds.getJSONObject(0)
    assertEquals("vless", proxy.getString("protocol"))
    val stream = proxy.getJSONObject("streamSettings")
    assertEquals("xhttp", stream.getString("network"))
    assertEquals("fragment", stream.getJSONObject("sockopt").getString("dialerProxy"))
    assertEquals("packet-up", stream.getJSONObject("xhttpSettings").getString("mode"))
  }

  @Test
  fun verify_proxy_mode_omits_tun_inbound() {
    val node = ProxyConfigParser.parseLine(
      "trojan://pass123@tr.v2reybnd.vip:443?sni=tr.v2reybnd.vip&type=ws&path=%2Fws#Trojan_WS"
    )!!
    val json = UniversalCoreConfigGenerator.generateXrayConfig(
      node = node,
      connectionMode = ConnectionMode.PROXY,
      socksPort = 20808,
      httpPort = 20809,
      bypassIran = false,
      enableTlsFragment = false,
      enableFakeDns = false
    )
    val root = JSONObject(json)
    val inbounds = root.getJSONArray("inbounds")
    assertEquals(2, inbounds.length())
    assertEquals("socks", inbounds.getJSONObject(0).getString("protocol"))
    assertEquals(20808, inbounds.getJSONObject(0).getInt("port"))
    assertEquals("http", inbounds.getJSONObject(1).getString("protocol"))
    assertEquals(20809, inbounds.getJSONObject(1).getInt("port"))
    assertFalse(root.has("fakedns"))
  }

  @Test
  fun verify_hysteria2_wireguard_socks_http_shadowsocks_vmess() {
    val hy2 = ProxyConfigParser.parseLine(
      "hy2://secret@nl.v2reybnd.vip:8443?sni=nl.v2reybnd.vip&obfs=salamander&obfs-password=salpass#NL_Hy2"
    )
    assertEquals(ProxyProtocol.HYSTERIA2, hy2?.protocol)
    val hy2Out = JSONObject(UniversalCoreConfigGenerator.generateXrayConfig(hy2!!))
      .getJSONArray("outbounds").getJSONObject(0)
    assertEquals("hysteria", hy2Out.getString("protocol"))
    assertEquals("salamander", hy2Out.getJSONObject("streamSettings").getJSONObject("finalmask").getJSONArray("udp").getJSONObject(0).getString("type"))

    val wg = ProxyConfigParser.parseLine(
      "wireguard://privKey123=@wg.v2reybnd.vip:51820?publickey=pubKey456=&address=172.16.0.2/32&reserved=1,2,3&mtu=1280#WG_Node"
    )
    assertEquals(ProxyProtocol.WIREGUARD, wg?.protocol)
    val wgOut = JSONObject(UniversalCoreConfigGenerator.generateXrayConfig(wg!!))
      .getJSONArray("outbounds").getJSONObject(0)
    assertEquals("wireguard", wgOut.getString("protocol"))
    assertEquals("privKey123=", wgOut.getJSONObject("settings").getString("secretKey"))

    val socks = ProxyConfigParser.parseLine("socks5://user1:pass1@127.0.0.1:1080#LocalSocks")
    assertEquals(ProxyProtocol.SOCKS, socks?.protocol)
    assertEquals("user1", socks?.username)

    val http = ProxyConfigParser.parseLine("http://u2:p2@proxy.example.com:8080#HttpProxy")
    assertEquals(ProxyProtocol.HTTP, http?.protocol)
    assertEquals("proxy.example.com", http?.server)

    val ss = ProxyConfigParser.parseLine("ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@1.2.3.4:8388#SS_Test")
    assertEquals(ProxyProtocol.SHADOWSOCKS, ss?.protocol)
    assertEquals("aes-256-gcm", ss?.method)
  }

  @Test
  fun verify_removed_openvpn_amnezia_tuic_are_rejected() {
    assertNull(ProxyConfigParser.parseLine("openvpn://1.2.3.4:1194#OVPN"))
    assertNull(ProxyConfigParser.parseLine("amnezia://abcdef"))
    assertNull(ProxyConfigParser.parseLine("awg://1.2.3.4:51820#AWG"))
    assertNull(ProxyConfigParser.parseLine("tuic://uuid:pass@1.2.3.4:443#TUIC"))
  }

  @Test
  fun verify_connect_writes_nonempty_config_json_and_passes_path() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val context: Context = app
    val repository = VpnRepository(context)
    val viewModel = MainViewModel(repository)

    val node = ProxyConfigParser.parseLine(
      "vless://b82b9920-a615-4cf5-b1a9-980b18fa9182@de.v2reybnd.vip:443?type=tcp&security=tls&sni=de.v2reybnd.vip#DE_TLS"
    )!!
    repository.addNodes(listOf(node))
    viewModel.setConnectionMode(ConnectionMode.PROXY)
    viewModel.savePorts("12808", "12809")

    var prepareCalled = false
    viewModel.toggleConnection(context) { prepareCalled = true }
    assertFalse(prepareCalled)

    val expectedFile = File(context.filesDir, V2reyBndVpnService.CONFIG_FILE_NAME)
    assertTrue(expectedFile.exists())
    assertTrue(expectedFile.length() > 0L)

    val writtenJson = JSONObject(expectedFile.readText())
    val inbounds = writtenJson.getJSONArray("inbounds")
    assertEquals(2, inbounds.length())
    assertEquals(12808, inbounds.getJSONObject(0).getInt("port"))
    assertEquals(12809, inbounds.getJSONObject(1).getInt("port"))

    val startedIntent = Shadows.shadowOf(app).nextStartedService
    assertNotNull(startedIntent)
    assertEquals(V2reyBndVpnService.ACTION_CONNECT, startedIntent.action)
    assertEquals(expectedFile.absolutePath, startedIntent.getStringExtra(V2reyBndVpnService.EXTRA_CONFIG_PATH))
    assertEquals(ConnectionMode.PROXY.name, startedIntent.getStringExtra(V2reyBndVpnService.EXTRA_CONNECTION_MODE))
  }

  @Test
  fun verify_xray_json_import_validation_and_execution() {
    val validXrayJson = """
      {
        "remarks": "Custom VLESS JSON",
        "outbounds": [
          {
            "tag": "proxy",
            "protocol": "vless",
            "settings": {
              "vnext": [
                {
                  "address": "custom.v2reybnd.vip",
                  "port": 443,
                  "users": [{ "id": "b82b9920-a615-4cf5-b1a9-980b18fa9182", "encryption": "none" }]
                }
              ]
            },
            "streamSettings": {
              "network": "ws",
              "security": "tls"
            }
          }
        ]
      }
    """.trimIndent()

    val validRes = ProxyConfigParser.parseBulkTextWithValidation(validXrayJson)
    assertNull(validRes.errorMessage)
    assertEquals(1, validRes.nodes.size)
    val jsonNode = validRes.nodes.first()
    assertEquals(ProxyProtocol.CUSTOM_JSON, jsonNode.protocol)
    assertEquals("Custom VLESS JSON", jsonNode.name)
    assertEquals("custom.v2reybnd.vip", jsonNode.server)

    val generatedVpn = JSONObject(
      UniversalCoreConfigGenerator.generateXrayConfig(
        node = jsonNode,
        connectionMode = ConnectionMode.VPN,
        socksPort = 11808,
        httpPort = 11809
      )
    )
    assertFalse(generatedVpn.has("remarks"))
    val inbounds = generatedVpn.getJSONArray("inbounds")
    assertEquals("tun", inbounds.getJSONObject(0).getString("protocol"))
    assertEquals(11808, inbounds.getJSONObject(1).getInt("port"))
    assertEquals(11809, inbounds.getJSONObject(2).getInt("port"))

    val invalidRes = ProxyConfigParser.parseBulkTextWithValidation("""{"foo":"bar"}""")
    assertTrue(invalidRes.nodes.isEmpty())
    assertNotNull(invalidRes.errorMessage)
  }

  @Test
  fun verify_sanaei_panel_auth_quota_expiry_and_errors() {
    val samplePanelJson = """
      {
        "success": true,
        "msg": "",
        "obj": [
          {
            "id": 1,
            "enable": true,
            "remark": "Germany_Reality",
            "port": 443,
            "protocol": "vless",
            "settings": "{\"clients\":[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"email\":\"Kamibnd1\",\"subId\":\"subSecret99\",\"enable\":true,\"totalGB\":53687091200,\"expiryTime\":4102444800000,\"flow\":\"xtls-rprx-vision\"},{\"id\":\"22222222-2222-2222-2222-222222222222\",\"email\":\"DisabledUser\",\"subId\":\"subDisabled\",\"enable\":false,\"totalGB\":0,\"expiryTime\":0}]}",
            "streamSettings": "{\"network\":\"tcp\",\"security\":\"reality\",\"realitySettings\":{\"serverNames\":[\"yahoo.com\"],\"shortIds\":[\"ab\"],\"settings\":{\"publicKey\":\"pub123\",\"fingerprint\":\"chrome\"}}}",
            "clientStats": [
              {
                "id": 10,
                "inboundId": 1,
                "enable": true,
                "email": "Kamibnd1",
                "up": 1073741824,
                "down": 2147483648,
                "total": 53687091200,
                "expiryTime": 4102444800000
              },
              {
                "id": 11,
                "inboundId": 1,
                "enable": false,
                "email": "DisabledUser",
                "up": 0,
                "down": 0,
                "total": 0,
                "expiryTime": 0
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val client = com.example.network.SanaeiPanelClient(
      baseUrl = "https://test.kami2.ir:2087/g956c0SiBSMcFjogcR",
      apiToken = "testToken"
    )

    // 1. Success match by email + subId
    val ok = client.parseInboundsListResponse(
      responseJson = samplePanelJson,
      panelBaseUrl = "https://test.kami2.ir:2087/g956c0SiBSMcFjogcR",
      inputEmail = "Kamibnd1",
      inputSubId = "subSecret99",
      rejectIfDisabled = true
    )
    assertTrue(ok is com.example.network.PanelSyncResult.Success)
    val success = ok as com.example.network.PanelSyncResult.Success
    assertEquals("Kamibnd1", success.status.email)
    assertEquals(3221225472L, success.status.usedBytes)
    assertEquals(53687091200L - 3221225472L, success.status.remainingBytes)
    assertFalse(success.status.isBlocked(1700000000000L))
    assertEquals(1, success.nodes.size)
    assertEquals(com.example.model.NodeOrigin.PANEL_ACCOUNT, success.nodes.first().origin)
    assertEquals("test.kami2.ir", success.nodes.first().server)

    // 2. Wrong subId
    val wrongPass = client.parseInboundsListResponse(
      samplePanelJson, "https://test.kami2.ir:2087/x", "Kamibnd1", "wrongSubId", true
    )
    assertTrue(wrongPass is com.example.network.PanelSyncResult.Error)
    assertTrue((wrongPass as com.example.network.PanelSyncResult.Error).isAuthError)

    // 3. Unknown user
    val unknownUser = client.parseInboundsListResponse(
      samplePanelJson, "https://test.kami2.ir:2087/x", "NoSuchUser", "subSecret99", true
    )
    assertTrue(unknownUser is com.example.network.PanelSyncResult.Error)
    assertTrue((unknownUser as com.example.network.PanelSyncResult.Error).isAuthError)

    // 4. Disabled user on login
    val disabledUser = client.parseInboundsListResponse(
      samplePanelJson, "https://test.kami2.ir:2087/x", "DisabledUser", "subDisabled", true
    )
    assertTrue(disabledUser is com.example.network.PanelSyncResult.Error)
    assertTrue((disabledUser as com.example.network.PanelSyncResult.Error).isDisabled)
  }

  @Test
  fun verify_exhausted_or_expired_blocks_connection_and_logout_keeps_manual_nodes() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val context: Context = app
    val repository = VpnRepository(context)
    val viewModel = MainViewModel(repository)

    // Add manual node
    val manualNode = ProxyConfigParser.parseLine(
      "vless://b82b9920-a615-4cf5-b1a9-980b18fa9182@manual.example.com:443?type=tcp&security=tls#ManualNode"
    )!!
    repository.addNode(manualNode)

    // Save exhausted panel account node
    val panelNode = ProxyConfigParser.parseLine(
      "vless://11111111-1111-1111-1111-111111111111@panel.example.com:443?type=tcp&security=tls#PanelNode"
    )!!.copy(origin = com.example.model.NodeOrigin.PANEL_ACCOUNT)
    val exhaustedStatus = com.example.model.SubscriptionStatus(
      source = com.example.model.AccountSource.PANEL_LOGIN,
      title = "Kamibnd1",
      email = "Kamibnd1",
      subId = "sub1",
      uploadBytes = 5_000_000_000L,
      downloadBytes = 6_000_000_000L,
      totalBytes = 10_000_000_000L,
      expiryTimeMs = 0L,
      enabled = true
    )
    repository.savePanelSession("Kamibnd1", "sub1", exhaustedStatus, listOf(panelNode))
    repository.selectNode(panelNode)
    viewModel.setConnectionMode(ConnectionMode.PROXY)

    // Exhausted quota must block starting VPN/Xray
    viewModel.toggleConnection(context) {}
    assertNull(Shadows.shadowOf(app).nextStartedService)
    assertTrue(V2reyBndVpnService.lastError.value?.contains("منقضی شده") == true)

    // Unlimited traffic (total == 0) + unlimited expiry (expiryTime <= 0) must NOT be blocked
    val unlimitedStatus = com.example.model.SubscriptionStatus(
      source = com.example.model.AccountSource.PANEL_LOGIN,
      title = "UnlimitedUser",
      uploadBytes = 999_999_999L,
      downloadBytes = 999_999_999L,
      totalBytes = 0L,
      expiryTimeMs = 0L,
      enabled = true
    )
    assertTrue(unlimitedStatus.isUnlimitedTraffic)
    assertTrue(unlimitedStatus.isUnlimitedTime)
    assertFalse(unlimitedStatus.isBlocked())

    // Logout removes only PANEL_ACCOUNT nodes and keeps MANUAL node intact
    viewModel.logout(context)
    assertFalse(viewModel.isLoggedIn.value)
    assertEquals(1, viewModel.nodes.value.size)
    assertEquals("ManualNode", viewModel.nodes.value.first().name)
    assertEquals(com.example.model.NodeOrigin.MANUAL, viewModel.nodes.value.first().origin)
  }

  @Test
  fun verify_panel_sub_url_discovery_and_lossless_variants() {
    val client = com.example.network.SanaeiPanelClient(
      baseUrl = "https://panel.example.com:2087/secret",
      apiToken = "tok"
    )
    val panelSettings = JSONObject(
      """
      {
        "subURI": "https://sub.example.com:2095/sub",
        "subPort": 2095,
        "subDomain": "alt.example.com",
        "subPath": "/customsub/"
      }
      """.trimIndent()
    )
    val candidates = client.buildCandidateSubUrlsFromPanelSettings(
      panelBaseUrl = "https://panel.example.com:2087/secret",
      subId = "subToken1",
      panelSettingsObj = panelSettings
    )
    assertTrue(candidates.first() == "https://sub.example.com:2095/sub/subToken1")
    assertTrue(candidates.any { it.contains("alt.example.com:2095/customsub/subToken1") })

    // Verify lossless bulk subscription import keeps exact port 2095, literal '+' in remark, empty SNI for non-TLS,
    // and distinct IP/domain variants on the same port without collapsing them.
    val bulkSubPayload = listOf(
      "vless://11111111-1111-1111-1111-111111111111@1.1.1.1:2095?type=tcp&security=none#BND+IR+1",
      "vless://11111111-1111-1111-1111-111111111111@2.2.2.2:2095?type=tcp&security=none#BND+IR+2",
      "vless://11111111-1111-1111-1111-111111111111@cdn.example.com:2095?type=ws&host=cdn.example.com&path=%2Fws&security=tls&sni=cdn.example.com#BND+CDN"
    ).joinToString("\n")

    val parsed = ProxyConfigParser.parseBulkTextWithValidation(
      bulkSubPayload,
      com.example.model.NodeOrigin.PANEL_ACCOUNT
    )
    assertNull(parsed.errorMessage)
    assertEquals(3, parsed.nodes.size)
    assertEquals(listOf(2095, 2095, 2095), parsed.nodes.map { it.port })
    assertEquals(listOf("1.1.1.1", "2.2.2.2", "cdn.example.com"), parsed.nodes.map { it.server })
    assertEquals("BND+IR+1", parsed.nodes[0].name)
    assertEquals("", parsed.nodes[0].sni)
    assertEquals("cdn.example.com", parsed.nodes[2].sni)
  }

  @Test
  fun verify_unlimited_subscriptions_switching_and_unconditional_delete() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val context: Context = app
    val repository = VpnRepository(context)
    val viewModel = MainViewModel(repository)

    // Manual config must remain untouched across all subscription operations
    val manual = ProxyConfigParser.parseLine(
      "vless://aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa@manual.host.vip:443?type=tcp&security=tls#ManualKeep"
    )!!
    repository.addNode(manual)

    // Add 3 separate unlimited subscriptions
    val n1 = ProxyConfigParser.parseLine(
      "vless://11111111-1111-1111-1111-111111111111@s1.example.com:443?security=tls#Sub1_Node"
    )!!
    val sub1 = repository.upsertManualSubscription(
      url = "https://sub1.example.com/sub/a",
      status = com.example.model.SubscriptionStatus(
        source = com.example.model.AccountSource.SUBSCRIPTION_URL,
        title = "Sub 1",
        totalBytes = 10_000_000_000L
      ),
      syncedNodes = listOf(n1),
      makeActive = true
    )

    val n2 = ProxyConfigParser.parseLine(
      "vless://22222222-2222-2222-2222-222222222222@s2.example.com:443?security=tls#Sub2_Node"
    )!!
    val sub2 = repository.upsertManualSubscription(
      url = "https://sub2.example.com/sub/b",
      status = com.example.model.SubscriptionStatus(
        source = com.example.model.AccountSource.SUBSCRIPTION_URL,
        title = "Sub 2 Expired",
        uploadBytes = 8_000_000_000L,
        downloadBytes = 3_000_000_000L,
        totalBytes = 10_000_000_000L,
        expiryTimeMs = 1000L,
        enabled = false
      ),
      syncedNodes = listOf(n2),
      makeActive = false
    )

    val n3 = ProxyConfigParser.parseLine(
      "vless://33333333-3333-3333-3333-333333333333@s3.example.com:2095?security=none#Sub3_Node"
    )!!
    val sub3 = repository.upsertManualSubscription(
      url = "https://sub3.example.com/sub/c",
      status = com.example.model.SubscriptionStatus(
        source = com.example.model.AccountSource.SUBSCRIPTION_URL,
        title = "Sub 3"
      ),
      syncedNodes = listOf(n3),
      makeActive = false
    )

    assertEquals(3, viewModel.subscriptions.value.size)
    assertEquals(4, viewModel.nodes.value.size)

    // Switch active subscription to sub2 and verify activeSubscriptionStatus + selectedNode update
    viewModel.selectSubscription(sub2.id)
    assertEquals(sub2.id, viewModel.activeSubscriptionStatus.value?.id)
    assertEquals("Sub2_Node", viewModel.selectedNode.value?.name)
    assertTrue(viewModel.activeSubscriptionStatus.value?.isBlocked() == true)

    // Unconditionally delete exhausted/expired/disabled sub2
    viewModel.deleteSubscription(context, sub2.id)
    assertEquals(2, viewModel.subscriptions.value.size)
    assertFalse(viewModel.subscriptions.value.any { it.id == sub2.id })
    assertEquals(3, viewModel.nodes.value.size)
    assertFalse(viewModel.nodes.value.any { it.name == "Sub2_Node" })
    assertTrue(viewModel.nodes.value.any { it.name == "ManualKeep" })
    assertTrue(viewModel.nodes.value.any { it.name == "Sub1_Node" })
    assertTrue(viewModel.nodes.value.any { it.name == "Sub3_Node" })
    assertTrue(viewModel.subscriptions.value.any { it.id == sub1.id })
    assertTrue(viewModel.subscriptions.value.any { it.id == sub3.id })
  }

  @Test
  fun verify_qr_payload_classification_and_import() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val context: Context = app
    val repository = VpnRepository(context)
    val viewModel = MainViewModel(repository)

    // 1. Subscription URL (including custom port 2096)
    val subClass = ProxyConfigParser.classifyQrPayload("https://sub.example.com:2096/sub/abc123")
    assertEquals(ProxyConfigParser.QrImportType.SUBSCRIPTION_URL, subClass.type)

    // 2. Single VLESS URI -> CONFIG
    val uriClass = ProxyConfigParser.classifyQrPayload(
      "vless://b82b9920-a615-4cf5-b1a9-980b18fa9182@de.v2reybnd.vip:2095?type=tcp&security=none#QR_VLESS"
    )
    assertEquals(ProxyConfigParser.QrImportType.CONFIG, uriClass.type)
    assertEquals(1, uriClass.nodes.size)

    val importedType = viewModel.importScannedQrContent(
      "vless://b82b9920-a615-4cf5-b1a9-980b18fa9182@de.v2reybnd.vip:2095?type=tcp&security=none#QR_VLESS"
    )
    assertEquals(ProxyConfigParser.QrImportType.CONFIG, importedType)
    assertTrue(viewModel.nodes.value.any { it.name == "QR_VLESS" && it.port == 2095 })

    // 3. Invalid payload -> INVALID
    val invalidType = viewModel.importScannedQrContent("not-a-valid-qr-config-or-url")
    assertEquals(ProxyConfigParser.QrImportType.INVALID, invalidType)
  }

  @Test
  fun verify_runtime_geodata_sanitization_preserves_saved_json() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val assets = UniversalCoreConfigGenerator.prepareAssetsDir(app)
    assertTrue(assets.dir.exists())
    assertEquals("xray_assets", assets.dir.name)

    val rawCustomJson = """
      {
        "remarks": "GeoJsonNode",
        "dns": {
          "servers": [
            "1.1.1.1",
            {
              "address": "223.5.5.5",
              "domains": ["geosite:ir", "domain:ir"],
              "expectIPs": ["geoip:ir"]
            },
            {
              "address": "8.8.8.8",
              "domains": ["geosite:cn"]
            }
          ]
        },
        "outbounds": [
          {
            "tag": "proxy",
            "protocol": "vless",
            "settings": {
              "vnext": [
                {
                  "address": "geo.example.com",
                  "port": 443,
                  "users": [{ "id": "b82b9920-a615-4cf5-b1a9-980b18fa9182", "encryption": "none" }]
                }
              ]
            }
          }
        ],
        "routing": {
          "rules": [
            { "type": "field", "domain": ["geosite:category-ads-all"], "outboundTag": "block" },
            { "type": "field", "ip": ["geoip:ir", "geoip:private"], "outboundTag": "direct" },
            { "type": "field", "domain": ["geosite:ir", "domain:ir"], "outboundTag": "direct" },
            { "type": "field", "network": "tcp,udp", "outboundTag": "proxy" }
          ]
        }
      }
    """.trimIndent()

    val parseRes = ProxyConfigParser.parseBulkTextWithValidation(rawCustomJson)
    assertEquals(1, parseRes.nodes.size)
    val savedNode = parseRes.nodes.first()
    val originalSavedJson = savedNode.rawXrayJson!!
    assertTrue(originalSavedJson.contains("geosite:category-ads-all"))

    val runtimeConfig = UniversalCoreConfigGenerator.generateXrayConfig(
      node = savedNode,
      connectionMode = ConnectionMode.VPN,
      socksPort = 10808,
      httpPort = 10809
    )
    val sanitized = UniversalCoreConfigGenerator.sanitizeRuntimeGeodata(
      configJson = runtimeConfig,
      hasGeosite = false,
      hasGeoip = false
    )

    // Saved config remains untouched
    assertTrue(savedNode.rawXrayJson!!.contains("geosite:category-ads-all"))
    assertTrue(savedNode.rawXrayJson!!.contains("geoip:ir"))

    val root = JSONObject(sanitized)
    val rules = root.getJSONObject("routing").getJSONArray("rules")
    // Expected kept rules: tun DNS port 53, mixed ["domain:ir"], catch-all tcp,udp
    val outboundTags = (0 until rules.length()).map { rules.getJSONObject(it).getString("outboundTag") }
    assertFalse(outboundTags.contains("block"))
    assertTrue(outboundTags.contains("dns-out"))
    assertTrue(outboundTags.contains("direct"))
    assertTrue(outboundTags.contains("proxy"))

    val directRule = (0 until rules.length())
      .map { rules.getJSONObject(it) }
      .first { it.optString("outboundTag") == "direct" }
    val directDomains = directRule.getJSONArray("domain")
    assertEquals(1, directDomains.length())
    assertEquals("domain:ir", directDomains.getString(0))

    val dnsServers = root.getJSONObject("dns").getJSONArray("servers")
    // Server 8.8.8.8 had only geosite:cn so it's dropped; 223.5.5.5 keeps domain:ir without expectIPs
    assertEquals(2, dnsServers.length())
    val domesticDns = dnsServers.getJSONObject(1)
    assertEquals("223.5.5.5", domesticDns.getString("address"))
    assertEquals("domain:ir", domesticDns.getJSONArray("domains").getString(0))
    assertFalse(domesticDns.has("expectIPs"))
  }

  @Test
  fun verify_raw_vless_port_2095_xhttp_tls_outbounds_and_settings() {
    // Variant 1: Explicit host, path, mode, alpn, allowInsecure, plus stray flow that must be ignored on xhttp
    val uriWithHost =
      "vless://b82b9920-a615-4cf5-b1a9-980b18fa9182@edge.v2reybnd.vip:2095?type=xhttp&path=%2Fxhttp-api&host=cdn.v2reybnd.vip&mode=stream-up&security=tls&sni=cdn.v2reybnd.vip&alpn=h2,http/1.1&fp=firefox&flow=xtls-rprx-vision&allowInsecure=1#Edge2095"
    val node1 = ProxyConfigParser.parseLine(uriWithHost)!!
    assertEquals(ProxyProtocol.VLESS, node1.protocol)
    assertEquals(2095, node1.port)
    assertEquals("xhttp", node1.network)
    assertEquals("tls", node1.security)

    val config1 = UniversalCoreConfigGenerator.generateXrayConfig(
      node = node1,
      connectionMode = ConnectionMode.VPN,
      socksPort = 10808,
      httpPort = 10809
    )
    // Non-geo config must be returned verbatim by sanitizeRuntimeGeodata
    val afterSanitize1 = UniversalCoreConfigGenerator.sanitizeRuntimeGeodata(
      configJson = config1,
      hasGeosite = false,
      hasGeoip = false
    )
    assertEquals(config1, afterSanitize1)

    val outbound1 = JSONObject(config1).getJSONArray("outbounds").getJSONObject(0)
    assertEquals("vless", outbound1.getString("protocol"))
    val vnext1 = outbound1.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
    assertEquals("edge.v2reybnd.vip", vnext1.getString("address"))
    assertEquals(2095, vnext1.getInt("port"))
    val user1 = vnext1.getJSONArray("users").getJSONObject(0)
    assertEquals("none", user1.getString("encryption"))
    assertFalse(user1.has("flow"))

    val stream1 = outbound1.getJSONObject("streamSettings")
    assertEquals("xhttp", stream1.getString("network"))
    assertEquals("tls", stream1.getString("security"))

    val xhttp1 = stream1.getJSONObject("xhttpSettings")
    assertEquals("/xhttp-api", xhttp1.getString("path"))
    assertEquals("cdn.v2reybnd.vip", xhttp1.getString("host"))
    assertEquals("stream-up", xhttp1.getString("mode"))

    val tls1 = stream1.getJSONObject("tlsSettings")
    assertEquals("cdn.v2reybnd.vip", tls1.getString("serverName"))
    assertTrue(tls1.getBoolean("allowInsecure"))
    assertEquals("firefox", tls1.getString("fingerprint"))
    val alpn1 = tls1.getJSONArray("alpn")
    assertEquals(2, alpn1.length())
    assertEquals("h2", alpn1.getString(0))
    assertEquals("http/1.1", alpn1.getString(1))

    // Variant 2: Raw IP server on 2095, no host= param, domain sni=, fp=none
    val uriWithoutHost =
      "vless://11111111-1111-1111-1111-111111111111@185.10.10.10:2095?type=xhttp&path=%2F&security=tls&sni=sub.v2reybnd.vip&fp=none#Raw2095"
    val node2 = ProxyConfigParser.parseLine(uriWithoutHost)!!
    val config2 = UniversalCoreConfigGenerator.generateXrayConfig(
      node = node2,
      connectionMode = ConnectionMode.PROXY,
      socksPort = 10808,
      httpPort = 10809
    )
    val outbound2 = JSONObject(config2).getJSONArray("outbounds").getJSONObject(0)
    val stream2 = outbound2.getJSONObject("streamSettings")
    val xhttp2 = stream2.getJSONObject("xhttpSettings")
    assertEquals("/", xhttp2.getString("path"))
    assertEquals("sub.v2reybnd.vip", xhttp2.getString("host"))
    assertEquals("auto", xhttp2.getString("mode"))

    val tls2 = stream2.getJSONObject("tlsSettings")
    assertEquals("sub.v2reybnd.vip", tls2.getString("serverName"))
    assertFalse(tls2.getBoolean("allowInsecure"))
    assertFalse(tls2.has("fingerprint"))

    // Verify end-to-end ViewModel connect writes valid port 2095 xhttp+tls config.json
    val app = ApplicationProvider.getApplicationContext<Application>()
    val context: Context = app
    val repository = VpnRepository(context)
    val viewModel = MainViewModel(repository)
    repository.addNode(node2)
    repository.selectNode(node2)
    viewModel.setConnectionMode(ConnectionMode.PROXY)
    viewModel.toggleConnection(context) {}

    val writtenFile = File(context.filesDir, V2reyBndVpnService.CONFIG_FILE_NAME)
    assertTrue(writtenFile.exists())
    val writtenOutbound = JSONObject(writtenFile.readText())
      .getJSONArray("outbounds").getJSONObject(0)
    assertEquals(2095, writtenOutbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getInt("port"))
    assertEquals("xhttp", writtenOutbound.getJSONObject("streamSettings").getString("network"))
    assertEquals("tls", writtenOutbound.getJSONObject("streamSettings").getString("security"))
  }
}
