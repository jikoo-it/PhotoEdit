package com.momi.watermarker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.momi.watermarker.presentation.editor.EditorScreen
import com.momi.watermarker.presentation.theme.MomiWaterMarkerTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host for the Compose UI. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomiWaterMarkerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EditorScreen()
                }
            }
        }
    }
}
