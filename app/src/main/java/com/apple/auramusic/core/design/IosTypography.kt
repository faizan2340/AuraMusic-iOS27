package com.apple.auramusic.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object IosTypography {
    private val SfProFallback = FontFamily.SansSerif

    val LargeTitle = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val Title1 = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).em,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val Title2 = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.015).em,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val Headline = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.01).em,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val Body = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.005).em,
        color = Color.White.copy(alpha = 0.90f),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val Subheadline = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.005).em,
        color = Color.White.copy(alpha = 0.68f),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val TabularTimestamp = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.em,
        fontFeatureSettings = "tnum",
        color = Color.White.copy(alpha = 0.62f),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val LosslessBadge = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.03.em,
        fontFeatureSettings = "tnum",
        color = Color.White.copy(alpha = 0.85f),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val LyricLineActive = TextStyle(
        fontFamily = SfProFallback,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
}
