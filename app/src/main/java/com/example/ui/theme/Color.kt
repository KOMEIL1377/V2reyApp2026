package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// V2reyBND Modern Purple & Dark Violet Palette
val BndBgDark = Color(0xFF0A0118)
val BndSurfaceDark = Color(0xFF140529)
val BndSurfaceVariantDark = Color(0xFF200B3E)
val BndSurfaceCard = Color(0xFF1A0933)
val BndSurfaceCardHover = Color(0xFF261048)

val BndVioletPrimary = Color(0xFF8A2BE2)     // Blue Violet
val BndPurpleSecondary = Color(0xFF9370DB)   // Medium Purple
val BndOrchidTertiary = Color(0xFFDA70D6)    // Orchid / Magenta highlight
val BndNeonPurple = Color(0xFFC084FC)        // Bright purple glow
val BndSoftPurple = Color(0xFFE9D5FF)

val BndSuccessGreen = Color(0xFF10B981)
val BndWarningYellow = Color(0xFFFBBF24)
val BndErrorRed = Color(0xFFF43F5E)
val BndInfoBlue = Color(0xFF38BDF8)

val BndTextPrimary = Color(0xFFF8F5FF)
val BndTextSecondary = Color(0xFFC7B8E3)
val BndTextMuted = Color(0xFF8C7BA6)

// Gradients
val BndPowerGradientActive = Brush.linearGradient(
    colors = listOf(Color(0xFF8A2BE2), Color(0xFFDA70D6), Color(0xFF00F0FF))
)

val BndPowerGradientInactive = Brush.linearGradient(
    colors = listOf(Color(0xFF3B1566), Color(0xFF240A42), Color(0xFF18052B))
)

val BndCardGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF1D0A38), Color(0xFF130426))
)

val BndVipBannerGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF7C3AED), Color(0xFFC026D3), Color(0xFF9333EA))
)

val BndPingGood = Color(0xFF10B981)
val BndPingMedium = Color(0xFFF59E0B)
val BndPingPoor = Color(0xFFEF4444)
