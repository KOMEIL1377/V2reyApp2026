package com.example

import com.example.model.ProxyProtocol
import com.example.parser.ProxyConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun verify_vless_reality_parsing() {
    val uri = "vless://b82b9920-a615-4cf5-b1a9-980b18fa9182@de.v2reybnd.vip:443?security=reality&sni=speedtest.net&pbk=xyz&sid=123#Frankfurt"
    val node = ProxyConfigParser.parseLine(uri)
    assertNotNull(node)
    assertEquals(ProxyProtocol.VLESS, node?.protocol)
    assertEquals("de.v2reybnd.vip", node?.server)
    assertEquals(443, node?.port)
    assertEquals("Frankfurt", node?.name)
  }

  @Test
  fun verify_hysteria2_parsing() {
    val uri = "hy2://mypassword@nl.v2reybnd.vip:8443?sni=nl.v2reybnd.vip#Amsterdam"
    val node = ProxyConfigParser.parseLine(uri)
    assertNotNull(node)
    assertEquals(ProxyProtocol.HYSTERIA2, node?.protocol)
    assertEquals("nl.v2reybnd.vip", node?.server)
    assertEquals(8443, node?.port)
  }

  @Test
  fun verify_amnezia_parsing() {
    val uri = "awg://fi.v2reybnd.vip:51820?Jc=4&Jmin=40&Jmax=70&S1=32&S2=48#Helsinki"
    val node = ProxyConfigParser.parseLine(uri)
    assertNotNull(node)
    assertEquals(ProxyProtocol.AMNEZIA_WG, node?.protocol)
    assertEquals(4, node?.amneziaParams?.jc)
  }

  @Test
  fun verify_tuic_parsing() {
    val uri = "tuic://b82b9920-a615-4cf5-b1a9-980b18fa9182:password@tr.v2reybnd.vip:8443?congestion_control=bbr&sni=tr.v2reybnd.vip&alpn=h3#Istanbul_TUIC"
    val node = ProxyConfigParser.parseLine(uri)
    assertNotNull(node)
    assertEquals(ProxyProtocol.TUIC, node?.protocol)
    assertEquals("tr.v2reybnd.vip", node?.server)
    assertEquals(8443, node?.port)
    assertEquals("Istanbul_TUIC", node?.name)
  }

  @Test
  fun verify_openvpn_requires_auth_detection() {
    val configWithAuth = """
      client
      dev tun
      proto udp
      remote de.v2reybnd.vip 1194
      resolv-retry infinite
      nobind
      auth-user-pass
      cipher AES-256-GCM
    """.trimIndent()

    assertTrue(ProxyConfigParser.isOpenVpnConfig(configWithAuth))
    assertTrue(ProxyConfigParser.checkOpenVpnRequiresAuth(configWithAuth))
  }

  @Test
  fun verify_openvpn_without_auth_detection() {
    val configCertOnly = """
      client
      dev tun
      proto tcp
      remote nl.v2reybnd.vip 443
      nobind
      <ca>
      -----BEGIN CERTIFICATE-----
      MIIB...
      -----END CERTIFICATE-----
      </ca>
    """.trimIndent()

    assertTrue(ProxyConfigParser.isOpenVpnConfig(configCertOnly))
    assertFalse(ProxyConfigParser.checkOpenVpnRequiresAuth(configCertOnly))
  }

  @Test
  fun verify_openvpn_credential_embedding() {
    val rawConfig = """
      client
      dev tun
      remote uk.v2reybnd.vip 1194
      auth-user-pass
    """.trimIndent()

    val embedded = ProxyConfigParser.embedOpenVpnCredentials(rawConfig, "bnd_user", "bnd_pass_999")
    assertTrue(embedded.contains("<auth-user-pass>\nbnd_user\nbnd_pass_999\n</auth-user-pass>"))
    assertFalse(ProxyConfigParser.checkOpenVpnRequiresAuth(embedded))

    val parsedNode = ProxyConfigParser.parseOpenVpn(
      configOrUri = rawConfig,
      customName = "London VIP.ovpn",
      username = "bnd_user",
      password = "bnd_pass_999"
    )

    assertEquals("London VIP.ovpn", parsedNode.name)
    assertEquals("uk.v2reybnd.vip", parsedNode.server)
    assertEquals("bnd_user", parsedNode.openVpnUsername)
    assertEquals("bnd_pass_999", parsedNode.openVpnPassword)
    assertEquals(ProxyProtocol.OPENVPN, parsedNode.protocol)
  }
}
