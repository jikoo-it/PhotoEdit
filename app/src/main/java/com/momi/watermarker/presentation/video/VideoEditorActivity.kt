package com.momi.watermarker.presentation.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.momi.watermarker.presentation.theme.MomiWaterMarkerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Standalone launcher entry point for the video editor.
 *
 * Deliberately a separate activity (not a route inside the image editor's
 * [com.momi.watermarker.MainActivity]) so the video-editing feature stays fully
 * self-contained and touches no image-processing UI.
 */
@AndroidEntryPoint
class VideoEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomiWaterMarkerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VideoEditorScreen()
                }
            }
        }
    }
}
