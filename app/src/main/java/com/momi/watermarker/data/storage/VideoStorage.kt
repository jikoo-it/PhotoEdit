package com.momi.watermarker.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import kotlin.math.roundToInt
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level video I/O: creating cache destinations for exported clips, probing
 * metadata, exposing FileProvider URIs, and exporting to the shared gallery.
 *
 * The video sibling of [ImageStorage]; kept separate so storage mechanics can
 * evolve without touching business logic.
 */
@Singleton
class VideoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Creates an empty output file (in the shared video cache) for a transform. */
    fun createOutputFile(prefix: String): File {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}.mp4")
    }

    /** A `content://` URI for [file], exported via [FileProvider]. */
    fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Reads the duration (ms) of the video at [uri] via metadata. */
    fun probeDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: error("No duration metadata for $uri")
        } finally {
            retriever.release()
        }
    }

    /**
     * Decodes the image at [uri] into a [Bitmap] suitable for a Media3 video
     * overlay. Media3 uploads the overlay as an OpenGL texture, which fails for
     * hardware-backed / non-ARGB_8888 bitmaps and for bitmaps larger than the
     * GPU's max texture size — the "video frame processing error" seen otherwise.
     * So we force a software ARGB_8888 config and downscale oversized images to a
     * safe texture dimension.
     */
    fun decodeBitmap(uri: Uri): Bitmap {
        // Buffer the bytes so we can decode twice (bounds, then pixels).
        val bytes = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $uri" }
            input.readBytes()
        }

        // First pass: read the dimensions to pick a sub-sampling factor.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            // ARGB_8888 (software) is what GL texture upload expects; never HARDWARE.
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_OVERLAY_DIMENSION)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Could not decode image at $uri")

        // Sub-sampling only halves in powers of two, so a final clamp guarantees
        // both sides sit within the safe texture size.
        val clamped = clampToMaxDimension(decoded, MAX_OVERLAY_DIMENSION)

        // Guarantee a software ARGB_8888 backing store regardless of decoder choices.
        return if (clamped.config == Bitmap.Config.ARGB_8888) {
            clamped
        } else {
            clamped.copy(Bitmap.Config.ARGB_8888, false).also { clamped.recycle() }
        }
    }

    /** Largest power-of-two sub-sample that keeps both sides within [max]. */
    private fun sampleSizeFor(width: Int, height: Int, max: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= max || height / (sample * 2) >= max) {
            sample *= 2
        }
        return sample
    }

    /** Returns [bitmap] scaled down (preserving aspect) so neither side exceeds [max]. */
    private fun clampToMaxDimension(bitmap: Bitmap, max: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= max) return bitmap
        val scale = max.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    /**
     * The displayed pixel size (width × height, accounting for rotation) of the
     * video at [uri]. Falls back to 1920×1080 if metadata is unavailable, so
     * overlay sizing always has a sensible frame to scale against.
     */
    fun probeDisplaySize(uri: Uri): Size {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1920
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1080
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            // A 90°/270° rotation swaps the displayed width and height.
            if (rotation == 90 || rotation == 270) Size(h, w) else Size(w, h)
        } catch (t: Throwable) {
            Size(1920, 1080)
        } finally {
            retriever.release()
        }
    }

    /**
     * Renders [text] to a transparent ARGB_8888 bitmap whose text cap height is
     * [targetHeightPx], for use as a video overlay. Multi-line text is laid out
     * top-to-bottom and center-aligned.
     */
    fun renderTextBitmap(text: String, colorArgb: Int, targetHeightPx: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = targetHeightPx.coerceAtLeast(1).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            // A subtle shadow keeps light text legible over bright footage.
            setShadowLayer(textSize * 0.06f, 0f, textSize * 0.03f, 0x99000000.toInt())
        }
        val lines = text.split('\n')
        val fm = paint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val pad = (paint.textSize * 0.12f).roundToInt()
        val width = (lines.maxOf { paint.measureText(it) }.roundToInt() + pad * 2).coerceAtLeast(1)
        val height = ((lineHeight * lines.size).roundToInt() + pad * 2).coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        paint.textAlign = Paint.Align.CENTER
        var baseline = pad - fm.ascent
        for (line in lines) {
            canvas.drawText(line, width / 2f, baseline, paint)
            baseline += lineHeight
        }
        return bitmap
    }

    /**
     * Copies the video at [sourceUri] into the device gallery
     * (Movies/MomiWaterMarker) and returns the new MediaStore content URI.
     */
    fun saveToGallery(sourceUri: Uri, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/MomiWaterMarker",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = resolver.insert(collection, values)
            ?: error("Failed to create gallery entry")

        resolver.openOutputStream(target).use { output ->
            requireNotNull(output) { "Cannot open output stream for $target" }
            resolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "Cannot open input stream for $sourceUri" }
                input.copyTo(output)
            }
        }

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        return target
    }

    private companion object {
        const val SHARED_DIR = "shared_videos"

        /**
         * Safe upper bound for an overlay bitmap's dimensions. The GL_MAX_TEXTURE_SIZE
         * guaranteed by GLES 2.0 is 2048; virtually every device meets it, so we cap
         * overlays here to stay within the frame processor's texture limits.
         */
        const val MAX_OVERLAY_DIMENSION = 2048
    }
}
