package com.momi.watermarker.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.momi.watermarker.data.rendering.LayerCompositor
import com.momi.watermarker.data.rendering.PortraitEffectProcessor
import com.momi.watermarker.data.storage.ImageStorage
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.FlattenedImage
import com.momi.watermarker.domain.model.LayerContent
import com.momi.watermarker.domain.model.LayerDocument
import com.momi.watermarker.domain.model.LayerIds
import com.momi.watermarker.domain.model.PortraitEffect
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
                result = flattenPortraitLook(document, max(outW, outH))
                if (result == null) {
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

    /**
     * Portrait look must grayscale the **full photo**, then stamp the color
     * subject on top. The generic layer loop can miss that backdrop, which
     * shows up as "only the person, background gone."
     */
    private suspend fun flattenPortraitLook(document: LayerDocument, maxLongEdge: Int): Bitmap? {
        val adjLayer = document.layer(LayerIds.ADJUSTMENT) ?: return null
        if (!adjLayer.visible) return null
        val adj = adjLayer.content as? LayerContent.Adjustment ?: return null
        if (!adj.grayscale) return null
        if (document.layer(LayerIds.BACKGROUND)?.visible != true) return null
        if (document.layer(LayerIds.FILL)?.visible == true) return null
        if (document.layer(LayerIds.REPLACEMENT)?.visible == true) return null

        val effect = if (adj.blurStrength > 0.01f) {
            PortraitEffect.SelectiveColorWithBlur(adj.blurStrength)
        } else {
            PortraitEffect.SelectiveColor
        }
        val source = imageStorage.decodeBoundedBitmap(Uri.parse(document.sourceUri), maxLongEdge)
        val decoded = mutableListOf<Bitmap>()
        try {
            val subjectUris = document.subjectLayers()
                .filter { it.visible }
                .mapNotNull { (it.content as? LayerContent.Raster)?.uri }
            val customCutout = subjectUris.any { "studio_people" !in it }
            if (!customCutout) {
                return processor.apply(source, effect)
            }
            for (uri in subjectUris) {
                val bitmap = imageStorage.decodeBoundedBitmap(Uri.parse(uri), maxLongEdge)
                bitmap.setHasAlpha(true)
                decoded += bitmap
            }
            val first = decoded.first()
            val result = processor.composite(source, first, effect)
            if (decoded.size > 1) {
                val stamp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                val dest = RectF(0f, 0f, result.width.toFloat(), result.height.toFloat())
                val canvas = Canvas(result)
                for (i in 1 until decoded.size) {
                    canvas.drawBitmap(decoded[i], null, dest, stamp)
                }
            }
            return result
        } finally {
            source.recycle()
            decoded.forEach { it.recycle() }
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
