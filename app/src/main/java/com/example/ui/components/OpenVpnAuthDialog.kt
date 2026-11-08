package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BndNeonPurple
import com.example.ui.theme.BndOrchidTertiary
import com.example.ui.theme.BndSoftPurple
import com.example.ui.theme.BndSurfaceCard
import com.example.ui.theme.BndSurfaceDark
import com.example.ui.theme.BndTextMuted
import com.example.ui.theme.BndTextPrimary
import com.example.ui.theme.BndTextSecondary
import com.example.ui.theme.BndVioletPrimary

@Composable
fun OpenVpnAuthDialog(
    profileName: String,
    onDismiss: () -> Unit,
    onConfirm: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BndSurfaceDark,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BndVioletPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = BndOrchidTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "احراز هویت OpenVPN",
                        color = BndTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "دستور auth-user-pass شناسایی شد",
                        color = BndNeonPurple,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "کانفیگ «$profileName» برای اتصال نیازمند نام کاربری و کلمه عبور است. لطفاً اطلاعات حساب را وارد کنید:",
                    color = BndTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("openvpn_username_input"),
                    label = { Text("نام کاربری (Username)", color = BndTextMuted, fontSize = 12.sp) },
                    placeholder = { Text("مثلاً: user123", color = BndTextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = BndSoftPurple)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BndSurfaceCard,
                        unfocusedContainerColor = BndSurfaceCard,
                        focusedBorderColor = BndVioletPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = BndTextPrimary,
                        unfocusedTextColor = BndTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("openvpn_password_input"),
                    label = { Text("کلمه عبور (Password)", color = BndTextMuted, fontSize = 12.sp) },
                    placeholder = { Text("رمز عبور اکانت OpenVPN", color = BndTextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = BndSoftPurple)
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BndSurfaceCard,
                        unfocusedContainerColor = BndSurfaceCard,
                        focusedBorderColor = BndVioletPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = BndTextPrimary,
                        unfocusedTextColor = BndTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(username.trim(), password.trim())
                },
                modifier = Modifier.testTag("openvpn_auth_confirm_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BndVioletPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تایید و ذخیره کانفیگ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = BndTextSecondary)
            }
        }
    )
}
