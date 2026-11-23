package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VpnRepository
import com.example.model.ConnectionMode
import com.example.ui.components.AccountLoginDialog
import com.example.ui.components.BndTopHeaderBar
import com.example.ui.components.formatJalaliDate
import com.example.ui.components.formatTrafficQuota
import com.example.ui.theme.BndBgDark
import com.example.ui.theme.BndErrorRed
import com.example.ui.theme.BndNeonPurple
import com.example.ui.theme.BndOrchidTertiary
import com.example.ui.theme.BndSoftPurple
import com.example.ui.theme.BndSuccessGreen
import com.example.ui.theme.BndSurfaceCard
import com.example.ui.theme.BndSurfaceDark
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
    val connectionMode by viewModel.connectionMode.collectAsState()
    val socksPort by viewModel.socksPort.collectAsState()
    val httpPort by viewModel.httpPort.collectAsState()
    val bypassIran by viewModel.bypassIran.collectAsState()
    val selectedDns by viewModel.selectedDns.collectAsState()
    val enableTlsFragment by viewModel.enableTlsFragment.collectAsState()
    val enableFakeDns by viewModel.enableFakeDns.collectAsState()
    val coreLogs by viewModel.coreLogs.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val loggedInEmail by viewModel.loggedInEmail.collectAsState()
    val panelStatus by viewModel.panelSubscriptionStatus.collectAsState()
    val isRefreshingSub by viewModel.isRefreshingSubscription.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    var socksPortInput by remember(socksPort) { mutableStateOf(socksPort.toString()) }
    var httpPortInput by remember(httpPort) { mutableStateOf(httpPort.toString()) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(socksPort, httpPort) {
        socksPortInput = socksPort.toString()
        httpPortInput = httpPort.toString()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BndBgDark)
    ) {
        BndTopHeaderBar(subtitle = "تنظیمات هسته Xray و شبکه")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "پیکربندی اختصاصی Xray-core، حالت اتصال و پورت‌های محلی",
                color = BndTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 0. Online BND Account (Login / Quota / Logout)
            SettingsCard(title = "حساب کاربری اشتراک BND", icon = Icons.Default.AccountCircle) {
                if (isLoggedIn) {
                    val status = panelStatus
                    val isBlocked = status?.isBlocked() == true
                    val remainingText = when {
                        status == null -> "—"
                        status.isUnlimitedTraffic -> "نامحدود"
                        else -> formatTrafficQuota(status.remainingBytes)
                    }
                    val expiryText = when {
                        status == null -> "—"
                        status.isUnlimitedTime -> "بدون محدودیت زمانی"
                        else -> formatJalaliDate(status.expiryTimeMs)
                    }

                    InfoRow(label = "نام کاربری متصل:", value = loggedInEmail.orEmpty())
                    InfoRow(
                        label = "وضعیت اشتراک:",
                        value = if (isBlocked) "منقضی شده" else "فعال • باقی‌مانده: $remainingText • انقضا: $expiryText"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.refreshSubscriptionAndServers(context) },
                            enabled = !isRefreshingSub,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_refresh_account_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = BndSurfaceVariantDark),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = BndOrchidTertiary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بروزرسانی حساب", color = BndTextPrimary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.logout(context) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("logout_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = BndErrorRed.copy(alpha = 0.18f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = BndErrorRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("خروج از حساب", color = BndErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "برای همگام‌سازی خودکار سرورها، حجم باقی‌مانده و تاریخ انقضا با نام کاربری و Subscription ID وارد شوید. کانفیگ‌های دستی شما بدون نیاز به ورود هم همیشه فعال هستند.",
                        color = BndTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.clearLoginError()
                            showLoginDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ورود به حساب کاربری BND", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Connection Mode Selector (VPN TUN vs Proxy Only)
            SettingsCard(title = "حالت اتصال (Connection Mode)", icon = Icons.Default.Lan) {
                ConnectionModeOption(
                    title = "حالت VPN (System TUN)",
                    subtitle = "عبور کامل ترافیک تمامی برنامه‌های گوشی از طریق تونل Xray (نیازمند مجوز VPN)",
                    selected = connectionMode == ConnectionMode.VPN,
                    testTag = "mode_vpn_option",
                    onClick = { viewModel.setConnectionMode(ConnectionMode.VPN) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                ConnectionModeOption(
                    title = "حالت Proxy (Local SOCKS5 / HTTP)",
                    subtitle = "اجرای هسته Xray فقط به عنوان پراکسی محلی بدون ایجاد تونل VPN سیستم",
                    selected = connectionMode == ConnectionMode.PROXY,
                    testTag = "mode_proxy_option",
                    onClick = { viewModel.setConnectionMode(ConnectionMode.PROXY) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Local Port Settings (SOCKS5 & HTTP + Reset)
            SettingsCard(title = "تنظیمات پورت‌های محلی (Port Settings)", icon = Icons.Default.SettingsEthernet) {
                Text(
                    text = "پورت‌های شنود محلی 127.0.0.1 برای اتصال پراکسی در هر دو حالت VPN و Proxy فعال هستند.",
                    color = BndTextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = socksPortInput,
                        onValueChange = { socksPortInput = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("پورت SOCKS5", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("socks_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BndSurfaceVariantDark,
                            unfocusedContainerColor = BndSurfaceVariantDark,
                            focusedBorderColor = BndVioletPrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = BndTextPrimary,
                            unfocusedTextColor = BndTextPrimary,
                            focusedLabelColor = BndOrchidTertiary,
                            unfocusedLabelColor = BndTextMuted
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = httpPortInput,
                        onValueChange = { httpPortInput = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("پورت HTTP", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("http_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BndSurfaceVariantDark,
                            unfocusedContainerColor = BndSurfaceVariantDark,
                            focusedBorderColor = BndVioletPrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = BndTextPrimary,
                            unfocusedTextColor = BndTextPrimary,
                            focusedLabelColor = BndOrchidTertiary,
                            unfocusedLabelColor = BndTextMuted
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.savePorts(socksPortInput, httpPortInput) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_ports_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره پورت‌ها", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.resetPortsToDefault() },
                        modifier = Modifier.testTag("reset_ports_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = BndSoftPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "پیش‌فرض (${VpnRepository.DEFAULT_SOCKS_PORT}/${VpnRepository.DEFAULT_HTTP_PORT})",
                            color = BndSoftPurple,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Routing Section
            SettingsCard(title = "مسیریابی و روتینگ ترافیک", icon = Icons.AutoMirrored.Filled.AltRoute) {
                SettingSwitchItem(
                    title = "عبور سایت‌های ایرانی (Bypass Iran LAN)",
                    subtitle = "دامنه‌های .ir و آی‌پی‌های شبکه داخلی مستقیم و بدون VPN باز می‌شوند",
                    checked = bypassIran,
                    testTag = "switch_bypass_iran",
                    onCheckedChange = { viewModel.setBypassIran(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. DNS Section
            SettingsCard(title = "سرور امن DNS", icon = Icons.Default.Dns) {
                val dnsOptions = listOf(
                    Pair("1.1.1.1", "Cloudflare DNS (سریع و پایدار)"),
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

            // 5. Anti-Censorship Section (TLS Fragment & FakeDNS)
            SettingsCard(title = "تنظیمات ضد فیلترینگ و DPI هسته Xray", icon = Icons.Default.Security) {
                SettingSwitchItem(
                    title = "تکه‌تکه کردن TLS Hello (TLS Fragment)",
                    subtitle = "عبور از فایروال SNI با چِین کردن Freedom Fragment روی اتصالات TCP/TLS",
                    checked = enableTlsFragment,
                    testTag = "switch_tls_fragment",
                    onCheckedChange = { viewModel.setEnableTlsFragment(it) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                SettingSwitchItem(
                    title = "تفکیک هوشمند دامنه (FakeDNS & Sniffing)",
                    subtitle = "فعال‌سازی استخر FakeDNS برای کاهش تاخیر DNS و جلوگیری از نشت نام دامنه",
                    checked = enableFakeDns,
                    testTag = "switch_fakedns",
                    onCheckedChange = { viewModel.setEnableFakeDns(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Core Logs & Diagnostics Card
            SettingsCard(title = "گزارش و خطایابی هسته Xray (Core Logs)", icon = Icons.Default.BugReport) {
                if (!lastError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BndErrorRed.copy(alpha = 0.14f))
                            .border(1.dp, BndErrorRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "آخرین خطا: $lastError",
                            color = BndErrorRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = "تعداد خطوط ثبت‌شده: ${coreLogs.size} خط",
                    color = BndTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("view_core_logs_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BndSurfaceVariantDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("مشاهده لاگ کامل هسته", color = BndOrchidTertiary, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.clearCoreLogs()
                            viewModel.clearError()
                            Toast.makeText(context, "لاگ‌های هسته پاک شد", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("پاکسازی", color = BndTextMuted, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 7. Xray Core Info Card
            SettingsCard(title = "مشخصات فنی هسته Xray-core", icon = Icons.Default.Info) {
                InfoRow(label = "موتور مرکزی:", value = "Xray-core (${viewModel.xrayVersion})")
                InfoRow(label = "پورت‌های فعال:", value = "SOCKS :$socksPort / HTTP :$httpPort")
                InfoRow(
                    label = "پروتکل‌های پشتیبانی‌شده:",
                    value = "VLESS (Reality, TLS, XHTTP, gRPC, WS), VMess, Trojan, Shadowsocks, SOCKS, HTTP, WireGuard, Hysteria2"
                )
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

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            containerColor = BndSurfaceDark,
            title = {
                Text("گزارش اجرای هسته Xray", color = BndTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                val logText = remember(coreLogs, lastError) {
                    buildString {
                        if (!lastError.isNullOrBlank()) {
                            appendLine("[LAST ERROR] $lastError")
                            appendLine("--------------------------------")
                        }
                        if (coreLogs.isEmpty()) {
                            append("هنوز رویدادی ثبت نشده است. برای مشاهده لاگ‌ها اتصال را برقرار کنید.")
                        } else {
                            coreLogs.forEach { appendLine(it) }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BndBgDark)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logText,
                        color = if (lastError != null) BndSuccessGreen else BndTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val allText = coreLogs.joinToString("\n")
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Xray Logs", allText))
                        Toast.makeText(context, "لاگ‌ها کپی شد", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BndOrchidTertiary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("کپی لاگ", color = BndOrchidTertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("بستن", color = BndTextSecondary)
                }
            }
        )
    }

    if (showLoginDialog) {
        AccountLoginDialog(
            initialEmail = loggedInEmail.orEmpty(),
            isLoading = isLoggingIn,
            errorMessage = loginError,
            onDismiss = { showLoginDialog = false },
            onLoginSubmit = { email, subId ->
                viewModel.loginWithCredentials(email, subId) {
                    showLoginDialog = false
                }
            }
        )
    }
}

@Composable
private fun ConnectionModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BndVioletPrimary.copy(alpha = 0.16f) else BndSurfaceVariantDark.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (selected) BndOrchidTertiary else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = BndOrchidTertiary,
                unselectedColor = BndTextMuted
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = BndTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = BndTextMuted, fontSize = 11.sp, lineHeight = 15.sp)
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
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, color = BndTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = BndTextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = label, color = BndTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = BndNeonPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
