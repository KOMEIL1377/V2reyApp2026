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
}
