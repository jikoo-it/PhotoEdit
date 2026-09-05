package com.momi.watermarker.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.momi.watermarker.data.rendering.maskToShape
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ImageInfo
import com.momi.watermarker.domain.model.NormalizedRect
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
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
     * Decodes [uri] EXIF-corrected, **subsampling during the decode** so the
     * full-resolution bitmap is never materialized. The result's long edge is
     * roughly ≤ [maxLongEdge] (then fine-scaled to the exact cap). This bounds
     * peak memory for large sources — use it instead of [decodeBitmap] when a
     * working-size cap is known. Returns the image untouched if already smaller.
     */
    fun decodeBoundedBitmap(uri: Uri, maxLongEdge: Int): Bitmap {
        // 1) Bounds-only decode to size the subsample.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $uri" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Couldn't read image bounds for $uri" }

        // 2) Full decode with power-of-two subsampling to bound the allocation.
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(max(bounds.outWidth, bounds.outHeight), maxLongEdge)
        }
        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $uri" }
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("Failed to decode image at $uri")

        // 3) Apply EXIF rotation.
        val rotation = readExifRotation(uri)
        val oriented = if (rotation == 0f) {
            decoded
        } else {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it != decoded) decoded.recycle() }
        }

        // 4) Fine-scale to the exact cap if subsampling left it larger.
        val longEdge = max(oriented.width, oriented.height)
        if (longEdge <= maxLongEdge) return oriented
        val scale = maxLongEdge / longEdge.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    /** Largest power-of-two subsample that keeps the decoded long edge ≥ [maxLongEdge]. */
    private fun sampleSizeFor(sourceLongEdge: Int, maxLongEdge: Int): Int {
        if (maxLongEdge < 1) return 1
        var sample = 1
        while (sourceLongEdge / (sample * 2) >= maxLongEdge) sample *= 2
        return sample
    }

    /**
     * Reads [uri]'s pixel dimensions (bounds-only decode, no full bitmap) and
     * encoded byte size. Dimensions are swapped when EXIF marks the image as
     * rotated 90°/270°, so they match what [decodeBitmap] produces.
     */
    fun readImageInfo(uri: Uri): ImageInfo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $uri" }
            BitmapFactory.decodeStream(input, null, options)
        }
        check(options.outWidth > 0 && options.outHeight > 0) { "Couldn't read image bounds for $uri" }

        val swapAxes = readExifRotation(uri).let { it == 90f || it == 270f }
        val width = if (swapAxes) options.outHeight else options.outWidth
        val height = if (swapAxes) options.outWidth else options.outHeight
        return ImageInfo(width = width, height = height, sizeBytes = readByteSize(uri))
    }

    /** Best-effort encoded size of [uri] in bytes, or null if unavailable. */
    private fun readByteSize(uri: Uri): Long? = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
            fd.length.takeIf { it >= 0 }
        }
    }.getOrNull()

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
     * Encodes [bitmap] into the shared cache per [export] (format + compression
     * mode) and returns a `content://` URI. The file carries the format's
     * extension so downstream consumers (and the gallery export) get the right
     * type. In [CompressionMode.TARGET_SIZE] the quality is chosen to fit the
     * size budget.
     */
    fun writeToCache(bitmap: Bitmap, prefix: String, export: ExportOptions): Uri {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.${export.format.extension}")
        val bytes = encodeToBytes(bitmap, export)
        FileOutputStream(file).use { out -> out.write(bytes) }
        return fileProviderUri(file)
    }

    /** The number of bytes [bitmap] would occupy when encoded per [export]. */
    fun measureEncodedSize(bitmap: Bitmap, export: ExportOptions): Long =
        encodeToBytes(bitmap, export).size.toLong()

    /**
     * Encodes [bitmap] to a byte array per [export]. For a fixed quality this is
     * a single compress; for a size target it binary-searches quality for the
     * largest that fits the budget (falling back to the lowest quality if none
     * do).
     */
    private fun encodeToBytes(bitmap: Bitmap, export: ExportOptions): ByteArray {
        val format = export.format.toCompressFormat()
        if (!export.usesTargetSize) {
            return compress(bitmap, format, export.effectiveQuality)
        }

        val target = export.targetSizeBytes ?: return compress(bitmap, format, export.effectiveQuality)
        var low = MIN_TARGET_QUALITY
        var high = 100
        var best: ByteArray? = null
        while (low <= high) {
            val mid = (low + high) / 2
            val encoded = compress(bitmap, format, mid)
            if (encoded.size <= target) {
                best = encoded
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        // If even the lowest quality overshoots, keep that smallest result.
        return best ?: compress(bitmap, format, MIN_TARGET_QUALITY)
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray =
        java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.toByteArray()
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
            val shaped = maskToShape(cropped, shape)
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
        const val MIN_TARGET_QUALITY = 5
    }
}
