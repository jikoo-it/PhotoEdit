package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.ImageProcessingRepository
import com.momi.watermarker.domain.repository.MediaRepository
import com.momi.watermarker.domain.usecase.FitImagesToSizeUseCase
import com.momi.watermarker.domain.usecase.SaveImageUseCase
import com.momi.watermarker.domain.util.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitImagesToSizeUseCaseTest {

    private val processing = mockk<ImageProcessingRepository>()
    private val media = mockk<MediaRepository>()
    private val useCase = FitImagesToSizeUseCase(processing, SaveImageUseCase(media))

    @Test
    fun `fits and saves each image independently`() = runTest {
        val a = WatermarkImage("content://a")
        val b = WatermarkImage("content://b")
        val fittedA = WatermarkImage("content://a-out")
        val fittedB = WatermarkImage("content://b-out")
        coEvery { processing.fitToTargetSize(a, 200_000L, ExportFormat.JPEG) } returns Outcome.Success(fittedA)
        coEvery { processing.fitToTargetSize(b, 200_000L, ExportFormat.JPEG) } returns Outcome.Success(fittedB)
        coEvery { media.saveToGallery(any(), any(), any()) } answers {
            Outcome.Success(firstArg())
        }

        val result = useCase(listOf(a, b), 200_000L, ExportFormat.JPEG)

        assertTrue(result.allSucceeded)
        assertEquals(2, result.savedCount)
        coVerify(exactly = 1) { processing.fitToTargetSize(a, 200_000L, ExportFormat.JPEG) }
        coVerify(exactly = 1) { processing.fitToTargetSize(b, 200_000L, ExportFormat.JPEG) }
    }
}
