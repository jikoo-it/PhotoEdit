package com.momi.watermarker.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.PhotoFilter
import com.momi.watermarker.domain.model.ResizeMode
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

    /** Undo/redo history of edit snapshots (most recent at the end). */
    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()

    /**
     * The tag of the last recorded edit, used to coalesce a continuous run of
     * edits (e.g. one slider drag) into a single undo step. Reset on undo/redo.
     */
    private var lastHistoryTag: String? = null

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

    // --- Tool selection ---

    /** Switches the active tool panel. Does not change the pipeline. */
    fun onToolSelected(tool: EditorTool) {
        if (tool == _uiState.value.selectedTool) return
        _uiState.update { it.copy(selectedTool = tool) }
    }

    // --- Crop events ---

    /** Stores a rectangular crop (fractions of the source) chosen in the cropper. */
    fun onCropChanged(rect: NormalizedRect) =
        updateAndPreview(tag = null) { it.copy(crop = ImageOp.Crop(rect)) }

    /** Clears the crop (back to the full image). */
    fun onResetCrop() = updateAndPreview(tag = null) { it.copy(crop = ImageOp.Crop()) }

    // --- Transform events ---

    /** Rotates the image 90° clockwise. */
    fun onRotateClockwise() = updateTransform { it.rotatedClockwise() }

    fun onFlipHorizontal() = updateTransform { it.copy(flipHorizontal = !it.flipHorizontal) }

    fun onFlipVertical() = updateTransform { it.copy(flipVertical = !it.flipVertical) }

    /** Clears all rotate/flip settings. */
    fun onResetTransform() = updateTransform { ImageOp.Transform() }

    // --- Resize events ---

    fun onResizeModeSelected(mode: ResizeMode) = updateResize(tag = null) { it.copy(mode = mode) }

    fun onResizePercentChanged(percent: Float) =
        updateResize("resize.percent") { it.copy(percent = percent.coerceIn(0.01f, 1f)) }

    fun onResizeMaxDimensionChanged(maxDimensionPx: Int) =
        updateResize(tag = null) { it.copy(maxDimensionPx = maxDimensionPx.coerceAtLeast(1)) }

    /** Clears any downscaling (back to full size). */
    fun onResetResize() = updateResize(tag = null) { ImageOp.Resize() }

    // --- Filter events ---

    fun onFilterSelected(filter: PhotoFilter) =
        updateAndPreview(tag = null) { it.copy(filter = ImageOp.Filter(filter)) }

    // --- Adjustment events ---

    fun onBrightnessChanged(value: Float) =
        updateAdjust("adjust.brightness") { it.copy(brightness = value.coerceIn(-1f, 1f)) }

    fun onContrastChanged(value: Float) =
        updateAdjust("adjust.contrast") { it.copy(contrast = value.coerceIn(-1f, 1f)) }

    fun onSaturationChanged(value: Float) =
        updateAdjust("adjust.saturation") { it.copy(saturation = value.coerceIn(-1f, 1f)) }

    fun onWarmthChanged(value: Float) =
        updateAdjust("adjust.warmth") { it.copy(warmth = value.coerceIn(-1f, 1f)) }

    /** Clears all fine-grained adjustments. */
    fun onResetAdjust() = updateAdjust(tag = null) { ImageOp.Adjust() }

    // --- Export events ---

    fun onExportFormatSelected(format: ExportFormat) =
        mutate(tag = null, preview = false) { it.copy(exportOptions = it.exportOptions.copy(format = format)) }

    fun onExportQualityChanged(quality: Int) =
        mutate("export.quality", preview = false) {
            it.copy(exportOptions = it.exportOptions.copy(quality = quality.coerceIn(0, 100)))
        }

    // --- Watermark configuration events ---

    fun onWatermarkTypeSelected(type: WatermarkType) = updateConfig(tag = null) { it.copy(type = type) }

    fun onTextChanged(text: String) = updateConfig("wm.text") { it.copy(text = text) }

    fun onPatternSelected(pattern: WatermarkPattern) = updateConfig(tag = null) { it.copy(pattern = pattern) }

    fun onFontSelected(font: WatermarkFont) = updateConfig(tag = null) { it.copy(font = font) }

    fun onColorSelected(colorArgb: Int) = updateConfig(tag = null) { it.copy(colorArgb = colorArgb) }

    fun onOpacityChanged(opacity: Float) =
        updateConfig("wm.opacity") { it.copy(opacity = opacity.coerceIn(0f, 1f)) }

    fun onTextSizeChanged(ratio: Float) =
        updateConfig("wm.textSize") { it.copy(textSizeRatio = ratio.coerceIn(0.02f, 0.2f)) }

    fun onImageSizeChanged(ratio: Float) =
        updateConfig("wm.imageSize") { it.copy(imageSizeRatio = ratio.coerceIn(MIN_IMAGE_RATIO, MAX_IMAGE_RATIO)) }

    fun onRotationChanged(degrees: Float) = updateConfig("wm.rotation") { it.copy(rotationDegrees = degrees) }

    fun onTileSpacingChanged(ratio: Float) =
        updateConfig("wm.tileSpacing") { it.copy(tileSpacingRatio = ratio.coerceIn(0f, MAX_SPACING_RATIO)) }

    fun onLineSpacingChanged(ratio: Float) =
        updateConfig("wm.lineSpacing") { it.copy(lineSpacingRatio = ratio.coerceIn(0f, MAX_SPACING_RATIO)) }

    /**
     * Called after the user picks a watermark image and confirms a crop [rect]
     * and [shape]. Crops/masks it, stores it as the image watermark, and
     * switches to image mode.
     */
    fun onWatermarkImageCropped(sourceUri: String, rect: NormalizedRect, shape: CropShape) {
        viewModelScope.launch {
            when (val result = cropImage(WatermarkImage(sourceUri), rect, shape)) {
                is Outcome.Success ->
                    updateConfig(tag = null) { it.copy(type = WatermarkType.IMAGE, imageUri = result.data.uri) }
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
        // An empty pipeline is fine — the images are re-encoded per the export
        // options (batch compression / format conversion with no other edits).
        val pipeline = state.pipeline
        val export = state.exportOptions
        val originals = if (deleteOriginals && state.canDeleteOriginals) sources.map { it.uri } else emptyList()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = processAndSaveImages(sources, pipeline, export)
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

    private fun updateConfig(tag: String?, transform: (WatermarkConfig) -> WatermarkConfig) =
        mutate(tag) { it.copy(config = transform(it.config)) }

    private fun updateTransform(reduce: (ImageOp.Transform) -> ImageOp.Transform) =
        mutate(tag = null) { it.copy(transform = reduce(it.transform)) }

    private fun updateResize(tag: String?, reduce: (ImageOp.Resize) -> ImageOp.Resize) =
        mutate(tag) { it.copy(resize = reduce(it.resize)) }

    private fun updateAdjust(tag: String?, reduce: (ImageOp.Adjust) -> ImageOp.Adjust) =
        mutate(tag) { it.copy(adjust = reduce(it.adjust)) }

    private fun updateAndPreview(tag: String?, reduce: (EditorUiState) -> EditorUiState) =
        mutate(tag = tag, reduce = reduce)

    // --- Edit history (undo/redo) ---

    /**
     * Applies an edit: records the pre-edit state for undo (coalescing a run of
     * edits that share a non-null [tag], e.g. a single slider drag), mutates the
     * state, refreshes the undo/redo flags and re-renders the preview.
     */
    private fun mutate(
        tag: String?,
        preview: Boolean = true,
        reduce: (EditorUiState) -> EditorUiState,
    ) {
        recordHistory(tag)
        _uiState.update { reduce(it).copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
        if (preview) regeneratePreview()
    }

    /** Pushes the current edit snapshot onto the undo stack, unless coalesced. */
    private fun recordHistory(tag: String?) {
        // A run of same-tag edits (one continuous interaction) collapses to a
        // single undo step: only the first captures the pre-edit state.
        if (tag != null && tag == lastHistoryTag) return
        undoStack.addLast(_uiState.value.snapshot())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        lastHistoryTag = tag
    }

    /** Reverts to the previous edit state. */
    fun onUndo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_uiState.value.snapshot())
        val previous = undoStack.removeLast()
        lastHistoryTag = null
        applySnapshot(previous)
    }

    /** Re-applies an edit state that was undone. */
    fun onRedo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_uiState.value.snapshot())
        val next = redoStack.removeLast()
        lastHistoryTag = null
        applySnapshot(next)
    }

    /** Clears every edit back to defaults (recorded as one undoable step). */
    fun onResetAllEdits() {
        if (!_uiState.value.hasAnyEdits) return
        recordHistory(tag = null)
        applySnapshot(EditSnapshot.PRISTINE)
    }

    private fun applySnapshot(snapshot: EditSnapshot) {
        _uiState.update {
            it.restore(snapshot).copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
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
        const val MAX_HISTORY = 50
    }
}
