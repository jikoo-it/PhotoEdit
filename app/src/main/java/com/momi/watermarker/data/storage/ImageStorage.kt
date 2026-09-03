package com.momi.watermarker.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.squircleUnitPoints
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Low-level image I/O: decoding bitmaps (EXIF-corrected), writing them to the
 * app's private cache, creating camera capture destinations, and exporting to
 * the shared gallery.
 *
 * Kept separate from the repositories so the storage mechanics can evolve
 * (e.g. scoped storage changes) without touching business logic.
 */
@Singleton
class ImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Decodes [uri] into a bitmap, rotating it per its EXIF orientation. */
    fun decodeBitmap(uri: Uri): Bitmap {
        val bitmap = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $uri" }
            BitmapFactory.decodeStream(input)
        } ?: error("Failed to decode image at $uri")

        val rotation = readExifRotation(uri)
        if (rotation == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { rotated -> if (rotated != bitmap) bitmap.recycle() }
    }

    /**
     * Compresses [bitmap] into the shared cache directory and returns a
     * `content://` URI exported via [FileProvider]. Use [Bitmap.CompressFormat.PNG]
     * to preserve transparency (e.g. for logo watermarks).
     */
    fun writeToCache(
        bitmap: Bitmap,
        prefix: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    ): Uri {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else JPEG_QUALITY
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")
        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
        }
        return fileProviderUri(file)
    }

    /**
     * Compresses [bitmap] into the shared cache using [format] at [quality]
     * (0..100; ignored for formats without a quality setting) and returns a
     * `content://` URI. The file carries [format]'s extension so downstream
     * consumers (and the gallery export) get the right type.
     */
    fun writeToCache(bitmap: Bitmap, prefix: String, format: ExportFormat, quality: Int): Uri {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.${format.extension}")
        val effectiveQuality = if (format.supportsQuality) quality else 100
        FileOutputStream(file).use { out ->
            bitmap.compress(format.toCompressFormat(), effectiveQuality, out)
        }
        return fileProviderUri(file)
    }

    private fun ExportFormat.toCompressFormat(): Bitmap.CompressFormat = when (this) {
        ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        // Lossy WebP honours the quality setting; available since API 30 (minSdk 31).
        ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
    }

    /**
     * Decodes [sourceUri], crops it to [rect] (fractions of the full image),
     * masks it to [shape] (making pixels outside the shape transparent), and
     * writes the result to cache as a PNG (to keep the transparency), returning
     * the cropped image's URI.
     */
    fun cropToCache(sourceUri: Uri, rect: NormalizedRect, shape: CropShape): Uri {
        val bitmap = decodeBitmap(sourceUri)
        try {
            val w = bitmap.width
            val h = bitmap.height
            val x = (rect.left * w).roundToInt().coerceIn(0, w - 1)
            val y = (rect.top * h).roundToInt().coerceIn(0, h - 1)
            val cropWidth = (rect.width * w).roundToInt().coerceIn(1, w - x)
            val cropHeight = (rect.height * h).roundToInt().coerceIn(1, h - y)
            val cropped = Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
            val shaped = applyShapeMask(cropped, shape)
            return try {
                writeToCache(shaped, prefix = "watermark_src", format = Bitmap.CompressFormat.PNG)
            } finally {
                if (shaped != cropped) shaped.recycle()
                if (cropped != bitmap) cropped.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Returns [src] masked to [shape]: the shape is drawn opaque, then the source
     * is composited only where the shape covers it (`SRC_IN`), leaving everything
     * outside the shape transparent. [CropShape.RECTANGLE] needs no mask and is
     * returned unchanged.
     */
    private fun applyShapeMask(src: Bitmap, shape: CropShape): Bitmap {
        if (shape == CropShape.RECTANGLE) return src
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(shapePath(shape, w.toFloat(), h.toFloat()), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    /** The [shape] outline filling the `w`×`h` box. */
    private fun shapePath(shape: CropShape, w: Float, h: Float): Path = Path().apply {
        when (shape) {
            CropShape.RECTANGLE -> addRect(0f, 0f, w, h, Path.Direction.CW)
            CropShape.CIRCLE -> addOval(0f, 0f, w, h, Path.Direction.CW)
            CropShape.ROUNDED -> {
                val r = minOf(w, h) * CropShape.ROUNDED_CORNER_FRACTION
                addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
            }
            CropShape.SQUIRCLE -> {
                squircleUnitPoints().forEachIndexed { i, (ux, uy) ->
                    val px = ux * w
                    val py = uy * h
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
        }
    }

    /** Creates an empty JPEG file the camera can write into, returning its URI. */
    fun createCaptureFile(): Uri {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        return fileProviderUri(file)
    }

    /**
     * Copies the image at [sourceUri] into the device gallery (Pictures/MomiWaterMarker)
     * as [format] and returns the new MediaStore content URI. [sourceUri] is
     * expected to already be encoded in [format] (see [writeToCache]).
     */
    fun saveToGallery(sourceUri: Uri, displayName: String, format: ExportFormat): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.${format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/MomiWaterMarker",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
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
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        return target
    }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun readExifRotation(uri: Uri): Float =
        runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return 0f
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)

    private companion object {
        const val SHARED_DIR = "shared_images"
        const val JPEG_QUALITY = 95
    }
}
