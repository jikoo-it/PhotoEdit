package com.momi.watermarker.presentation.video

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.momi.watermarker.R
import com.momi.watermarker.presentation.theme.extraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * A Media3 [ExoPlayer] preview of [uri], embedded via [AndroidView]. Shows
 * [placeholder] text when no clip is loaded. The player is released when the
 * composable leaves the composition. The stock settings (playback-speed) icon
 * is hidden; a fullscreen control on the player expands the preview.
 */
@Composable
fun VideoPreview(
    uri: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isFullscreen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(uri) {
        if (uri == null) isFullscreen = false
        if (uri != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = autoPlay
        } else {
            exoPlayer.clearMediaItems()
            positionMs = 0L
            durationMs = 0L
        }
    }

    LaunchedEffect(uri, exoPlayer) {
        if (uri == null) return@LaunchedEffect
        while (isActive) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val probed = exoPlayer.duration
            durationMs = if (probed != C.TIME_UNSET && probed > 0L) probed else 0L
            delay(32)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (uri != null) {
            if (isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                )
            } else {
                PlayerSurface(
                    exoPlayer = exoPlayer,
                    isFullscreen = false,
                    onFullscreenChange = { isFullscreen = it },
                    modifier = Modifier.fillMaxSize(),
                )
                TimecodeBadge(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        } else {
            Text(
                text = placeholder ?: stringResource(R.string.choose_video_to_begin),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    if (isFullscreen && uri != null) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val extras = MaterialTheme.extraColors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(extras.immersiveBackground)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center,
            ) {
                PlayerSurface(
                    exoPlayer = exoPlayer,
                    isFullscreen = true,
                    onFullscreenChange = { isFullscreen = it },
                    modifier = Modifier.fillMaxSize(),
                )
                TimecodeBadge(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

@Composable
private fun PlayerSurface(
    exoPlayer: ExoPlayer,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                applyPreviewChrome(isFullscreen, onFullscreenChange)
            }
        },
        update = { view ->
            view.player = exoPlayer
            view.applyPreviewChrome(isFullscreen, onFullscreenChange)
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

@Composable
private fun TimecodeBadge(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(
            R.string.preview_timecode,
            formatTimecode(positionMs),
            formatTimecode(durationMs),
        ),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@OptIn(UnstableApi::class)
private fun PlayerView.applyPreviewChrome(
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
) {
    findViewById<View>(androidx.media3.ui.R.id.exo_position)?.visibility = View.GONE
    findViewById<View>(androidx.media3.ui.R.id.exo_duration)?.visibility = View.GONE
    findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
    setFullscreenButtonClickListener { onFullscreenChange(it) }
    setFullscreenButtonState(isFullscreen)
}

/** Formats a millisecond offset as `m:ss.SSS` (or `h:mm:ss.SSS`). */
fun formatMs(ms: Long): String = formatTimecode(ms)
