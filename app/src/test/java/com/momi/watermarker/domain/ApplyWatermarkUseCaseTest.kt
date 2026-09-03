package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.WatermarkRepository
import com.momi.watermarker.domain.usecase.ApplyWatermarkUseCase
import com.momi.watermarker.domain.util.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyWatermarkUseCaseTest {

    private val repository = mockk<WatermarkRepository>()
    private val useCase = ApplyWatermarkUseCase(repository)
    private val source = WatermarkImage("content://source")

    @Test
    fun `blank text fails without touching the repository`() = runTest {
        val result = useCase(source, WatermarkConfig(text = "  "))

        assertTrue(result is Outcome.Failure)
        coVerify(exactly = 0) { repository.applyWatermark(any(), any()) }
    }

    @Test
    fun `valid config delegates to the repository`() = runTest {
        val expected = WatermarkImage("content://out")
        coEvery { repository.applyWatermark(any(), any()) } returns Outcome.Success(expected)

        val result = useCase(source, WatermarkConfig(text = "©"))

        assertTrue(result is Outcome.Success)
        coVerify(exactly = 1) { repository.applyWatermark(source, any()) }
    }
}
