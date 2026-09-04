package com.momi.watermarker.data.mlkit

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper over ML Kit's on-device Subject Segmentation. Extracts the
 * salient subject(s) from a bitmap and returns them over a transparent
 * background.
 *
 * The segmentation model is downloaded on demand by Google Play services on
 * first use, so the first call needs a network connection.
 */
@Singleton
class SubjectSegmenter @Inject constructor() {

    // A single reusable client; segmentation is stateless between calls.
    private val client by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build(),
        )
    }

    /**
     * Returns a bitmap the same size as [bitmap] containing only the detected
     * subject(s); every other pixel is transparent. Throws if no subject was
     * found or the model isn't available.
     */
    suspend fun cutout(bitmap: Bitmap): Bitmap = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val foreground = result.foregroundBitmap
                if (foreground != null) {
                    cont.resume(foreground)
                } else {
                    cont.resumeWithException(
                        IllegalStateException("No subject found in the image."),
                    )
                }
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
