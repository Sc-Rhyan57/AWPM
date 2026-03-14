package com.rhyan57.awp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object AwpColors {
    val Background   = Color(0xFF0A0A0F)
    val Surface      = Color(0xFF13131A)
    val SurfaceVar   = Color(0xFF1C1C26)
    val Border       = Color(0xFF2A2A3A)
    val Primary      = Color(0xFF6C63FF)
    val PrimaryDim   = Color(0xFF4A43CC)
    val Accent       = Color(0xFF00D4FF)
    val Success      = Color(0xFF00C853)
    val Warning      = Color(0xFFFFAB00)
    val Error        = Color(0xFFFF5252)
    val TextPrimary  = Color(0xFFEEEEFF)
    val TextSecondary= Color(0xFF9999BB)
    val TextMuted    = Color(0xFF555570)
    val OnPrimary    = Color(0xFFFFFFFF)
    val Roblox       = Color(0xFFE02020)
}

val AwpColorScheme = darkColorScheme(
    primary        = AwpColors.Primary,
    onPrimary      = AwpColors.OnPrimary,
    background     = AwpColors.Background,
    surface        = AwpColors.Surface,
    surfaceVariant = AwpColors.SurfaceVar,
    onBackground   = AwpColors.TextPrimary,
    onSurface      = AwpColors.TextPrimary,
    error          = AwpColors.Error
)
