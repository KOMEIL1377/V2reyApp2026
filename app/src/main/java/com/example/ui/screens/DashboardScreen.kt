package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BndTelegramLinks
import com.example.model.NetworkTrafficStats
import com.example.model.ProxyNode
import com.example.model.VpnState
import com.example.ui.components.BndTopHeaderBar
import com.example.ui.theme.BndBgDark
import com.example.ui.theme.BndErrorRed
import com.example.ui.theme.BndNeonPurple
import com.example.ui.theme.BndOrchidTertiary
import com.example.ui.theme.BndPingGood
import com.example.ui.theme.BndPingMedium
import com.example.ui.theme.BndPingPoor
import com.example.ui.theme.BndPowerGradientActive
import com.example.ui.theme.BndPowerGradientInactive
import com.example.ui.theme.BndPurpleSecondary
import com.example.ui.theme.BndSoftPurple
import com.example.ui.theme.BndSuccessGreen
import com.example.ui.theme.BndSurfaceCard
import com.example.ui.theme.BndSurfaceVariantDark
import com.example.ui.theme.BndTextMuted
import com.example.ui.theme.BndTextPrimary
import com.example.ui.theme.BndTextSecondary
import com.example.ui.theme.BndVioletPrimary
import com.example.ui.theme.BndWarningYellow
import com.example.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToServers: () -> Unit,
    onRequestVpnPermission: (android.content.Intent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vpnState by viewModel.vpnState.collectAsState()
    val selectedNode by viewModel.selectedNode.collectAsState()
    val trafficStats by viewModel.trafficStats.collectAsState()
    val isTestingAll by viewModel.isTestingAllPings.collectAsState()
    val nodes by viewModel.nodes.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BndBgDark)
    ) {
        // Prominent Header with BND Logo
        BndTopHeaderBar(
            subtitle = "Multi-Protocol • The Fastest VPN",
            actions = {
                if (nodes.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.testAllPings() },
                        modifier = Modifier
                            .testTag("test_all_pings_header_button")
                            .clip(CircleShape)
                            .background(BndSurfaceCard)
                            .size(38.dp)
                    ) {
                        if (isTestingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = BndOrchidTertiary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Test all pings",
                                tint = BndSoftPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onNavigateToServers,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BndVioletPrimary)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Server",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selected Server Card / Empty state banner
            SelectedServerCard(
                node = selectedNode,
                onCardClick = onNavigateToServers,
                onTestPing = { selectedNode?.let { viewModel.testSinglePing(it) } }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Central Glowing Big Power Button
            GlowingPowerButton(
                vpnState = vpnState,
                hasNodes = nodes.isNotEmpty(),
                onClick = {
                    viewModel.toggleConnection(context, onRequestVpnPermission)
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Connection Status Banner
            ConnectionStatusPill(vpnState = vpnState, hasNodes = nodes.isNotEmpty())

            Spacer(modifier = Modifier.height(22.dp))

            // Real-Time Live Traffic & Speed Cards
            TrafficDashboardSection(
                vpnState = vpnState,
                stats = trafficStats
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Accurate Telegram Bot Store & Support Action Cards
            QuickStoreAndSupportBanners(
                onOpenBot = { viewModel.openTelegramUrl(context, BndTelegramLinks.STORE_BOT) },
                onOpenSupport = { viewModel.openTelegramUrl(context, BndTelegramLinks.SUPPORT_ADMIN) }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SelectedServerCard(
    node: ProxyNode?,
    onCardClick: () -> Unit,
    onTestPing: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_server_card")
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(containerColor = BndSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(BndVioletPrimary.copy(alpha = 0.5f), BndOrchidTertiary.copy(alpha = 0.3f))
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
            if (node == null) {
                // Empty state on Dashboard
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BndVioletPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = BndOrchidTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "سروری اضافه نشده است",
                            color = BndTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "برای افزودن کانفیگ یا سابسکریپشن لمس کنید",
                            color = BndNeonPurple,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "افزودن",
                        color = BndOrchidTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Add server",
                        tint = BndOrchidTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = node.flag,
                        fontSize = 30.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.name,
                            color = BndTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(node.protocol.badgeColorHex).copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = node.protocol.displayName,
                                    color = Color(node.protocol.badgeColorHex),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            PingBadge(
                                pingMs = node.pingMs,
                                isTesting = node.isTesting,
                                onClick = onTestPing
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "تغییر",
                        color = BndOrchidTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Change server",
                        tint = BndOrchidTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PingBadge(
    pingMs: Long?,
    isTesting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (color, text) = when {
        isTesting -> Pair(BndWarningYellow, "تست…")
        pingMs == null -> Pair(BndTextMuted, "پینگ")
        pingMs < 0 -> Pair(BndPingPoor, "قطع")
        pingMs < 150 -> Pair(BndPingGood, "${pingMs}ms")
        pingMs < 300 -> Pair(BndPingMedium, "${pingMs}ms")
        else -> Pair(BndPingPoor, "${pingMs}ms")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GlowingPowerButton(
    vpnState: VpnState,
    hasNodes: Boolean,
    onClick: () -> Unit
) {
    val isConnected = vpnState == VpnState.CONNECTED
    val isConnecting = vpnState == VpnState.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "power_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected || isConnecting) 1.14f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isConnected || isConnecting) 0.6f else 0f,
        targetValue = if (isConnected || isConnecting) 0.1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .size(230.dp)
            .testTag("vpn_power_button_container"),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected || isConnecting) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                (if (isConnected) BndOrchidTertiary else BndWarningYellow).copy(alpha = pulseAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    if (isConnected) {
                        Brush.sweepGradient(
                            listOf(
                                BndVioletPrimary,
                                BndOrchidTertiary,
                                Color(0xFF00E5FF),
                                BndVioletPrimary
                            )
                        )
                    } else if (isConnecting) {
                        Brush.sweepGradient(
                            listOf(
                                BndWarningYellow,
                                BndVioletPrimary,
                                BndWarningYellow
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                BndSurfaceVariantDark,
                                Color(0xFF190630)
                            )
                        )
                    }
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (isConnected) BndPowerGradientActive else BndPowerGradientInactive
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(54.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "VPN Power",
                            tint = if (isConnected) Color.White else BndTextSecondary,
                            modifier = Modifier.size(58.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            vpnState == VpnState.CONNECTED -> "قطع اتصال"
                            vpnState == VpnState.CONNECTING -> "در حال اتصال…"
                            vpnState == VpnState.RECONNECTING -> "مجدد…"
                            !hasNodes -> "افزودن سرور"
                            else -> "شروع اتصال"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(vpnState: VpnState, hasNodes: Boolean) {
    val (pillColor, text) = when {
        vpnState == VpnState.CONNECTED -> Pair(BndSuccessGreen, "متصل شد • تونل امن و ضد فیلتر فعال است")
        vpnState == VpnState.CONNECTING -> Pair(BndWarningYellow, "در حال اتصال و برقراری ارتباط ایمن…")
        vpnState == VpnState.RECONNECTING -> Pair(BndWarningYellow, "در حال اتصال مجدد به سرور…")
        vpnState == VpnState.FAILED -> Pair(BndErrorRed, "اتصال ناموفق • لطفاً سرور را بررسی کنید")
        !hasNodes -> Pair(BndVioletPrimary, "حالت کلاینت خام • لطفاً کانفیگ اضافه کنید")
        else -> Pair(BndSurfaceVariantDark, "قطع اتصال • برای فعال‌سازی لمس کنید")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(pillColor.copy(alpha = if (vpnState == VpnState.DISCONNECTED && hasNodes) 0.4f else 0.2f))
            .border(
                1.dp,
                pillColor.copy(alpha = if (vpnState == VpnState.DISCONNECTED && hasNodes) 0.3f else 0.8f),
                RoundedCornerShape(30.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(pillColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (vpnState == VpnState.DISCONNECTED && hasNodes) BndTextSecondary else pillColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TrafficDashboardSection(
    vpnState: VpnState,
    stats: NetworkTrafficStats
) {
    val isConnected = vpnState == VpnState.CONNECTED

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrafficCard(
                title = "سرعت دانلود",
                speedText = if (isConnected) formatSpeed(stats.downloadSpeedBytes) else "0.0 KB/s",
                totalText = if (isConnected) "کل: ${formatBytes(stats.totalDownloadBytes)}" else "کل: 0 MB",
                icon = Icons.Default.Download,
                iconColor = BndSuccessGreen,
                modifier = Modifier.weight(1f)
            )

            TrafficCard(
                title = "سرعت آپلود",
                speedText = if (isConnected) formatSpeed(stats.uploadSpeedBytes) else "0.0 KB/s",
                totalText = if (isConnected) "کل: ${formatBytes(stats.totalUploadBytes)}" else "کل: 0 MB",
                icon = Icons.Default.Upload,
                iconColor = BndOrchidTertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BndSurfaceCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Duration",
                        tint = BndSoftPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مدت اتصال: " + formatDuration(stats.connectedDurationSeconds),
                        color = BndTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Protocol",
                        tint = BndSuccessGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BND Anti-DPI Active",
                        color = BndSuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficCard(
    title: String,
    speedText: String,
    totalText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BndSurfaceCard),
        shape = RoundedCornerShape(16.dp)
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
                    text = title,
                    color = BndTextMuted,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = speedText,
                color = BndTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = totalText,
                color = BndTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun QuickStoreAndSupportBanners(
    onOpenBot: () -> Unit,
    onOpenSupport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Store Bot Promo Card -> https://t.me/bndteam_bot
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("quick_bot_card")
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenBot),
            colors = CardDefaults.cardColors(containerColor = BndSurfaceVariantDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(BndVioletPrimary, BndOrchidTertiary)
                )
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "VIP",
                        tint = BndOrchidTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ربات خرید اشتراک",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = BndTelegramLinks.STORE_BOT_HANDLE,
                    color = BndNeonPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Support Admin Card -> https://t.me/kingV2rey
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("quick_support_card")
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenSupport),
            colors = CardDefaults.cardColors(containerColor = BndSurfaceVariantDark),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(BndPurpleSecondary, BndVioletPrimary)
                )
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Admin",
                        tint = BndSoftPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "پشتیبانی فنی BND",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = BndTelegramLinks.SUPPORT_ADMIN_HANDLE,
                    color = BndSoftPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1_048_576 -> String.format("%.1f MB/s", bytesPerSec / 1_048_576.0)
        bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
