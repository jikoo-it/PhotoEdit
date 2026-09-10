package com.momi.watermarker.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-specific colors that sit alongside [androidx.compose.material3.ColorScheme].
 * Overlays on photos and the cropper/fullscreen viewer use these so every
 * text and background still comes from the theme.
 */
data class ExtraColors(
    val immersiveBackground: Color,
    val immersiveOnBackground: Color,
    val overlayScrim: Color,
    val overlayContent: Color,
    val cropGuide: Color,
    val checkerLight: Color,
    val checkerDark: Color,
    val onLightSwatch: Color,
    val onDarkSwatch: Color,
)

internal val LightExtraColors = ExtraColors(
    immersiveBackground = Neutral10,
    immersiveOnBackground = Neutral99,
    overlayScrim = Scrim,
    overlayContent = Neutral99,
    cropGuide = Neutral99,
    checkerLight = Neutral94,
    checkerDark = SurfaceVariantLight,
    onLightSwatch = Neutral10,
    onDarkSwatch = Neutral99,
)

internal val DarkExtraColors = ExtraColors(
    immersiveBackground = Neutral12,
    immersiveOnBackground = Neutral90,
    overlayScrim = Scrim,
    overlayContent = Neutral90,
    cropGuide = Neutral90,
    checkerLight = SurfaceVariantDark,
    checkerDark = Neutral12,
    onLightSwatch = Neutral10,
    onDarkSwatch = Neutral99,
)

internal val LocalExtraColors = staticCompositionLocalOf { LightExtraColors }

val MaterialTheme.extraColors: ExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtraColors.current
