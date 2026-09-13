package com.momi.watermarker.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.momi.watermarker.data.rendering.LayerCompositor
import com.momi.watermarker.data.rendering.PortraitEffectProcessor
import com.momi.watermarker.data.storage.ImageStorage
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.FlattenedImage
import com.momi.watermarker.domain.model.LayerDocument
import com.momi.watermarker.domain.repository.StudioRepository
import com.momi.watermarker.domain.util.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** [StudioRepository] backed by ML person extraction and [LayerCompositor]. */
class StudioRepositoryImpl @Inject constructor(
    private val imageStorage: ImageStorage,
    private val processor: PortraitEffectProcessor,
    private val compositor: LayerCompositor,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : StudioRepository {

    override suspend fun extractPeople(sourceUri: String, maxLongEdge: Int): Outcome<String> =
        withContext(dispatcher) {
            var source: Bitmap? = null
            var cutout: Bitmap? = null
            try {
                source = imageStorage.decodeBoundedBitmap(Uri.parse(sourceUri), maxLongEdge)
                cutout = processor.extractForeground(source)
                val uri = imageStorage.writeToCache(
                    cutout,
                    prefix = "studio_people",
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

    override suspend fun flatten(document: LayerDocument, maxLongEdge: Int): Outcome<FlattenedImage> =
        withContext(dispatcher) {
            val rasters = mutableListOf<Bitmap>()
            var result: Bitmap? = null
            try {
                val (outW, outH) = workingSize(document, maxLongEdge)
                val decoded = mutableMapOf<String, Bitmap>()
                result = compositor.flatten(document, outW, outH) { uri ->
                    decoded.getOrPut(uri) {
                        val bitmap = imageStorage.decodeBoundedBitmap(
                            Uri.parse(uri),
                            max(outW, outH),
                        )
                        rasters += bitmap
                        bitmap
                    }
                }
                val hasAlpha = document.producesTransparency()
                val format =
                    if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val uri = imageStorage.writeToCache(result, prefix = "studio_flat", format = format)
                Outcome.Success(FlattenedImage(uri.toString(), hasAlpha))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Outcome.Failure(t)
            } finally {
                rasters.forEach { it.recycle() }
                result?.recycle()
            }
        }

    private fun workingSize(document: LayerDocument, maxLongEdge: Int): Pair<Int, Int> {
        val canvasW = document.canvasWidth.coerceAtLeast(1)
        val canvasH = document.canvasHeight.coerceAtLeast(1)
        val longEdge = max(canvasW, canvasH)
        val scale = min(1f, maxLongEdge / longEdge.toFloat())
        return (canvasW * scale).roundToInt().coerceAtLeast(1) to
            (canvasH * scale).roundToInt().coerceAtLeast(1)
    }
}
