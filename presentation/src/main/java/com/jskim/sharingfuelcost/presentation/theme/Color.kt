package com.jskim.sharingfuelcost.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 시안 D "답부터 보기" 토큰. docs/ui-ux.md와 함께 관리한다.
internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF07704B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6F5E6),
    onPrimaryContainer = Color(0xFF00311F),
    secondary = Color(0xFF56605B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7EBE9),
    onSecondaryContainer = Color(0xFF1B211E),
    background = Color(0xFFF1F3F2),
    onBackground = Color(0xFF181C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181C1A),
    surfaceVariant = Color(0xFFE7EBE9),
    onSurfaceVariant = Color(0xFF4F5853),
    outline = Color(0xFF7A837E),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ED8A2),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005235),
    onPrimaryContainer = Color(0xFFB8F3D8),
    secondary = Color(0xFFBAC4BF),
    onSecondary = Color(0xFF29312D),
    secondaryContainer = Color(0xFF2C3330),
    onSecondaryContainer = Color(0xFFE1E7E4),
    background = Color(0xFF0C0F0E),
    onBackground = Color(0xFFE8ECEA),
    surface = Color(0xFF171B19),
    onSurface = Color(0xFFE8ECEA),
    surfaceVariant = Color(0xFF2C3330),
    onSurfaceVariant = Color(0xFFB9C2BD),
    outline = Color(0xFF868F8A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
