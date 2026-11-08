package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BndTopHeaderBar
import com.example.ui.theme.BndBgDark
import com.example.ui.theme.BndErrorRed
import com.example.ui.theme.BndNeonPurple
import com.example.ui.theme.BndOrchidTertiary
import com.example.ui.theme.BndSurfaceCard
import com.example.ui.theme.BndSurfaceVariantDark
import com.example.ui.theme.BndTextMuted
import com.example.ui.theme.BndTextPrimary
import com.example.ui.theme.BndTextSecondary
import com.example.ui.theme.BndVioletPrimary
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bypassIran by viewModel.bypassIran.collectAsState()
    val selectedDns by viewModel.selectedDns.collectAsState()

    var enableFragment by remember { mutableStateOf(true) }
    var enableMux by remember { mutableStateOf(false) }
    var enableFakeDns by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BndBgDark)
    ) {
        // Prominent Header with BND Logo
        BndTopHeaderBar(subtitle = "تنظیمات پیشرفته هسته و شبکه")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "پیکربندی هسته Sing-box / Xray و امنیت تونل",
                color = BndTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Routing Section
            SettingsCard(title = "مسیریابی و روتینگ ترافیک", icon = Icons.AutoMirrored.Filled.AltRoute) {
                SettingSwitchItem(
                    title = "عبور سایت‌های ایرانی (Bypass Iran LAN)",
                    subtitle = "بانک‌ها و سامانه‌های ایرانی بدون عبور از VPN باز می‌شوند",
                    checked = bypassIran,
                    onCheckedChange = { viewModel.setBypassIran(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // DNS Section
            SettingsCard(title = "سرور امن DNS (DoH / Encrypted)", icon = Icons.Default.Dns) {
                val dnsOptions = listOf(
                    Pair("1.1.1.1", "Cloudflare DoH (سریع‌ترین و پایدار)"),
                    Pair("8.8.8.8", "Google Public DNS"),
                    Pair("94.140.14.14", "AdGuard DNS (مسدودسازی تبلیغات)"),
                    Pair("9.9.9.9", "Quad9 Secure DNS")
                )

                dnsOptions.forEach { (ip, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setSelectedDns(ip) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDns == ip,
                            onClick = { viewModel.setSelectedDns(ip) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = BndOrchidTertiary,
                                unselectedColor = BndTextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = label, color = BndTextPrimary, fontSize = 13.sp)
                            Text(text = ip, color = BndTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Advanced Anti-Censorship Section
            SettingsCard(title = "تنظیمات ضد فیلترینگ و دور زدن DPI", icon = Icons.Default.Security) {
                SettingSwitchItem(
                    title = "تکه تکه کردن پکت‌ها (TLS Fragment)",
                    subtitle = "برای دور زدن فیلتر هوشمند SNI و فایروال همراه اول و ایرانسل",
                    checked = enableFragment,
                    onCheckedChange = { enableFragment = it }
                )

                Spacer(modifier = Modifier.height(6.dp))

                SettingSwitchItem(
                    title = "تفکیک هوشمند نام دامنه (FakeDNS & Sniffing)",
                    subtitle = "جلوگیری از نشت DNS و کاهش پینگ لود اولیه صفحات",
                    checked = enableFakeDns,
                    onCheckedChange = { enableFakeDns = it }
                )

                Spacer(modifier = Modifier.height(6.dp))

                SettingSwitchItem(
                    title = "چند کاناله کردن اتصالات (Multiplexing - Mux)",
                    subtitle = "کاهش تاخیر TCP در اتصالات با پینگ متغیر",
                    checked = enableMux,
                    onCheckedChange = { enableMux = it }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cores Version Info Card
            SettingsCard(title = "مشخصات فنی هسته و پروتکل‌ها", icon = Icons.Default.Info) {
                InfoRow(label = "نسخه Sing-box Core:", value = "v1.10.7")
                InfoRow(label = "نسخه Xray Core:", value = "v1.8.24")
                InfoRow(label = "ماژول AmneziaWG:", value = "v1.0.0 (Jc/Jmin Obfuscation)")
                InfoRow(label = "کتابخانه OpenVPN:", value = "ics-openvpn 0.7.50")
                InfoRow(label = "پروتکل‌های فعال:", value = "VLESS, Hysteria 2, TUIC, Amnezia, Trojan, VMess, OpenVPN")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Clear all servers button
            Button(
                onClick = {
                    viewModel.clearAllNodes()
                    Toast.makeText(context, "تمامی سرورها حذف شدند", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_all_servers_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BndSurfaceVariantDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = BndErrorRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("پاکسازی و حذف تمام سرورها", color = BndErrorRed, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BndSurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BndOrchidTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = BndTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = BndTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = BndTextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BndVioletPrimary,
                uncheckedThumbColor = BndTextMuted,
                uncheckedTrackColor = BndSurfaceVariantDark
            )
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = BndTextSecondary, fontSize = 12.sp)
        Text(text = value, color = BndNeonPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
