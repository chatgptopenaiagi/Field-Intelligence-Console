package com.fieldconsole.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Colour palette — dark, high-contrast, industrial ─────────────────────────

private val FicBackground   = Color(0xFF0A0A0A)
private val FicSurface      = Color(0xFF141414)
private val FicSurfaceVar   = Color(0xFF1E1E1E)
private val FicOnBackground = Color(0xFFE8E8E8)
private val FicOnSurface    = Color(0xFFD0D0D0)
private val FicPrimary      = Color(0xFF4FC3F7)   // Cyan-blue — SCAN button
private val FicOnPrimary    = Color(0xFF001F2A)
private val FicSecondary    = Color(0xFF80CBC4)
private val FicOnSecondary  = Color(0xFF00201E)
private val FicError        = Color(0xFFFF5252)
private val FicOnError      = Color(0xFF690005)
private val FicOutline      = Color(0xFF3A3A3A)

// Action Matrix button colours
val ColorInfoScan   = Color(0xFF1565C0)  // Blue
val ColorHistory    = Color(0xFF2E7D32)  // Green
val ColorNavigation = Color(0xFFF9A825)  // Yellow
val ColorAiAnalysis = Color(0xFF6A1B9A)  // Purple
val ColorRiskSafety = Color(0xFFC62828)  // Red
val ColorLogistics  = Color(0xFFE65100)  // Orange
val ColorFinancial  = Color(0xFF37474F)  // Dark Gray
val ColorEmergency  = Color(0xFFEEEEEE)  // Near-white

private val FicDarkColorScheme = darkColorScheme(
    primary          = FicPrimary,
    onPrimary        = FicOnPrimary,
    secondary        = FicSecondary,
    onSecondary      = FicOnSecondary,
    background       = FicBackground,
    onBackground     = FicOnBackground,
    surface          = FicSurface,
    onSurface        = FicOnSurface,
    surfaceVariant   = FicSurfaceVar,
    onSurfaceVariant = FicOnSurface,
    error            = FicError,
    onError          = FicOnError,
    outline          = FicOutline
)

@Composable
fun FieldIntelligenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FicDarkColorScheme,
        typography  = FicTypography,
        content     = content
    )
}
