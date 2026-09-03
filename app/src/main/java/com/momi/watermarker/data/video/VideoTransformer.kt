package com.momi.watermarker.data.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin coroutine wrapper around Media3 [Transformer].
 *
 * Media3 requires the transformer to be built, started, and cancelled on a
 * thread that owns a [Looper]; its listener callbacks arrive on that same
 * thread. We therefore drive everything on the main thread and bridge the
 * async listener into a suspending call, so callers (repositories) stay on
 * plain coroutines and never see the threading constraint.
 */
@Singleton
class VideoTransformer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Trims the video at [inputUri] to [startMs, endMs] and writes the result to
     * [outputPath]. Suspends until export completes; throws [ExportException] on
     * failure. Cancelling the coroutine cancels the in-flight export.
     */
    suspend fun trim(inputUri: Uri, outputPath: String, startMs: Long, endMs: Long) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        }
                    })
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(inputUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build(),
                    )
                    .build()

                val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

                // cancel() must run on the transformer's (main) thread, but
                // invokeOnCancellation may fire from any thread — so hop to main.
                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }

                transformer.start(editedMediaItem, outputPath)
            }
        }
    }
}
