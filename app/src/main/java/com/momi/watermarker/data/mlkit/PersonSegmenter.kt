package com.momi.watermarker.data.mlkit

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper over ML Kit's bundled on-device **Selfie Segmentation**. Produces
 * a soft, per-pixel foreground (person) confidence mask for one image. All
 * detected people are foreground, so multi-person images keep everyone in color.
 *
 * The model is bundled in the APK, so this works offline with no download.
 */
@Singleton
class PersonSegmenter @Inject constructor() {

    private val client by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                // Leave the raw-size mask OFF: the default scales the mask up to
                // the input image's dimensions, so it aligns pixel-for-pixel with
                // the photo and can be composited without any extra scaling.
                .build(),
        )
    }

    /**
     * Returns an ARGB_8888 mask the same size as [bitmap] where each pixel's
     * **alpha** is the person-confidence (0 = background, 255 = person) and the
     * RGB channels are white. Suitable as a `DST_IN` mask for feathered
     * compositing.
     */
    suspend fun personMask(bitmap: Bitmap): Bitmap = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { mask ->
                // If the caller cancelled (e.g. a newer render superseded this
                // one), don't allocate a mask bitmap that would be leaked.
                if (!cont.isActive) return@addOnSuccessListener
                try {
                    cont.resume(toAlphaMask(mask))
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    /** Packs the confidence [mask] into an alpha bitmap (alpha = confidence). */
    private fun toAlphaMask(mask: SegmentationMask): Bitmap {
        val width = mask.width
        val height = mask.height
        val buffer = mask.buffer
        buffer.rewind()
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val confidence = buffer.float // advances 4 bytes; values in 0f..1f
            val alpha = (confidence * 255f + 0.5f).toInt().coerceIn(0, 255)
            // White with confidence-scaled alpha (only alpha matters for DST_IN).
            pixels[i] = (alpha shl 24) or 0x00FFFFFF
        }
        // Must be mutable: the processor feathers the mask in place (setPixels).
        // The int[] factory overload returns an immutable bitmap, so build a
        // mutable one and write the pixels into it.
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
