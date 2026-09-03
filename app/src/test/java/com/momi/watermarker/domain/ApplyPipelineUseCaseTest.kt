package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.Pipeline
import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.ImageProcessingRepository
import com.momi.watermarker.domain.usecase.ApplyPipelineUseCase
import com.momi.watermarker.domain.util.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyPipelineUseCaseTest {

    private val repository = mockk<ImageProcessingRepository>()
    private val useCase = ApplyPipelineUseCase(repository)
    private val source = WatermarkImage("content://source")

    @Test
    fun `empty pipeline fails without touching the repository`() = runTest {
        val result = useCase(source, Pipeline.EMPTY)

        assertTrue(result is Outcome.Failure)
        coVerify(exactly = 0) { repository.applyPipeline(any(), any()) }
    }

    @Test
    fun `non-empty pipeline delegates to the repository`() = runTest {
        val expected = WatermarkImage("content://out")
        coEvery { repository.applyPipeline(any(), any()) } returns Outcome.Success(expected)
        val pipeline = Pipeline(listOf(ImageOp.Watermark(WatermarkConfig(text = "©"))))

        val result = useCase(source, pipeline)

        assertTrue(result is Outcome.Success)
        coVerify(exactly = 1) { repository.applyPipeline(source, pipeline) }
    }
}
