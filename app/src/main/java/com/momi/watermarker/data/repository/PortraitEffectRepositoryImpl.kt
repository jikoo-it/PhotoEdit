package com.momi.watermarker.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.momi.watermarker.data.rendering.PortraitEffectProcessor
import com.momi.watermarker.data.storage.ImageStorage
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.PortraitEffect
import com.momi.watermarker.domain.repository.PortraitEffectRepository
import com.momi.watermarker.domain.util.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [PortraitEffectRepository] backed by [PortraitEffectProcessor], with
 * [ImageStorage] for decoding and caching. Decodes at a bounded resolution so
 * previews stay responsive and large images don't OOM.
 */
class PortraitEffectRepositoryImpl @Inject constructor(
    private val imageStorage: ImageStorage,
    private val processor: PortraitEffectProcessor,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : PortraitEffectRepository {

    override suspend fun render(
        sourceUri: String,
        effect: PortraitEffect,
        maxLongEdge: Int,
    ): Outcome<String> = withContext(dispatcher) {
        var source: Bitmap? = null
        var result: Bitmap? = null
        try {
            source = imageStorage.decodeBoundedBitmap(Uri.parse(sourceUri), maxLongEdge)
            result = processor.apply(source, effect)
            // The result is opaque (color subject over an opaque background).
            val uri = imageStorage.writeToCache(result, prefix = "portrait", format = Bitmap.CompressFormat.JPEG)
            Outcome.Success(uri.toString())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Outcome.Failure(t)
        } finally {
            source?.recycle()
            result?.recycle()
        }
    }
}
