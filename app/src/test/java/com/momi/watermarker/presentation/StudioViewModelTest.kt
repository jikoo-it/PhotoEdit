package com.momi.watermarker.presentation

import android.content.Context
import com.momi.watermarker.MainDispatcherRule
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.FlattenedImage
import com.momi.watermarker.domain.model.ImageInfo
import com.momi.watermarker.domain.model.LayerContent
import com.momi.watermarker.domain.model.LayerIds
import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.model.StudioBackdrop
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.usecase.CutoutPathUseCase
import com.momi.watermarker.domain.usecase.ExtractPeopleUseCase
import com.momi.watermarker.domain.usecase.FlattenLayerDocumentUseCase
import com.momi.watermarker.domain.usecase.GetImageInfoUseCase
import com.momi.watermarker.domain.usecase.ProposeSubjectOutlineUseCase
import com.momi.watermarker.domain.usecase.SaveImageUseCase
import com.momi.watermarker.domain.util.Outcome
import com.momi.watermarker.presentation.studio.StudioViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appContext = mockk<Context>(relaxed = true)
    private val getImageInfo = mockk<GetImageInfoUseCase>()
    private val proposeSubjectOutline = mockk<ProposeSubjectOutlineUseCase>()
    private val cutoutPath = mockk<CutoutPathUseCase>()
    private val extractPeople = mockk<ExtractPeopleUseCase>()
    private val flattenDocument = mockk<FlattenLayerDocumentUseCase>()
    private val saveImage = mockk<SaveImageUseCase>()

    @Before
    fun setUp() {
        every { appContext.getString(any()) } returns ""
        every { appContext.getString(any(), *anyVararg()) } returns ""
        coEvery { getImageInfo(any()) } returns Outcome.Success(ImageInfo(1000, 800, 1_000L))
        coEvery { flattenDocument(any(), any()) } returns Outcome.Success(
            FlattenedImage("content://preview", hasTransparency = false),
        )
        coEvery { proposeSubjectOutline(any()) } returns Outcome.Success(squarePath())
        coEvery { cutoutPath(any(), any()) } returns Outcome.Success("content://traced")
        coEvery { extractPeople(any(), any()) } returns Outcome.Success("content://people")
        coEvery { saveImage(any(), any()) } returns Outcome.Success(WatermarkImage("content://saved"))
    }

    private fun viewModel() = StudioViewModel(
        appContext = appContext,
        getImageInfo = getImageInfo,
        proposeSubjectOutline = proposeSubjectOutline,
        cutoutPath = cutoutPath,
        extractPeople = extractPeople,
        flattenDocument = flattenDocument,
        saveImage = saveImage,
    )

    @Test
    fun `opening a photo creates a background layer and previews`() = runTest {
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.hasSource)
        assertEquals("content://photo", state.sourceUri)
        assertEquals(listOf(LayerIds.BACKGROUND), state.document!!.layers.map { it.id })
        assertEquals("content://preview", state.previewUri)
        assertTrue(state.canSave)
        assertFalse(state.canUndo)
    }

    @Test
    fun `auto cut out previews an outline without adding a layer`() = runTest {
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        vm.onCutOut()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.isReviewingCutout)
        assertFalse(state.hasSubject)
        assertFalse(state.canSave)
        assertEquals(squarePath().size, state.cutoutOutline.size)
    }

    @Test
    fun `confirming a cut out adds a subject layer and records undo`() = runTest {
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        vm.onCutOut()
        advanceUntilIdle()
        vm.onConfirmCutout()
        advanceUntilIdle()
        val doc = vm.uiState.value.document!!
        assertTrue(doc.hasSubject())
        assertFalse(vm.uiState.value.isReviewingCutout)
        assertEquals(LayerIds.SUBJECT, doc.selectedLayerId)
        assertTrue(vm.uiState.value.canUndo)
        vm.onUndo()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasSubject)
    }

    @Test
    fun `dragging a handle updates the preview outline only`() = runTest {
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        vm.onCutOut()
        advanceUntilIdle()
        vm.onOutlinePointMoved(0, NormalizedPoint(0.11f, 0.12f))
        assertEquals(0.11f, vm.uiState.value.cutoutOutline[0].x, 0f)
        assertEquals(0.12f, vm.uiState.value.cutoutOutline[0].y, 0f)
        assertFalse(vm.uiState.value.hasSubject)
    }

    @Test
    fun `transparent backdrop exports png`() = runTest {
        coEvery { flattenDocument(any(), any()) } returns Outcome.Success(
            FlattenedImage("content://preview", hasTransparency = true),
        )
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        vm.onCutOut()
        advanceUntilIdle()
        vm.onConfirmCutout()
        advanceUntilIdle()
        vm.onBackdropSelected(StudioBackdrop.TRANSPARENT)
        advanceUntilIdle()
        assertEquals(StudioBackdrop.TRANSPARENT, vm.uiState.value.backdrop)
        assertEquals(ExportFormat.PNG, vm.uiState.value.exportFormat)
    }

    @Test
    fun `portrait look extracts people when there is no subject`() = runTest {
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        vm.onPortraitLookToggled(true)
        advanceUntilIdle()
        val doc = vm.uiState.value.document!!
        assertTrue(doc.hasSubject())
        assertTrue(doc.portraitLookEnabled())
        assertEquals(
            "content://people",
            (doc.layer(LayerIds.SUBJECT)!!.content as LayerContent.Raster).uri,
        )
    }

    @Test
    fun `background blur keeps color and extracts people when needed`() = runTest {
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        vm.onBlurChanged(0.45f)
        vm.onBlurCommitted()
        advanceUntilIdle()
        val doc = vm.uiState.value.document!!
        assertTrue(doc.hasSubject())
        assertFalse(doc.portraitLookEnabled())
        assertEquals(0.45f, doc.blurStrength(), 0.0f)
        val adj = doc.layer(LayerIds.ADJUSTMENT)!!.content as LayerContent.Adjustment
        assertFalse(adj.grayscale)
    }

    @Test
    fun `tracing an outline waits for confirm`() = runTest {
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        vm.onStartTrace()
        assertTrue(vm.uiState.value.isTracing)
        vm.onTraceCompleted(squarePath())
        val state = vm.uiState.value
        assertFalse(state.isTracing)
        assertTrue(state.isReviewingCutout)
        assertFalse(state.hasSubject)
    }

    @Test
    fun `a tiny trace stays in tracing mode`() = runTest {
        val vm = viewModel()
        vm.onImageSelected("content://photo")
        advanceUntilIdle()
        vm.onStartTrace()
        vm.onTraceCompleted(List(12) { NormalizedPoint(0.50f + it * 0.001f, 0.50f) })
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isTracing)
        assertFalse(vm.uiState.value.isReviewingCutout)
        assertFalse(vm.uiState.value.hasSubject)
    }
}

private fun squarePath(): List<NormalizedPoint> {
    val points = mutableListOf<NormalizedPoint>()
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int) {
        repeat(steps) { i ->
            val t = i / steps.toFloat()
            points += NormalizedPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
        }
    }
    line(0.2f, 0.2f, 0.8f, 0.2f, 4)
    line(0.8f, 0.2f, 0.8f, 0.8f, 4)
    line(0.8f, 0.8f, 0.2f, 0.8f, 4)
    line(0.2f, 0.8f, 0.2f, 0.2f, 4)
    return points
}
