package com.example.ui.screens

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.model.StorePlan
import com.example.ui.components.BndTopHeaderBar
import com.example.ui.theme.BndBgDark
import com.example.ui.theme.BndNeonPurple
import com.example.ui.theme.BndOrchidTertiary
import com.example.ui.theme.BndSoftPurple
import com.example.ui.theme.BndSuccessGreen
import com.example.ui.theme.BndSurfaceCard
import com.example.ui.theme.BndSurfaceVariantDark
import com.example.ui.theme.BndTextMuted
import com.example.ui.theme.BndTextPrimary
import com.example.ui.theme.BndTextSecondary
import com.example.ui.theme.BndVioletPrimary
import com.example.ui.theme.BndVipBannerGradient
import com.example.ui.theme.BndWarningYellow
import com.example.viewmodel.MainViewModel

@Composable
fun StoreScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val plans = viewModel.storePlans
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BndBgDark)
    ) {
        // Prominent Header with BND Logo
        BndTopHeaderBar(subtitle = "خرید اشتراک BND • @bndteam_bot")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Store Banner
            StoreHeroBanner()

            Spacer(modifier = Modifier.height(18.dp))

            // Direct Official Telegram Sales Bot CTA Button -> https://t.me/bndteam_bot
            Button(
                onClick = { viewModel.openTelegramUrl(context, BndTelegramLinks.STORE_BOT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("open_telegram_bot_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Telegram",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ورود به ربات تلگرام خرید اشتراک (${BndTelegramLinks.STORE_BOT_HANDLE})",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تعرفه‌های اشتراک اختصاصی BND",
                    color = BndTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تحویل آنی در ربات",
                    color = BndNeonPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Plans List
            plans.forEach { plan ->
                StorePlanCard(
                    plan = plan,
                    onOrderClick = {
                        viewModel.openTelegramUrl(context, BndTelegramLinks.STORE_BOT)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Features & Guarantee Trust Card
            TrustAndPaymentCard(
                onOpenSupport = { viewModel.openTelegramUrl(context, BndTelegramLinks.SUPPORT_ADMIN) }
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StoreHeroBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BndVipBannerGradient)
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "BNDTeam • THE FASTEST VPN",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "VIP",
                        tint = BndWarningYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "اینترنت آزاد، بدون قطعی و با پینگ زیر ۸۰",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 26.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "پشتیبانی از پروتکل‌های نسل جدید VLESS Reality، Hysteria 2، TUIC و AmneziaWG سازگار با تمامی اپراتورهای همراه اول، ایرانسل و مخابرات.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun StorePlanCard(
    plan: StorePlan,
    onOrderClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan_card_${plan.id}")
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (plan.isPopular) BndSurfaceVariantDark else BndSurfaceCard
        ),
        border = if (plan.isPopular) {
            CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(BndOrchidTertiary, BndVioletPrimary)
                )
            )
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = plan.title,
                        color = BndTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${plan.durationText} • ${plan.trafficText}",
                        color = BndNeonPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (plan.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (plan.isPopular) BndOrchidTertiary else BndVioletPrimary
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = plan.badge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = plan.priceTomans,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${plan.priceUsd})",
                        color = BndTextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Button(
                    onClick = onOrderClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (plan.isPopular) BndOrchidTertiary else BndVioletPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("خرید در ربات", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = BndSuccessGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = feature,
                            color = BndTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustAndPaymentCard(
    onOpenSupport: () -> Unit
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
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = BndSuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "روش‌های پرداخت و تضمین بازگشت وجه",
                    color = BndTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "• پرداخت ریالی آسان از طریق کارت به کارت و درگاه شتاب\n• پرداخت رمزارز (USDT تتر، ترون TRX، ارز TON)\n• ضمانت بازگشت وجه کامل تا ۴۸ ساعت در صورت بروز هرگونه اختلال\n• پشتیبانی ۲۴ ساعته مدیریت از طریق ${BndTelegramLinks.SUPPORT_ADMIN_HANDLE}",
                color = BndTextSecondary,
                fontSize = 12.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenSupport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BndSurfaceVariantDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = BndSoftPurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ارتباط با مدیریت و پشتیبانی (${BndTelegramLinks.SUPPORT_ADMIN_HANDLE})", color = BndSoftPurple, fontSize = 12.sp)
            }
        }
    }
}
