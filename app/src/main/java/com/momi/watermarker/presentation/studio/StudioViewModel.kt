package com.momi.watermarker.presentation.studio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.R
import com.momi.watermarker.domain.model.LayerContent
import com.momi.watermarker.domain.model.LayerDocument
import com.momi.watermarker.domain.model.LayerIds
import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.model.StudioBackdrop
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.model.isUsableSelection
import com.momi.watermarker.domain.model.translated
import com.momi.watermarker.domain.usecase.CutoutPathUseCase
import com.momi.watermarker.domain.usecase.ExtractPeopleUseCase
import com.momi.watermarker.domain.usecase.FlattenLayerDocumentUseCase
import com.momi.watermarker.domain.usecase.GetImageInfoUseCase
import com.momi.watermarker.domain.usecase.ProposeSubjectOutlineUseCase
import com.momi.watermarker.domain.usecase.SaveImageUseCase
import com.momi.watermarker.domain.util.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the layered Studio: one photo, a layer stack, cut-out / portrait-look /
 * backdrop tools, undo, and flatten-to-gallery save.
 */
@HiltViewModel
class StudioViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getImageInfo: GetImageInfoUseCase,
    private val proposeSubjectOutline: ProposeSubjectOutlineUseCase,
    private val cutoutPath: CutoutPathUseCase,
    private val extractPeople: ExtractPeopleUseCase,
    private val flattenDocument: FlattenLayerDocumentUseCase,
    private val saveImage: SaveImageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _effects = Channel<StudioEvent>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val undoStack = ArrayDeque<LayerDocument>()
    private val redoStack = ArrayDeque<LayerDocument>()
    private var lastHistoryTag: String? = null
    private var flattenJob: Job? = null
    private var extractJob: Job? = null

    fun onImageSelected(uri: String) {
        extractJob?.cancel()
        flattenJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        lastHistoryTag = null
        _uiState.value = StudioUiState(isRendering = true)
        viewModelScope.launch {
            when (val info = getImageInfo(WatermarkImage(uri))) {
                is Outcome.Success -> {
                    val doc = LayerDocument.fromPhoto(uri, info.data.width, info.data.height)
                    _uiState.update {
                        it.copy(
                            document = doc,
                            fillColorArgb = LayerDocument.DEFAULT_FILL,
                            isSaved = false,
                            canUndo = false,
                            canRedo = false,
                        )
                    }
                    flatten()
                }
                is Outcome.Failure -> {
                    _uiState.update { StudioUiState() }
                    emitMessage(R.string.error_preview_failed, info.error.message.orEmpty())
                }
            }
        }
    }

    fun onCutOut() {
        val source = document()?.sourceUri ?: return
        if (_uiState.value.isTracing || _uiState.value.isBusy) return
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            _uiState.update { it.copy(isSegmenting = true, isTracing = false, cutoutActive = true) }
            when (val result = proposeSubjectOutline(source)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isSegmenting = false,
                        cutoutOutline = result.data,
                        cutoutActive = true,
                    )
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSegmenting = false, cutoutActive = false) }
                    emitMessage(R.string.error_cutout_subject, result.error.message.orEmpty())
                }
            }
        }
    }

    fun onStartTrace() {
        if (document() == null || _uiState.value.isBusy) return
        _uiState.update { it.copy(isTracing = true, cutoutOutline = emptyList(), cutoutActive = true) }
    }

    fun onCancelCutout() {
        extractJob?.cancel()
        _uiState.update {
            it.copy(
                isTracing = false,
                cutoutOutline = emptyList(),
                isSegmenting = false,
                cutoutActive = false,
            )
        }
    }

    fun onTraceCompleted(outline: List<NormalizedPoint>) {
        if (!outline.isUsableSelection()) {
            emitMessage(R.string.studio_trace_too_small)
            return
        }
        _uiState.update { it.copy(isTracing = false, cutoutOutline = outline) }
    }

    fun onOutlinePointMoved(index: Int, point: NormalizedPoint) {
        _uiState.update { state ->
            if (index !in state.cutoutOutline.indices) return@update state
            val next = state.cutoutOutline.toMutableList()
            next[index] = point
            state.copy(cutoutOutline = next)
        }
    }

    fun onOutlineTranslated(dx: Float, dy: Float) {
        _uiState.update { state ->
            if (state.cutoutOutline.isEmpty()) return@update state
            state.copy(cutoutOutline = state.cutoutOutline.translated(dx, dy))
        }
    }

    fun onConfirmCutout() {
        val source = document()?.sourceUri ?: return
        val outline = _uiState.value.cutoutOutline
        if (!outline.isUsableSelection()) {
            emitMessage(R.string.studio_trace_too_small)
            return
        }
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            _uiState.update { it.copy(isSegmenting = true) }
            when (val result = cutoutPath(source, outline)) {
                is Outcome.Success -> {
                    _uiState.update {
                        it.copy(isSegmenting = false, cutoutOutline = emptyList(), cutoutActive = false)
                    }
                    mutate { it.withSubject(result.data) }
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSegmenting = false) }
                    emitMessage(R.string.error_cutout_path, result.error.message.orEmpty())
                }
            }
        }
    }

    fun onPortraitLookToggled(enabled: Boolean) {
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            val current = document() ?: return@launch
            val next = if (enabled) ensureSubject(current) ?: return@launch else current
            recordHistory(tag = null, current)
            applyDocument(next.withPortraitLook(enabled), flattenAfter = true)
        }
    }

    fun onBlurChanged(strength: Float) {
        _uiState.update { it.copy(pendingBlur = strength.coerceIn(0f, 1f)) }
    }

    fun onBlurCommitted() {
        val current = document() ?: return
        val strength = _uiState.value.pendingBlur ?: current.blurStrength()
        _uiState.update { it.copy(pendingBlur = null) }
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            val next = if (strength > 0.01f) ensureSubject(current) ?: return@launch else current
            recordHistory(tag = "studio.blur", current)
            applyDocument(next.withBlur(strength), flattenAfter = true)
        }
    }

    /**
     * Portrait look and background blur both need a subject on top of the
     * adjustment so the person stays sharp. If the user hasn't cut one out,
     * isolate every detected person.
     */
    private suspend fun ensureSubject(current: LayerDocument): LayerDocument? {
        if (current.hasSubject()) return current
        _uiState.update { it.copy(isSegmenting = true) }
        return when (val people = extractPeople(current.sourceUri, PREVIEW_MAX_LONG_EDGE)) {
            is Outcome.Success -> {
                _uiState.update { it.copy(isSegmenting = false) }
                current.withSubject(people.data)
            }
            is Outcome.Failure -> {
                _uiState.update { it.copy(isSegmenting = false) }
                emitMessage(R.string.error_apply_effect, people.error.message.orEmpty())
                null
            }
        }
    }

    fun onBackdropSelected(backdrop: StudioBackdrop) {
        when (backdrop) {
            StudioBackdrop.ORIGINAL -> mutate { it.showingOriginalBackdrop() }
            StudioBackdrop.TRANSPARENT -> mutate { it.showingTransparentBackdrop() }
            StudioBackdrop.COLOR -> mutate { it.showingColorFill(_uiState.value.fillColorArgb) }
            StudioBackdrop.IMAGE -> {
                val existing = (document()?.layer(LayerIds.REPLACEMENT)?.content as? LayerContent.Raster)?.uri
                if (existing != null) mutate { it.showingReplacement(existing) }
            }
        }
    }

    fun onFillColorSelected(argb: Int) {
        _uiState.update { it.copy(fillColorArgb = argb) }
        mutate { it.showingColorFill(argb) }
    }

    fun onReplacementImageSelected(uri: String) {
        mutate { it.showingReplacement(uri) }
    }

    fun onLayerSelected(id: String) {
        _uiState.update { state ->
            val doc = state.document ?: return@update state
            state.copy(document = doc.selecting(id))
        }
    }

    fun onToggleLayerVisibility(id: String) {
        mutate { it.togglingVisibility(id) }
    }

    fun onDeleteSelectedLayer() {
        val id = document()?.selectedLayerId ?: return
        mutate { it.removing(id) }
    }

    fun onUndo() {
        if (undoStack.isEmpty()) return
        val current = document() ?: return
        redoStack.addLast(current)
        lastHistoryTag = null
        applyDocument(undoStack.removeLast(), flattenAfter = true)
    }

    fun onRedo() {
        if (redoStack.isEmpty()) return
        val current = document() ?: return
        undoStack.addLast(current)
        lastHistoryTag = null
        applyDocument(redoStack.removeLast(), flattenAfter = true)
    }

    fun onSaveRequested() {
        val doc = document()
        if (doc == null || !_uiState.value.canSave) {
            emitMessage(R.string.error_nothing_to_save)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val flat = flattenDocument(doc, EXPORT_MAX_LONG_EDGE)) {
                is Outcome.Success -> when (
                    val saved = saveImage(WatermarkImage(flat.data.uri), _uiState.value.exportFormat)
                ) {
                    is Outcome.Success -> {
                        _uiState.update { it.copy(isSaving = false, isSaved = true) }
                        emitMessage(R.string.saved_one_to_gallery)
                    }
                    is Outcome.Failure -> {
                        _uiState.update { it.copy(isSaving = false) }
                        emitMessage(R.string.error_save_failed, saved.error.message.orEmpty())
                    }
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    emitMessage(R.string.error_export_failed, flat.error.message.orEmpty())
                }
            }
        }
    }

    private fun mutate(tag: String? = null, transform: (LayerDocument) -> LayerDocument) {
        val current = document() ?: return
        recordHistory(tag, current)
        applyDocument(transform(current), flattenAfter = true)
    }

    private fun recordHistory(tag: String?, current: LayerDocument) {
        if (tag != null && tag == lastHistoryTag && undoStack.isNotEmpty()) return
        undoStack.addLast(current)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        lastHistoryTag = tag
    }

    private fun applyDocument(document: LayerDocument, flattenAfter: Boolean) {
        _uiState.update {
            it.copy(
                document = document,
                isSaved = false,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                fillColorArgb = document.layer(LayerIds.FILL)?.let { document.fillColorArgb() }
                    ?: it.fillColorArgb,
            )
        }
        if (flattenAfter) flatten()
    }

    private fun flatten() {
        val doc = document() ?: return
        flattenJob?.cancel()
        flattenJob = viewModelScope.launch {
            _uiState.update { it.copy(isRendering = true) }
            when (val result = flattenDocument(doc, PREVIEW_MAX_LONG_EDGE)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(previewUri = result.data.uri, isRendering = false, isSaved = false)
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isRendering = false) }
                    emitMessage(R.string.error_preview_failed, result.error.message.orEmpty())
                }
            }
        }
    }

    private fun document(): LayerDocument? = _uiState.value.document

    private fun emitMessage(resId: Int, vararg formatArgs: Any) {
        val message =
            if (formatArgs.isEmpty()) appContext.getString(resId)
            else appContext.getString(resId, *formatArgs)
        viewModelScope.launch { _effects.send(StudioEvent.ShowMessage(message)) }
    }

    private companion object {
        const val PREVIEW_MAX_LONG_EDGE = 1080
        const val EXPORT_MAX_LONG_EDGE = 2560
        const val MAX_HISTORY = 50
    }
}

sealed interface StudioEvent {
    data class ShowMessage(val message: String) : StudioEvent
}
