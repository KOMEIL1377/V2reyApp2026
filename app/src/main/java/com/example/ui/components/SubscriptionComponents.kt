package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SubscriptionStatus
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
import java.util.Calendar
import java.util.Locale

@Composable
fun SubscriptionStatusCard(
    status: SubscriptionStatus,
    isRefreshing: Boolean,
    refreshButtonLabel: String,
    refreshTestTag: String,
    onRefresh: () -> Unit,
    onLoginClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    isLoggedIn: Boolean = false,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val isBlocked = status.isBlocked(now)
    val remainingDays = status.remainingDays(now)

    val usedText = formatTrafficQuota(status.usedBytes)
    val totalText = if (status.isUnlimitedTraffic) "نامحدود" else formatTrafficQuota(status.totalBytes)
    val remainingText = if (status.isUnlimitedTraffic) "نامحدود" else formatTrafficQuota(status.remainingBytes)

    val expirySummary = when {
        !status.enabled -> "وضعیت کاربر: غیرفعال (منقضی شده)"
        status.isExpired(now) -> "تاریخ انقضا: منقضی شده (${formatJalaliDate(status.expiryTimeMs)})"
        status.isQuotaExhausted -> "حجم ترافیک: منقضی شده (حجم به پایان رسیده)"
        status.isUnlimitedTime -> "مدت زمان: بدون محدودیت زمانی"
        else -> {
            val daysLabel = if (remainingDays != null) "$remainingDays روز باقی‌مانده" else ""
            "$daysLabel • انقضا: ${formatJalaliDate(status.expiryTimeMs)}"
        }
    }

    val accentColor = if (isBlocked) BndErrorRed else BndOrchidTertiary
    val borderBrush = if (isBlocked) {
        Brush.horizontalGradient(listOf(BndErrorRed.copy(alpha = 0.7f), BndVioletPrimary.copy(alpha = 0.4f)))
    } else {
        Brush.horizontalGradient(listOf(BndVioletPrimary.copy(alpha = 0.75f), BndOrchidTertiary.copy(alpha = 0.55f)))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subscription_status_card")
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = BndSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = borderBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Subscription Name + Status Badge + Quick Icon Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Subscription account",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status.title.ifBlank { "اشتراک فعال BND" },
                            color = BndTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = status.source.badgeLabel,
                            color = BndSoftPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .testTag("subscription_state_badge")
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isBlocked) BndErrorRed.copy(alpha = 0.18f)
                                else BndSuccessGreen.copy(alpha = 0.16f)
                            )
                            .border(
                                1.dp,
                                if (isBlocked) BndErrorRed.copy(alpha = 0.6f) else BndSuccessGreen.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isBlocked) "منقضی شده" else "فعال",
                            color = if (isBlocked) BndErrorRed else BndSuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Traffic Usage vs Total + Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مصرف: $usedText / $totalText",
                    color = BndTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "باقی‌مانده: $remainingText",
                    color = if (isBlocked) BndErrorRed else BndNeonPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Custom Gradient Quota Progress Bar
            val barProgress = when {
                isBlocked -> 1f
                status.isUnlimitedTraffic -> 0.18f
                else -> status.usageFraction.coerceIn(0.03f, 1f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BndBgDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isBlocked) {
                                Brush.horizontalGradient(listOf(BndErrorRed, Color(0xFFF87171)))
                            } else {
                                Brush.horizontalGradient(listOf(BndVioletPrimary, BndOrchidTertiary))
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expiry Date / Remaining Days
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (isBlocked) BndErrorRed else BndSoftPurple,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = expirySummary,
                    color = if (isBlocked) BndErrorRed else BndTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isBlocked) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Refresh & Optional Login Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag(refreshTestTag),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BndSurfaceVariantDark,
                        disabledContainerColor = BndSurfaceVariantDark.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = BndOrchidTertiary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = refreshButtonLabel,
                            tint = BndOrchidTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = refreshButtonLabel,
                        color = BndTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isLoggedIn && onLoginClick != null) {
                    OutlinedButton(
                        onClick = onLoginClick,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("card_login_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Login",
                            tint = BndNeonPurple,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ورود حساب", color = BndNeonPurple, fontSize = 11.sp)
                    }
                }

                if (onDeleteClick != null) {
                    OutlinedButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("delete_subscription_button_${status.id}"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = BndErrorRed.copy(alpha = 0.12f),
                            contentColor = BndErrorRed
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف اشتراک",
                            tint = BndErrorRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حذف اشتراک", color = BndErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionSwitcherRow(
    subscriptions: List<SubscriptionStatus>,
    activeSubscriptionId: String?,
    onSelect: (String) -> Unit,
    onDelete: (SubscriptionStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    if (subscriptions.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subscriptions, key = { it.id }) { sub ->
            val isActive = sub.id == activeSubscriptionId
            val isBlocked = sub.isBlocked()
            val dotColor = if (isBlocked) BndErrorRed else BndSuccessGreen
            val displayTitle = sub.title.ifBlank {
                if (sub.email.isNotBlank()) sub.email else "اشتراک"
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) BndVioletPrimary.copy(alpha = 0.24f) else BndSurfaceCard)
                    .border(
                        width = 1.dp,
                        color = when {
                            isActive -> BndOrchidTertiary
                            isBlocked -> BndErrorRed.copy(alpha = 0.45f)
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(sub.id) }
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("subscription_chip_${sub.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.padding(end = 4.dp)) {
                    Text(
                        text = displayTitle,
                        color = if (isActive) Color.White else BndTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isBlocked) "منقضی شده" else "فعال",
                        color = dotColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = { onDelete(sub) },
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("delete_sub_item_${sub.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف اشتراک",
                        tint = BndErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteSubscriptionConfirmDialog(
    status: SubscriptionStatus,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val label = status.title.ifBlank {
        if (status.email.isNotBlank()) status.email else "این اشتراک"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BndSurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = BndErrorRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "حذف اشتراک",
                    color = BndTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = "آیا از حذف اشتراک «$label» مطمئن هستید؟ تمامی سرورهای همگام‌شده با این اشتراک حذف می‌شوند و سایر اشتراک‌ها و کانفیگ‌های دستی شما بدون تغییر باقی می‌مانند.",
                color = BndTextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BndErrorRed),
                modifier = Modifier.testTag("confirm_delete_subscription_button")
            ) {
                Text("حذف اشتراک", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = BndTextSecondary)
            }
        }
    )
}

@Composable
fun AccountLoginPromptBanner(
    onLoginClick: () -> Unit,
    onRefreshClick: (() -> Unit)? = null,
    refreshLabel: String = "بروزرسانی سرور / اشتراک",
    showRefresh: Boolean = false,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("login_prompt_banner")
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onLoginClick),
        colors = CardDefaults.cardColors(containerColor = BndSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(BndVioletPrimary.copy(alpha = 0.55f), BndOrchidTertiary.copy(alpha = 0.35f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(BndVioletPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Account login",
                            tint = BndOrchidTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ورود به حساب کاربری BND",
                            color = BndTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ورود با نام کاربری و Subscription ID یا استفاده از کانفیگ دستی",
                            color = BndTextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BndVioletPrimary)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "ورود",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (showRefresh && onRefreshClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRefreshClick,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = BndOrchidTertiary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = BndOrchidTertiary, modifier = Modifier.size(15.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(refreshLabel, color = BndOrchidTertiary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun AccountLoginDialog(
    initialEmail: String = "",
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onLoginSubmit: (email: String, subId: String) -> Unit
) {
    var emailInput by remember { mutableStateOf(initialEmail) }
    var subIdInput by remember { mutableStateOf("") }
    var showSubId by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = BndSurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = BndOrchidTertiary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ورود به حساب اشتراک BND",
                    color = BndTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "نام کاربری (Email) و کد اشتراک (Subscription ID) خود را وارد کنید تا حجم، تاریخ انقضا و سرورهای شما بصورت خودکار همگام شوند.",
                    color = BndTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("نام کاربری (Email)", fontSize = 12.sp) },
                    placeholder = { Text("مثال: Kamibnd1", color = BndTextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = BndSoftPurple)
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_email_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BndSurfaceCard,
                        unfocusedContainerColor = BndSurfaceCard,
                        focusedBorderColor = BndVioletPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = BndTextPrimary,
                        unfocusedTextColor = BndTextPrimary,
                        focusedLabelColor = BndOrchidTertiary,
                        unfocusedLabelColor = BndTextMuted
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = subIdInput,
                    onValueChange = { subIdInput = it },
                    label = { Text("رمز عبور (Subscription ID)", fontSize = 12.sp) },
                    placeholder = { Text("Subscription ID اشتراک", color = BndTextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = BndSoftPurple)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showSubId = !showSubId }) {
                            Icon(
                                imageVector = if (showSubId) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility",
                                tint = BndTextMuted
                            )
                        }
                    },
                    visualTransformation = if (showSubId) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_subid_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BndSurfaceCard,
                        unfocusedContainerColor = BndSurfaceCard,
                        focusedBorderColor = BndVioletPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = BndTextPrimary,
                        unfocusedTextColor = BndTextPrimary,
                        focusedLabelColor = BndOrchidTertiary,
                        unfocusedLabelColor = BndTextMuted
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_error_box")
                            .clip(RoundedCornerShape(10.dp))
                            .background(BndErrorRed.copy(alpha = 0.14f))
                            .border(1.dp, BndErrorRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = BndErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLoginSubmit(emailInput, subIdInput) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
                modifier = Modifier.testTag("confirm_login_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("ورود و دریافت سرورها", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("انصراف", color = BndTextSecondary)
            }
        }
    )
}

fun formatTrafficQuota(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        safe >= gb -> String.format(Locale.US, "%.2f GB", safe / gb)
        safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
        safe >= kb -> String.format(Locale.US, "%.0f KB", safe / kb)
        else -> "$safe B"
    }
}

fun formatJalaliDate(epochMs: Long): String {
    if (epochMs <= 0L) return "نامحدود"
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val gy = cal.get(Calendar.YEAR)
    val gm = cal.get(Calendar.MONTH) + 1
    val gd = cal.get(Calendar.DAY_OF_MONTH)
    val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
    return String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
}

internal fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + (days / 31)
        jd = 1 + (days % 31)
    } else {
        jm = 7 + ((days - 186) / 30)
        jd = 1 + ((days - 186) % 30)
    }
    return Triple(jy, jm, jd)
}
