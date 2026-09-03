package com.momi.watermarker.presentation

import app.cash.turbine.test
import com.momi.watermarker.MainDispatcherRule
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.model.WatermarkPattern
import com.momi.watermarker.domain.model.WatermarkType
import com.momi.watermarker.domain.usecase.ApplyPipelineUseCase
import com.momi.watermarker.domain.usecase.BatchSaveResult
import com.momi.watermarker.domain.usecase.CreateCaptureDestinationUseCase
import com.momi.watermarker.domain.usecase.CropImageUseCase
import com.momi.watermarker.domain.usecase.GetWatermarkOptionsUseCase
import com.momi.watermarker.domain.usecase.ProcessAndSaveImagesUseCase
import com.momi.watermarker.domain.util.Outcome
import com.momi.watermarker.presentation.editor.EditorEffect
import com.momi.watermarker.presentation.editor.EditorViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val applyPipeline = mockk<ApplyPipelineUseCase>()
    private val processAndSaveImages = mockk<ProcessAndSaveImagesUseCase>()
    private val createCaptureDestination = mockk<CreateCaptureDestinationUseCase>()
    private val cropImage = mockk<CropImageUseCase>()
    private val getOptions = GetWatermarkOptionsUseCase()

    private fun viewModel() = EditorViewModel(
        applyPipeline = applyPipeline,
        processAndSaveImages = processAndSaveImages,
        createCaptureDestination = createCaptureDestination,
        cropImage = cropImage,
        getOptions = getOptions,
    )

    @Test
    fun `initial state exposes the full option catalog`() {
        val state = viewModel().uiState.value
        assertEquals(WatermarkPattern.entries, state.availablePatterns)
        assertTrue(state.availableFonts.isNotEmpty())
        assertTrue(state.sourceImages.isEmpty())
    }

    @Test
    fun `selecting images renders a preview of the first after debounce`() = runTest {
        val output = WatermarkImage("content://watermarked")
        coEvery { applyPipeline(any(), any()) } returns Outcome.Success(output)

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a", "content://b"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.imageCount)
        assertEquals("content://a", state.selectedSource?.uri)
        assertTrue(state.sourceFromGallery)
        assertEquals(output, state.previewImage)
        coVerify { applyPipeline(any(), any()) }
    }

    @Test
    fun `selecting more images appends to the batch without duplicates`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a", "content://b"))
        advanceUntilIdle()
        vm.onImageFocused(1)
        advanceUntilIdle()

        // Add another image, plus a duplicate of one already present.
        vm.onImagesSelected(listOf("content://b", "content://c"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            listOf("content://a", "content://b", "content://c"),
            state.sourceImages.map { it.uri },
        )
        // Appending must not disturb which image is focused.
        assertEquals(1, state.selectedIndex)
    }

    @Test
    fun `removing a non-selected image keeps the selection`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a", "content://b", "content://c"))
        advanceUntilIdle()
        vm.onImageFocused(2)
        advanceUntilIdle()

        vm.onImageRemoved(0)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("content://b", "content://c"), state.sourceImages.map { it.uri })
        assertEquals("content://c", state.selectedSource?.uri)
    }

    @Test
    fun `removing the last image clears the batch`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a"))
        advanceUntilIdle()

        vm.onImageRemoved(0)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.hasImage)
        assertFalse(state.sourceFromGallery)
        assertEquals(null, state.previewImage)
    }

    @Test
    fun `focusing another image re-renders the preview for it`() = runTest {
        coEvery { applyPipeline(WatermarkImage("content://a"), any()) } returns
            Outcome.Success(WatermarkImage("content://wm-a"))
        coEvery { applyPipeline(WatermarkImage("content://b"), any()) } returns
            Outcome.Success(WatermarkImage("content://wm-b"))

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a", "content://b"))
        advanceUntilIdle()

        vm.onImageFocused(1)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.selectedIndex)
        assertEquals("content://wm-b", state.previewImage?.uri)
    }

    @Test
    fun `capture request emits LaunchCamera with the destination uri`() = runTest {
        coEvery { createCaptureDestination() } returns
            Outcome.Success(WatermarkImage("content://capture"))

        val vm = viewModel()
        vm.effects.test {
            vm.onCaptureRequested()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is EditorEffect.LaunchCamera)
            assertEquals("content://capture", (effect as EditorEffect.LaunchCamera).destinationUri)
        }
    }

    @Test
    fun `captured photos are not eligible for original deletion`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))

        val vm = viewModel()
        vm.onImageCaptured("content://capture")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.sourceFromGallery)
        assertFalse(state.canDeleteOriginals)
    }

    @Test
    fun `save with nothing selected emits a message and skips the repository`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onSaveRequested()
            advanceUntilIdle()
            assertTrue(awaitItem() is EditorEffect.ShowMessage)
        }
        coVerify(exactly = 0) { processAndSaveImages(any(), any(), any()) }
    }

    @Test
    fun `save renders and persists the whole batch`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))
        coEvery { processAndSaveImages(any(), any(), any()) } returns
            BatchSaveResult(savedCount = 2, requested = 2, errors = emptyList())

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a", "content://b"))
        advanceUntilIdle()

        vm.onSaveRequested()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaving)
        coVerify(exactly = 1) {
            processAndSaveImages(
                listOf(WatermarkImage("content://a"), WatermarkImage("content://b")),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `save with delete requested asks the screen to remove originals`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))
        coEvery { processAndSaveImages(any(), any(), any()) } returns
            BatchSaveResult(savedCount = 2, requested = 2, errors = emptyList())

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a", "content://b"))
        advanceUntilIdle()

        vm.effects.test {
            vm.onSaveRequested(deleteOriginals = true)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is EditorEffect.RequestDeleteOriginals)
            assertEquals(
                listOf("content://a", "content://b"),
                (effect as EditorEffect.RequestDeleteOriginals).uris,
            )
        }
    }

    @Test
    fun `partial save failure skips original deletion`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))
        coEvery { processAndSaveImages(any(), any(), any()) } returns
            BatchSaveResult(savedCount = 1, requested = 2, errors = listOf(RuntimeException("boom")))

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a", "content://b"))
        advanceUntilIdle()

        vm.effects.test {
            vm.onSaveRequested(deleteOriginals = true)
            advanceUntilIdle()
            // A summary message, not a delete request, since not all images saved.
            assertTrue(awaitItem() is EditorEffect.ShowMessage)
        }
    }

    @Test
    fun `confirming deletion clears the batch`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a"))
        advanceUntilIdle()

        vm.onOriginalsDeleteResult(deleted = true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.sourceImages.isEmpty())
    }

    @Test
    fun `cropping a watermark image switches to image mode`() = runTest {
        coEvery { applyPipeline(any(), any()) } returns
            Outcome.Success(WatermarkImage("content://wm"))
        coEvery { cropImage(any(), any(), any()) } returns
            Outcome.Success(WatermarkImage("content://cropped"))

        val vm = viewModel()
        vm.onImagesSelected(listOf("content://a"))
        advanceUntilIdle()

        vm.onWatermarkImageCropped(
            "content://logo",
            NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f),
            CropShape.CIRCLE,
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(WatermarkType.IMAGE, state.config.type)
        assertEquals("content://cropped", state.config.imageUri)
        coVerify { cropImage(WatermarkImage("content://logo"), any(), CropShape.CIRCLE) }
    }
}
