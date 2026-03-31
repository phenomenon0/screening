package com.screening.dashboard.ui.theme

import androidx.compose.ui.graphics.Color

// Surface hierarchy — tonal layering for depth
val Background = Color(0xFF131313)
val Surface = Color(0xFF131313)
val SurfaceContainerLowest = Color(0xFF0E0E0E)
val SurfaceContainerLow = Color(0xFF1C1B1B)
val SurfaceContainer = Color(0xFF201F1F)
val SurfaceContainerHigh = Color(0xFF2A2A2A)
val SurfaceContainerHighest = Color(0xFF353534)
val SurfaceBright = Color(0xFF3A3939)
val SurfaceVariant = Color(0xFF353534)

// Text
val OnSurface = Color(0xFFE5E2E1)
val OnSurfaceVariant = Color(0xFFBBC9CF)
val Outline = Color(0xFF859399)
val OutlineVariant = Color(0xFF3C494E)

// Primary (cyan spectrum)
val Primary = Color(0xFFA5E7FF)
val PrimaryContainer = Color(0xFF00D2FF)
val PrimaryFixed = Color(0xFFB6EBFF)
val PrimaryFixedDim = Color(0xFF47D6FF)
val OnPrimary = Color(0xFF003543)

// Secondary (blue spectrum)
val Secondary = Color(0xFFA9C8FC)
val SecondaryContainer = Color(0xFF294A77)

// Tertiary (green spectrum)
val Tertiary = Color(0xFF3BFC89)
val TertiaryContainer = Color(0xFF00DE72)

// Error (red spectrum)
val Error = Color(0xFFFFB4AB)
val ErrorContainer = Color(0xFF93000A)

// Amber (warnings)
val Amber = Color(0xFFFFAB40)

// ── Legacy aliases (for existing code compatibility) ──
val DarkBackground = Background
val DarkSurface = SurfaceContainer
val DarkCard = SurfaceContainerHigh
val AccentBlue = SecondaryContainer
val AccentCyan = PrimaryContainer
val AccentGreen = TertiaryContainer
val AccentRed = Color(0xFFFF5252)
val AccentAmber = Amber
val TextPrimary = OnSurface
val TextSecondary = OnSurfaceVariant
val TextDim = Outline
