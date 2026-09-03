package com.momi.watermarker.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkFont
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the editor screen: holds the [EditorUiState], mutates the
 * [WatermarkConfig] in response to user input, and (re)renders a live preview
 * through the domain use cases. Contains no Android UI or storage types.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val applyPipeline: ApplyPipelineUseCase,
    private val processAndSaveImages: ProcessAndSaveImagesUseCase,
    private val createCaptureDestination: CreateCaptureDestinationUseCase,
    private val cropImage: CropImageUseCase,
    getOptions: GetWatermarkOptionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditorUiState(
            availablePatterns = getOptions.patterns(),
            availableFonts = getOptions.fonts(),
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Tracks the in-flight preview render so rapid edits cancel stale work. */
    private var previewJob: Job? = null

    // --- Image source events ---

    /**
     * Called after the user picks one or more images from the gallery. New
     * images are appended to the current batch (duplicates ignored), so the user
     * can keep adding to a selection rather than starting over.
     */
    fun onImagesSelected(uris: List<String>) {
        if (uris.isEmpty()) return
        val wasEmpty = _uiState.value.sourceImages.isEmpty()
        _uiState.update { state ->
            val merged = (state.sourceImages.map { it.uri } + uris).distinct()
            state.copy(
                sourceImages = merged.map(::WatermarkImage),
                selectedIndex = if (wasEmpty) 0 else state.selectedIndex,
                sourceFromGallery = true,
                previewImage = if (wasEmpty) null else state.previewImage,
            )
        }
        // Only (re)render when the previewed image changed, i.e. a fresh batch.
        if (wasEmpty) regeneratePreview()
    }

    fun onCaptureRequested() {
        viewModelScope.launch {
            when (val result = createCaptureDestination()) {
                is Outcome.Success ->
                    _effects.send(EditorEffect.LaunchCamera(result.data.uri))
                is Outcome.Failure ->
                    emitMessage("Couldn't start the camera: ${result.error.message}")
            }
        }
    }

    /**
     * Called after the camera reports a successful capture into [uri]. The photo
     * is appended to the batch. A batch that is only camera captures is not
     * eligible for original deletion (nothing to remove from the gallery).
     */
    fun onImageCaptured(uri: String) {
        val wasEmpty = _uiState.value.sourceImages.isEmpty()
        _uiState.update { state ->
            val merged = (state.sourceImages.map { it.uri } + uri).distinct()
            state.copy(
                sourceImages = merged.map(::WatermarkImage),
                selectedIndex = if (wasEmpty) 0 else state.selectedIndex,
                sourceFromGallery = if (wasEmpty) false else state.sourceFromGallery,
                previewImage = if (wasEmpty) null else state.previewImage,
            )
        }
        if (wasEmpty) regeneratePreview()
    }

    /** Removes the image at [index] from the batch, keeping the preview stable. */
    fun onImageRemoved(index: Int) {
        val state = _uiState.value
        if (index !in state.sourceImages.indices) return
        val selectedUri = state.selectedSource?.uri
        val remaining = state.sourceImages.filterIndexed { i, _ -> i != index }

        if (remaining.isEmpty()) {
            previewJob?.cancel()
            _uiState.update {
                it.copy(
                    sourceImages = emptyList(),
                    selectedIndex = 0,
                    sourceFromGallery = false,
                    previewImage = null,
                )
            }
            return
        }

        // Keep the same image selected if it survived; otherwise clamp the index.
        val newIndex = remaining.indexOfFirst { it.uri == selectedUri }
            .let { if (it >= 0) it else index.coerceAtMost(remaining.lastIndex) }
        val selectionChanged = remaining[newIndex].uri != selectedUri
        _uiState.update {
            it.copy(
                sourceImages = remaining,
                selectedIndex = newIndex,
                previewImage = if (selectionChanged) null else it.previewImage,
            )
        }
        if (selectionChanged) regeneratePreview()
    }

    /** Switches which image in the batch is shown in the preview. */
    fun onImageFocused(index: Int) {
        val state = _uiState.value
        if (index !in state.sourceImages.indices || index == state.selectedIndex) return
        _uiState.update { it.copy(selectedIndex = index, previewImage = null) }
        regeneratePreview()
    }

    // --- Watermark configuration events ---

    fun onWatermarkTypeSelected(type: WatermarkType) = updateConfig { it.copy(type = type) }

    fun onTextChanged(text: String) = updateConfig { it.copy(text = text) }

    fun onPatternSelected(pattern: WatermarkPattern) = updateConfig { it.copy(pattern = pattern) }

    fun onFontSelected(font: WatermarkFont) = updateConfig { it.copy(font = font) }

    fun onColorSelected(colorArgb: Int) = updateConfig { it.copy(colorArgb = colorArgb) }

    fun onOpacityChanged(opacity: Float) =
        updateConfig { it.copy(opacity = opacity.coerceIn(0f, 1f)) }

    fun onTextSizeChanged(ratio: Float) =
        updateConfig { it.copy(textSizeRatio = ratio.coerceIn(0.02f, 0.2f)) }

    fun onImageSizeChanged(ratio: Float) =
        updateConfig { it.copy(imageSizeRatio = ratio.coerceIn(MIN_IMAGE_RATIO, MAX_IMAGE_RATIO)) }

    fun onRotationChanged(degrees: Float) = updateConfig { it.copy(rotationDegrees = degrees) }

    fun onTileSpacingChanged(ratio: Float) =
        updateConfig { it.copy(tileSpacingRatio = ratio.coerceIn(0f, MAX_SPACING_RATIO)) }

    fun onLineSpacingChanged(ratio: Float) =
        updateConfig { it.copy(lineSpacingRatio = ratio.coerceIn(0f, MAX_SPACING_RATIO)) }

    /**
     * Called after the user picks a watermark image and confirms a crop [rect]
     * and [shape]. Crops/masks it, stores it as the image watermark, and
     * switches to image mode.
     */
    fun onWatermarkImageCropped(sourceUri: String, rect: NormalizedRect, shape: CropShape) {
        viewModelScope.launch {
            when (val result = cropImage(WatermarkImage(sourceUri), rect, shape)) {
                is Outcome.Success ->
                    updateConfig { it.copy(type = WatermarkType.IMAGE, imageUri = result.data.uri) }
                is Outcome.Failure ->
                    emitMessage("Couldn't crop image: ${result.error.message}")
            }
        }
    }

    // --- Persistence ---

    /**
     * Renders and saves every image in the batch. When [deleteOriginals] is set
     * (and the batch came from the gallery), emits a request for the screen to
     * launch the system delete-consent dialog once all images saved.
     */
    fun onSaveRequested(deleteOriginals: Boolean = false) {
        val state = _uiState.value
        val sources = state.sourceImages
        if (sources.isEmpty()) {
            emitMessage("Nothing to save yet.")
            return
        }
        if (state.pipeline.isEmpty) {
            emitMessage("Add watermark text or choose a watermark image first.")
            return
        }
        val pipeline = state.pipeline
        val originals = if (deleteOriginals && state.canDeleteOriginals) sources.map { it.uri } else emptyList()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = processAndSaveImages(sources, pipeline)
            _uiState.update { it.copy(isSaving = false) }

            when {
                !result.anySucceeded ->
                    emitMessage("Save failed: ${result.errors.firstOrNull()?.message ?: "unknown error"}")
                // Only delete originals if every image was saved, so we never
                // remove an original whose watermarked copy failed to save.
                originals.isNotEmpty() && result.allSucceeded ->
                    _effects.send(EditorEffect.RequestDeleteOriginals(originals))
                else ->
                    emitMessage(savedMessage(result))
            }
        }
    }

    /** Called back by the screen once the system delete dialog is resolved. */
    fun onOriginalsDeleteResult(deleted: Boolean) {
        if (deleted) {
            _uiState.update {
                it.copy(
                    sourceImages = emptyList(),
                    selectedIndex = 0,
                    sourceFromGallery = false,
                    previewImage = null,
                )
            }
            emitMessage("Saved and removed originals ✓")
        } else {
            emitMessage("Saved ✓ · originals kept")
        }
    }

    private fun savedMessage(result: BatchSaveResult): String = when {
        result.requested == 1 -> "Saved to gallery ✓"
        result.allSucceeded -> "Saved ${result.savedCount} images to gallery ✓"
        else -> "Saved ${result.savedCount} of ${result.requested}; ${result.errors.size} failed"
    }

    private fun updateConfig(transform: (WatermarkConfig) -> WatermarkConfig) {
        _uiState.update { it.copy(config = transform(it.config)) }
        regeneratePreview()
    }

    /**
     * Debounces edits, then renders the watermark onto the selected source image
     * via the real engine so the preview is pixel-accurate to what will be saved.
     */
    private fun regeneratePreview() {
        val state = _uiState.value
        val source = state.selectedSource ?: return
        if (state.pipeline.isEmpty) {
            _uiState.update { it.copy(previewImage = null) }
            return
        }

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            _uiState.update { it.copy(isRendering = true) }
            when (val result = applyPipeline(source, _uiState.value.pipeline)) {
                is Outcome.Success ->
                    _uiState.update { it.copy(previewImage = result.data, isRendering = false) }
                is Outcome.Failure ->
                    _uiState.update { it.copy(isRendering = false) }
                        .also { emitMessage("Preview failed: ${result.error.message}") }
            }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _effects.send(EditorEffect.ShowMessage(message)) }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 250L
        const val MIN_IMAGE_RATIO = 0.05f
        const val MAX_IMAGE_RATIO = 0.8f
        const val MAX_SPACING_RATIO = 3f
    }
}
