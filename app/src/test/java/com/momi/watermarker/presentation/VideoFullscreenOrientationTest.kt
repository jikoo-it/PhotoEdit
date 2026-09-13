package com.momi.watermarker.presentation

import android.content.pm.ActivityInfo
import com.momi.watermarker.presentation.video.fullscreenOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFullscreenOrientationTest {

    @Test
    fun landscapeVideoLocksToLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            fullscreenOrientation(1920, 1080),
        )
    }

    @Test
    fun portraitVideoLocksToPortrait() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            fullscreenOrientation(1080, 1920),
        )
    }

    @Test
    fun anamorphicWideLocksToLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            fullscreenOrientation(720, 720, pixelWidthHeightRatio = 2f),
        )
    }

    @Test
    fun squareLeavesUnspecified() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            fullscreenOrientation(1080, 1080),
        )
    }

    @Test
    fun unknownLeavesUnspecified() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            fullscreenOrientation(0, 0),
        )
    }
}
