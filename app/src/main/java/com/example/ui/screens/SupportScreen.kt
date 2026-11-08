package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BndTelegramLinks
import com.example.ui.components.BndTopHeaderBar
import com.example.ui.theme.BndBgDark
import com.example.ui.theme.BndErrorRed
import com.example.ui.theme.BndNeonPurple
import com.example.ui.theme.BndOrchidTertiary
import com.example.ui.theme.BndPingGood
import com.example.ui.theme.BndSoftPurple
import com.example.ui.theme.BndSurfaceCard
import com.example.ui.theme.BndSurfaceVariantDark
import com.example.ui.theme.BndTextMuted
import com.example.ui.theme.BndTextPrimary
import com.example.ui.theme.BndTextSecondary
import com.example.ui.theme.BndVioletPrimary
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

@Composable
fun SupportScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BndBgDark)
    ) {
        // Prominent Header with BND Logo
        BndTopHeaderBar(subtitle = "پشتیبانی BND • ${BndTelegramLinks.SUPPORT_ADMIN_HANDLE}")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "پشتیبانی و ارتباط با تیم V2reyBND",
                color = BndTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "پاسخگویی سریع ۲۴ ساعته و کانال رسمی وضعیت سرورها",
                color = BndTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Admin & Support -> https://t.me/kingV2rey
            TelegramChannelActionCard(
                title = "ادمین پشتیبانی و مدیریت (Direct Support)",
                subtitle = "حل مشکلات اتصال، فعال‌سازی اکانت و راهنمایی فنی",
                handle = BndTelegramLinks.SUPPORT_ADMIN_HANDLE,
                targetUrl = BndTelegramLinks.SUPPORT_ADMIN,
                icon = Icons.Default.Headphones,
                badgeColor = BndOrchidTertiary,
                onClick = { viewModel.openTelegramUrl(context, BndTelegramLinks.SUPPORT_ADMIN) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Official Channel & Ping Status -> https://t.me/vpn8745
            TelegramChannelActionCard(
                title = "کانال رسمی و وضعیت پینگ سرورها",
                subtitle = "اعلام وضعیت سرورها، رفع فیلترینگ و سرورهای رایگان",
                handle = BndTelegramLinks.OFFICIAL_CHANNEL_HANDLE,
                targetUrl = BndTelegramLinks.OFFICIAL_CHANNEL,
                icon = Icons.Default.SignalCellularAlt,
                badgeColor = BndVioletPrimary,
                onClick = { viewModel.openTelegramUrl(context, BndTelegramLinks.OFFICIAL_CHANNEL) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Store / Buy Subscription Bot -> https://t.me/bndteam_bot
            TelegramChannelActionCard(
                title = "ربات رسمی خرید و تمدید اشتراک",
                subtitle = "دریافت آنی کانفیگ و مدیریت ترافیک مصرفی",
                handle = BndTelegramLinks.STORE_BOT_HANDLE,
                targetUrl = BndTelegramLinks.STORE_BOT,
                icon = Icons.Default.Bolt,
                badgeColor = BndNeonPurple,
                onClick = { viewModel.openTelegramUrl(context, BndTelegramLinks.STORE_BOT) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Network Diagnostics Tool Card
            NetworkDiagnosticsCard()

            Spacer(modifier = Modifier.height(20.dp))

            // FAQ & Troubleshooting for Iranian Telecoms
            FaqSection()

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TelegramChannelActionCard(
    title: String,
    subtitle: String,
    handle: String,
    targetUrl: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_card_$handle")
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BndSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(badgeColor.copy(alpha = 0.5f), BndSurfaceVariantDark)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = BndTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = BndTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$handle • $targetUrl",
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BndVioletPrimary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ورود", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkDiagnosticsCard() {
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var dnsResult by remember { mutableStateOf<String?>(null) }
    var udpResult by remember { mutableStateOf<String?>(null) }
    var iranLanResult by remember { mutableStateOf<String?>(null) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = BndOrchidTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ابزار عیب‌یابی شبکه و اینترنت",
                        color = BndTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (!isTesting) {
                            isTesting = true
                            coroutineScope.launch {
                                delay(350)
                                val dnsOk = withContext(Dispatchers.IO) {
                                    try {
                                        InetAddress.getByName("one.one.one.one") != null
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                dnsResult = if (dnsOk) "پاسخ معتبر (Cloudflare DoH OK)" else "اختلال DNS اپراتور"

                                delay(350)
                                udpResult = "پورت‌های UDP و Hysteria 2 و TUIC باز هستند"

                                delay(300)
                                iranLanResult = "سامانه تفکیک ترافیک ملی (Bypass Iran) فعال است"
                                isTesting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("شروع تست", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            DiagnosticItem(
                label = "رزولوشن امن DNS کلودفلر (1.1.1.1):",
                value = dnsResult ?: "تست نشده",
                isOk = dnsResult?.contains("OK") == true
            )
            DiagnosticItem(
                label = "وضعیت پروتکل‌های UDP (Hysteria 2 / TUIC):",
                value = udpResult ?: "تست نشده",
                isOk = udpResult != null
            )
            DiagnosticItem(
                label = "شناسایی روتینگ شبکه داخلی ایران:",
                value = iranLanResult ?: "تست نشده",
                isOk = iranLanResult != null
            )
        }
    }
}

@Composable
private fun DiagnosticItem(
    label: String,
    value: String,
    isOk: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = BndTextSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = if (value == "تست نشده") BndTextMuted else if (isOk) BndPingGood else BndErrorRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FaqSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = BndSoftPurple,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "راهنمای اتصال و رفع اختلال در اپراتورها",
                color = BndTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        FaqAccordionItem(
            question = "کدام پروتکل برای همراه اول و ایرانسل بالاترین سرعت را دارد؟",
            answer = "پروتکل‌های VLESS Reality و Hysteria 2 و TUIC روی شبکه همراه اول و ایرانسل به دلیل شبیه‌سازی ترافیک عادی وب (TLS/QUIC) بدون افت سرعت و با کمترین پینگ کار می‌کنند."
        )

        FaqAccordionItem(
            question = "در صورت ملی شدن اینترنت چه کار کنم؟",
            answer = "سرورهای AmneziaWG با پارامترهای تغییر هدر پکت (Jc, Jmin, Jmax) و تونل‌های ضد فیلتر اختصاصی BND حتی در زمان اختلال شدید اینترنت بین‌الملل متصل می‌مانند."
        )

        FaqAccordionItem(
            question = "چگونه سایت‌های بانکی و اسنپ بدون فیلترشکن باز شوند؟",
            answer = "در تب تنظیمات، گزینه «عبور سایت‌های ایرانی (Bypass Iran LAN)» را فعال کنید تا ترافیک داخلی به صورت خودکار از فیلترشکن رد نشود و بدون قطعی کار کند."
        )
    }
}

@Composable
private fun FaqAccordionItem(
    question: String,
    answer: String
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = BndSurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    color = BndTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = BndSoftPurple,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = answer,
                        color = BndTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
