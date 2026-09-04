package com.momi.watermarker.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.momi.watermarker.data.mlkit.SubjectSegmenter
import com.momi.watermarker.data.rendering.CutoutComposer
import com.momi.watermarker.data.storage.ImageStorage
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.BackgroundMode
import com.momi.watermarker.domain.model.CutoutRenderSpec
import com.momi.watermarker.domain.repository.ImageCutoutRepository
import com.momi.watermarker.domain.util.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max

/**
 * [ImageCutoutRepository] backed by ML Kit ([SubjectSegmenter]) for extraction
 * and [CutoutComposer] for compositing, with [ImageStorage] for I/O.
 */
class ImageCutoutRepositoryImpl @Inject constructor(
    private val imageStorage: ImageStorage,
    private val segmenter: SubjectSegmenter,
    private val composer: CutoutComposer,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ImageCutoutRepository {

    override suspend fun cutoutSubject(sourceUri: String): Outcome<String> =
        withContext(dispatcher) {
            var source: Bitmap? = null
            var cutout: Bitmap? = null
            try {
                source = decodeBounded(sourceUri)
                cutout = segmenter.cutout(source)
                val uri = imageStorage.writeToCache(
                    cutout,
                    prefix = "cutout",
                    format = Bitmap.CompressFormat.PNG,
                )
                Outcome.Success(uri.toString())
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Outcome.Failure(t)
            } finally {
                source?.recycle()
                cutout?.recycle()
            }
        }

    override suspend fun renderResult(spec: CutoutRenderSpec): Outcome<String> =
        withContext(dispatcher) {
            var cutout: Bitmap? = null
            var background: Bitmap? = null
            var result: Bitmap? = null
            try {
                cutout = imageStorage.decodeBitmap(Uri.parse(spec.cutoutUri))
                result = when (spec.mode) {
                    BackgroundMode.TRANSPARENT ->
                        composer.transparent(cutout)

                    BackgroundMode.COLOR ->
                        composer.overColor(cutout, spec.backgroundColorArgb)

                    BackgroundMode.BLUR -> {
                        background = decodeBounded(spec.sourceUri)
                        composer.overBlurredSource(cutout, background, spec.blurStrength)
                    }

                    BackgroundMode.IMAGE -> {
                        val bgUri = spec.backgroundImageUri
                            ?: error("Pick a background image first.")
                        background = decodeBounded(bgUri)
                        composer.overImage(cutout, background)
                    }
                }
                // Only a transparent background needs PNG; opaque results are
                // smaller as JPEG.
                val format =
                    if (spec.isTransparent) Bitmap.CompressFormat.PNG
                    else Bitmap.CompressFormat.JPEG
                val uri = imageStorage.writeToCache(result, prefix = "cutout_result", format = format)
                Outcome.Success(uri.toString())
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Outcome.Failure(t)
            } finally {
                cutout?.recycle()
                background?.recycle()
                result?.recycle()
            }
        }

    /** Decodes [uri] (EXIF-corrected) and downscales so its long edge ≤ [MAX_DIM]. */
    private fun decodeBounded(uri: String): Bitmap {
        val bitmap = imageStorage.decodeBitmap(Uri.parse(uri))
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= MAX_DIM) return bitmap
        val scale = MAX_DIM / longEdge.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private companion object {
        /** Cap the working resolution to bound memory and segmentation time. */
        const val MAX_DIM = 2048
    }
}
