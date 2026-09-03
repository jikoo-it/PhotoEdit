package com.momi.watermarker.presentation.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * A Media3 [ExoPlayer] preview of [uri], embedded via [AndroidView]. Shows
 * [placeholder] text when no clip is loaded. The player is released when the
 * composable leaves the composition.
 */
@Composable
fun VideoPreview(
    uri: String?,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose a video to begin.",
) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(uri) {
        if (uri != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
        } else {
            exoPlayer.clearMediaItems()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (uri != null) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = placeholder,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** Formats a millisecond duration as `m:ss`. */
fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
