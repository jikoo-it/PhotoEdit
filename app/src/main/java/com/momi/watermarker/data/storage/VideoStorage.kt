package com.momi.watermarker.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
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

    /** Decodes the image at [uri] into a [Bitmap] (e.g. for a video overlay). */
    fun decodeBitmap(uri: Uri): Bitmap =
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $uri" }
            BitmapFactory.decodeStream(input) ?: error("Could not decode image at $uri")
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
    }
}
