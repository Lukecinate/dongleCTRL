package com.suprcharg3d.dongleCTRL.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.suprcharg3d.dongleCTRL.data.ThemeMode

object DongleCtrlColors {
    val ObsidianBg = Color(0xFF0E1013)
    val ObsidianSurface = Color(0xFF171A1F)
    val ObsidianSurfaceElevated = Color(0xFF21252C)
    val ObsidianSurfaceHighest = Color(0xFF2A2F38)
    val ObsidianBorder = Color(0xFF2E3440)
    val TealAccent = Color(0xFF68D8C8)
    val TealContainer = Color(0xFF163834)
    val OffWhiteInk = Color(0xFFF2F4F7)
    val MutedInk = Color(0xFF9AA3B2)
    val AmberWarn = Color(0xFFF5B971)
    val CoralError = Color(0xFFFF7A7A)
}

private val DarkScheme = darkColorScheme(
    primary = DongleCtrlColors.TealAccent,
    onPrimary = Color(0xFF06201D),
    primaryContainer = DongleCtrlColors.TealContainer,
    onPrimaryContainer = Color(0xFFA8F2E6),
    secondary = Color(0xFF9FB4C7),
    onSecondary = Color(0xFF101C26),
    secondaryContainer = DongleCtrlColors.ObsidianSurfaceElevated,
    onSecondaryContainer = DongleCtrlColors.OffWhiteInk,
    background = DongleCtrlColors.ObsidianBg,
    onBackground = DongleCtrlColors.OffWhiteInk,
    surface = DongleCtrlColors.ObsidianSurface,
    onSurface = DongleCtrlColors.OffWhiteInk,
    surfaceVariant = DongleCtrlColors.ObsidianSurfaceElevated,
    onSurfaceVariant = DongleCtrlColors.MutedInk,
    surfaceContainerLow = Color(0xFF13161B),
    surfaceContainer = DongleCtrlColors.ObsidianSurface,
    surfaceContainerHigh = DongleCtrlColors.ObsidianSurfaceElevated,
    surfaceContainerHighest = DongleCtrlColors.ObsidianSurfaceHighest,
    outline = DongleCtrlColors.ObsidianBorder,
    outlineVariant = Color(0xFF252932),
    error = DongleCtrlColors.CoralError
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF12151A),
    surface = Color.White,
    onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFEBEEF3),
    onSurfaceVariant = Color(0xFF5A6577),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2F6),
    outline = Color(0xFFD7DCE4)
)

private val DongleCtrlTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun DongleCtrlTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (isDark) DarkScheme else LightScheme,
        typography = DongleCtrlTypography,
        content = content
    )
}
