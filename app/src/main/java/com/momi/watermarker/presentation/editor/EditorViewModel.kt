package com.momi.watermarker.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.domain.model.AspectRatioPreset
import com.momi.watermarker.domain.model.CompressionMode
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.FrameStyle
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
import com.momi.watermarker.domain.usecase.EstimateExportSizeUseCase
import com.momi.watermarker.domain.usecase.GetImageInfoUseCase
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
    private val getImageInfo: GetImageInfoUseCase,
    private val estimateExportSize: EstimateExportSizeUseCase,
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

    /** Tracks the in-flight export-size estimate so rapid export edits cancel stale work. */
    private var estimateJob: Job? = null

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
                selectedTool = batchSafeTool(state.selectedTool, merged.size),
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
                selectedTool = batchSafeTool(state.selectedTool, merged.size),
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
                    selectedImageInfo = null,
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

    /**
     * Stores a crop (rectangle + shape) chosen in the cropper. A non-rectangular
     * shape adds transparency, so the export format is bumped to an alpha-capable
     * one (PNG) if it was still the default JPEG.
     */
    fun onCropChanged(rect: NormalizedRect, shape: CropShape) =
        updateAndPreview(tag = null) { state ->
            // Keep the chosen masked-area background across re-crops.
            val next = state.copy(crop = state.crop.copy(rect = rect, shape = shape))
            next.copy(exportOptions = next.alphaSafeExport())
        }

    /**
     * Sets the fill for the masked area of a shaped crop: null keeps it
     * transparent (bumping JPEG → PNG so the alpha survives), a color fills it.
     */
    fun onCropBackgroundChanged(argb: Int?) =
        updateAndPreview(tag = null) { state ->
            val next = state.copy(crop = state.crop.copy(backgroundArgb = argb))
            next.copy(exportOptions = next.alphaSafeExport())
        }

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
        updateResize("resize.percent") {
            it.copy(percent = percent.coerceIn(ImageOp.Resize.MIN_PERCENT, ImageOp.Resize.MAX_PERCENT))
        }

    fun onResizeMaxDimensionChanged(maxDimensionPx: Int) =
        updateResize(tag = null) { it.copy(maxDimensionPx = maxDimensionPx.coerceAtLeast(1)) }

    /** Clears any scaling (back to full size). */
    fun onResetResize() = updateResize(tag = null) { ImageOp.Resize() }

    // --- Aspect-ratio (padding) events ---

    /** Pads the image to [preset]; transparent bars bump JPEG → PNG. */
    fun onAspectPresetSelected(preset: AspectRatioPreset) =
        updateAndPreview(tag = null) { state ->
            val next = state.copy(aspectPad = state.aspectPad.copy(preset = preset))
            next.copy(exportOptions = next.alphaSafeExport())
        }

    /** Sets the bar fill: null keeps them transparent, a color fills them. */
    fun onAspectFillChanged(argb: Int?) =
        updateAndPreview(tag = null) { state ->
            val next = state.copy(aspectPad = state.aspectPad.copy(fillArgb = argb))
            next.copy(exportOptions = next.alphaSafeExport())
        }

    /** Clears aspect-ratio padding (back to the image's own ratio). */
    fun onResetAspect() = updateAndPreview(tag = null) { it.copy(aspectPad = ImageOp.AspectPad()) }

    // --- Filter events ---

    fun onFilterSelected(filter: PhotoFilter) =
        updateAndPreview(tag = null) { it.copy(filter = ImageOp.Filter(filter)) }

    /**
     * Applies a user-picked custom color tint, clearing any preset. Coalesced
     * under one undo step since it is driven by dragging the RGB sliders.
     */
    fun onCustomTintChanged(colorArgb: Int) =
        updateAndPreview("filter.tint") { it.copy(filter = ImageOp.Filter(customTintArgb = colorArgb)) }

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

    // --- Pixelate events ---

    fun onPixelateBlockChanged(blockSizePx: Int) =
        mutate("pixelate.block") { it.copy(pixelate = ImageOp.Pixelate(blockSizePx.coerceAtLeast(1))) }

    /** Turns the mosaic effect off. */
    fun onResetPixelate() = mutate(tag = null) { it.copy(pixelate = ImageOp.Pixelate()) }

    // --- Frame events ---

    /** Selects a frame style; a rounded frame adds transparency, so bump to PNG. */
    fun onFrameStyleSelected(style: FrameStyle) =
        updateAndPreview(tag = null) { state ->
            val next = state.copy(frame = state.frame.copy(style = style))
            next.copy(exportOptions = next.alphaSafeExport())
        }

    fun onFrameWidthChanged(ratio: Float) =
        updateFrame("frame.width") { it.copy(widthRatio = ratio.coerceIn(MIN_FRAME_RATIO, MAX_FRAME_RATIO)) }

    fun onFrameColorSelected(colorArgb: Int) = updateFrame(tag = null) { it.copy(colorArgb = colorArgb) }

    /** Toggles a see-through area outside the frame; transparent bumps JPEG → PNG. */
    fun onFrameTransparentChanged(transparent: Boolean) =
        updateAndPreview(tag = null) { state ->
            val next = state.copy(frame = state.frame.copy(transparentBackground = transparent))
            next.copy(exportOptions = next.alphaSafeExport())
        }

    fun onFrameCornerRadiusChanged(ratio: Float) =
        updateFrame("frame.corner") { it.copy(cornerRadiusRatio = ratio.coerceIn(0f, MAX_CORNER_RATIO)) }

    /** Removes the frame. */
    fun onResetFrame() = updateAndPreview(tag = null) { it.copy(frame = ImageOp.Frame()) }

    // --- Export events ---

    fun onExportFormatSelected(format: ExportFormat) =
        updateExport(tag = null) { it.copy(format = format) }

    fun onExportQualityChanged(quality: Int) =
        updateExport("export.quality") { it.copy(quality = quality.coerceIn(0, 100)) }

    fun onCompressionModeSelected(mode: CompressionMode) =
        updateExport(tag = null) { export ->
            val target = if (mode == CompressionMode.TARGET_SIZE) {
                export.targetSizeBytes ?: ExportOptions.TARGET_SIZE_PRESETS.first()
            } else {
                export.targetSizeBytes
            }
            export.copy(mode = mode, targetSizeBytes = target)
        }

    fun onTargetSizeSelected(bytes: Long) =
        updateExport(tag = null) { it.copy(targetSizeBytes = bytes) }

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
        val export = state.alphaSafeExport()
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
                    selectedImageInfo = null,
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

    private fun updateFrame(tag: String?, reduce: (ImageOp.Frame) -> ImageOp.Frame) =
        mutate(tag) { it.copy(frame = reduce(it.frame)) }

    /** Applies an export-options change (no visible preview) and refreshes the size estimate. */
    private fun updateExport(tag: String?, reduce: (ExportOptions) -> ExportOptions) {
        mutate(tag, preview = false) { it.copy(exportOptions = reduce(it.exportOptions)) }
        refreshExportEstimate()
    }

    private fun updateAndPreview(tag: String?, reduce: (EditorUiState) -> EditorUiState) =
        mutate(tag = tag, reduce = reduce)

    /** The current tool, swapped to the default if it isn't allowed for [imageCount] images. */
    private fun batchSafeTool(current: EditorTool, imageCount: Int): EditorTool =
        if (imageCount > 1 && !current.supportsBatch) EditorTool.DEFAULT else current

    /** Bumps a JPEG export to PNG when the pipeline would produce transparency. */
    private fun EditorUiState.alphaSafeExport(): ExportOptions =
        if (producesTransparency && exportOptions.format == ExportFormat.JPEG) {
            exportOptions.copy(format = ExportFormat.PNG)
        } else {
            exportOptions
        }

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
            refreshShownImageInfo()
            refreshExportEstimate()
            return
        }

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            _uiState.update { it.copy(isRendering = true) }
            // A shaped crop introduces transparency, so render the preview in an
            // alpha-capable format; otherwise a fast JPEG is fine.
            val previewExport = if (_uiState.value.producesTransparency) {
                ExportOptions(format = ExportFormat.PNG)
            } else {
                ExportOptions()
            }
            when (val result = applyPipeline(source, _uiState.value.pipeline, previewExport)) {
                is Outcome.Success ->
                    _uiState.update { it.copy(previewImage = result.data, isRendering = false) }
                is Outcome.Failure ->
                    _uiState.update { it.copy(isRendering = false) }
                        .also { emitMessage("Preview failed: ${result.error.message}") }
            }
            refreshShownImageInfo()
            refreshExportEstimate()
        }
    }

    /**
     * Estimates the export size of the shown image (rendered preview when present,
     * otherwise the source) under the current export options, debounced. Stale
     * results are dropped if the shown image or export options change meanwhile.
     */
    private fun refreshExportEstimate() {
        val state = _uiState.value
        val shown = state.previewImage ?: state.selectedSource
        if (shown == null) {
            _uiState.update { it.copy(estimatedExportSize = null) }
            return
        }
        val export = state.exportOptions
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            delay(ESTIMATE_DEBOUNCE_MS)
            when (val result = estimateExportSize(shown, export)) {
                is Outcome.Success -> _uiState.update {
                    val current = it.previewImage ?: it.selectedSource
                    if (current?.uri == shown.uri && it.exportOptions == export) {
                        it.copy(estimatedExportSize = result.data)
                    } else {
                        it
                    }
                }
                is Outcome.Failure -> Unit
            }
        }
    }

    /**
     * Reads the dimensions/size of the image currently shown (the rendered
     * preview when present, otherwise the selected source) and publishes it.
     * Stale results are dropped if the shown image changes meanwhile.
     */
    private fun refreshShownImageInfo() {
        val shown = _uiState.value.let { it.previewImage ?: it.selectedSource }
        if (shown == null) {
            _uiState.update { it.copy(selectedImageInfo = null) }
            return
        }
        viewModelScope.launch {
            when (val result = getImageInfo(shown)) {
                is Outcome.Success -> _uiState.update { state ->
                    val current = state.previewImage ?: state.selectedSource
                    if (current?.uri == shown.uri) state.copy(selectedImageInfo = result.data) else state
                }
                is Outcome.Failure -> Unit
            }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _effects.send(EditorEffect.ShowMessage(message)) }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 250L
        const val ESTIMATE_DEBOUNCE_MS = 300L
        const val MIN_IMAGE_RATIO = 0.05f
        const val MAX_IMAGE_RATIO = 0.8f
        const val MAX_SPACING_RATIO = 3f
        const val MIN_FRAME_RATIO = 0.01f
        const val MAX_FRAME_RATIO = 0.25f
        const val MAX_CORNER_RATIO = 0.5f
        const val MAX_HISTORY = 50
    }
}
