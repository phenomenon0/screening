package com.screening.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DarkBg = Color(0xFF0D0D0D)
val Surface = Color(0xFF1A1A2E)
val Card = Color(0xFF16213E)
val Cyan = Color(0xFF00D2FF)
val Green = Color(0xFF00E676)
val Red = Color(0xFFFF5252)
val Amber = Color(0xFFFFAB40)
val TextW = Color(0xFFE8E8E8)
val TextG = Color(0xFFA0A0A0)
val Dim = Color(0xFF606060)

object MobileType {
    val h1 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextW)
    val h2 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = TextW)
    val body = TextStyle(fontSize = 16.sp, color = TextG)
    val label = TextStyle(fontSize = 12.sp, color = Dim)
}
