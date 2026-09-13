package com.momi.watermarker.presentation.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.momi.watermarker.R
import com.momi.watermarker.domain.model.normalizeRotationDegrees
import com.momi.watermarker.presentation.theme.extraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * A Media3 [ExoPlayer] preview of [uri], embedded via [AndroidView]. Shows
 * [placeholder] text when no clip is loaded. The player is released when the
 * composable leaves the composition. The stock settings (playback-speed) icon
 * is hidden; a fullscreen control on the player expands the preview and locks
 * the screen to landscape or portrait from the clip's aspect ratio.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(
    uri: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    autoPlay: Boolean = false,
    rotationDegrees: Int = 0,
) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var pixelRatio by remember { mutableFloatStateOf(1f) }
    val fullscreenState = remember { mutableStateOf(false) }
    var isFullscreen by fullscreenState
    val onFullscreenChange = remember(fullscreenState) {
        { entering: Boolean -> fullscreenState.value = entering }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    DisposableEffect(exoPlayer, rotationDegrees) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
                pixelRatio = videoSize.pixelWidthHeightRatio
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    applyPreviewRotation(exoPlayer, rotationDegrees)
                }
            }
        }
        exoPlayer.addListener(listener)
        val current = exoPlayer.videoSize
        videoWidth = current.width
        videoHeight = current.height
        pixelRatio = current.pixelWidthHeightRatio
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(isFullscreen) {
        val activity = context.findActivity()
        if (!isFullscreen || activity == null) {
            return@DisposableEffect onDispose { }
        }
        val previous = activity.requestedOrientation
        onDispose { activity.requestedOrientation = previous }
    }

    LaunchedEffect(isFullscreen, videoWidth, videoHeight, pixelRatio) {
        if (!isFullscreen) return@LaunchedEffect
        val activity = context.findActivity() ?: return@LaunchedEffect
        activity.requestedOrientation = fullscreenOrientation(videoWidth, videoHeight, pixelRatio)
    }

    LaunchedEffect(uri) {
        if (uri == null) {
            isFullscreen = false
            videoWidth = 0
            videoHeight = 0
            pixelRatio = 1f
        }
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

    LaunchedEffect(uri, rotationDegrees) {
        if (uri == null) return@LaunchedEffect
        applyPreviewRotation(exoPlayer, rotationDegrees)
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
                    onFullscreenChange = onFullscreenChange,
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
                    onFullscreenChange = onFullscreenChange,
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
    hideSettingsButton()
    setFullscreenButtonClickListener { onFullscreenChange(it) }
    setFullscreenButtonState(isFullscreen)
}

private fun PlayerView.hideSettingsButton() {
    val settings = findViewById<View>(androidx.media3.ui.R.id.exo_settings) ?: return
    settings.visibility = View.GONE
    if (settings.getTag(androidx.media3.ui.R.id.exo_settings) == true) return
    settings.setTag(androidx.media3.ui.R.id.exo_settings, true)
    settings.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        if (view.visibility != View.GONE) view.visibility = View.GONE
    }
}

/**
 * Screen orientation for fullscreen playback: landscape when the frame is
 * wider than it is tall, portrait when it is taller, otherwise unspecified.
 */
internal fun fullscreenOrientation(
    width: Int,
    height: Int,
    pixelWidthHeightRatio: Float = 1f,
): Int {
    if (width <= 0 || height <= 0) return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    val displayWidth = width * pixelWidthHeightRatio
    val displayHeight = height.toFloat()
    return when {
        displayWidth > displayHeight -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        displayWidth < displayHeight -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
private fun applyPreviewRotation(player: ExoPlayer, rotationDegrees: Int) {
    val degrees = normalizeRotationDegrees(rotationDegrees)
    if (degrees == 0) {
        player.setVideoEffects(emptyList())
    } else {
        player.setVideoEffects(
            listOf(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(degrees.toFloat())
                    .build(),
            ),
        )
    }
}

/** Formats a millisecond offset as `m:ss.SSS` (or `h:mm:ss.SSS`). */
fun formatMs(ms: Long): String = formatTimecode(ms)
