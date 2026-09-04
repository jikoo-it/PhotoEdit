package com.momi.watermarker.presentation.cutout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.domain.model.BackgroundMode
import com.momi.watermarker.domain.model.CutoutRenderSpec
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.usecase.CutoutSubjectUseCase
import com.momi.watermarker.domain.usecase.RenderCutoutUseCase
import com.momi.watermarker.domain.usecase.SaveImageUseCase
import com.momi.watermarker.domain.util.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * Drives the single-image cut-out studio: pick a photo, extract the subject
 * once, then re-render as the background choice changes, and finally save.
 */
@HiltViewModel
class CutoutViewModel @Inject constructor(
    private val cutoutSubject: CutoutSubjectUseCase,
    private val renderCutout: RenderCutoutUseCase,
    private val saveImage: SaveImageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CutoutUiState())
    val uiState: StateFlow<CutoutUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CutoutEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Latest subject extraction / background render; each is cancelled when a
    // newer request supersedes it.
    private var segmentJob: Job? = null
    private var renderJob: Job? = null

    /** A new photo was picked: reset, then extract its subject. */
    fun onImageSelected(uri: String) {
        segmentJob?.cancel()
        renderJob?.cancel()
        _uiState.value = CutoutUiState(sourceUri = uri, isSegmenting = true)
        segmentJob = viewModelScope.launch {
            val result = cutoutSubject(uri)
            // Ignore a result whose photo has since been replaced.
            if (_uiState.value.sourceUri != uri) return@launch
            when (result) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(cutoutUri = result.data, isSegmenting = false) }
                    render()
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSegmenting = false) }
                    emitMessage("Couldn't cut out a subject: ${result.error.message}")
                }
            }
        }
    }

    fun onModeSelected(mode: BackgroundMode) {
        _uiState.update { it.copy(mode = mode).invalidatingResult() }
        render()
    }

    fun onColorSelected(argb: Int) {
        _uiState.update { it.copy(backgroundColorArgb = argb).invalidatingResult() }
        if (_uiState.value.mode == BackgroundMode.COLOR) render()
    }

    fun onBackgroundImageSelected(uri: String) {
        _uiState.update {
            it.copy(backgroundImageUri = uri, mode = BackgroundMode.IMAGE).invalidatingResult()
        }
        render()
    }

    /** Updates the blur amount without re-rendering (call [onBlurCommitted] on release). */
    fun onBlurChanged(strength: Float) {
        _uiState.update { it.copy(blurStrength = strength.coerceIn(0f, 1f)) }
    }

    /** Re-renders after the blur slider settles. */
    fun onBlurCommitted() {
        if (_uiState.value.mode == BackgroundMode.BLUR) render()
    }

    /** Composites the current background choice into a preview. No-op until a subject exists. */
    private fun render() {
        val state = _uiState.value
        val cutoutUri = state.cutoutUri ?: return
        // Supersede any in-flight render before deciding what to do.
        renderJob?.cancel()
        if (state.mode == BackgroundMode.IMAGE && state.backgroundImageUri == null) {
            // Nothing to render yet — waiting for a background image.
            _uiState.update { it.copy(resultUri = null, isRendering = false) }
            return
        }
        renderJob = viewModelScope.launch {
            _uiState.update { it.copy(isRendering = true) }
            val spec = CutoutRenderSpec(
                sourceUri = state.sourceUri!!,
                cutoutUri = cutoutUri,
                mode = state.mode,
                backgroundColorArgb = state.backgroundColorArgb,
                backgroundImageUri = state.backgroundImageUri,
                blurStrength = state.blurStrength,
            )
            when (val result = renderCutout(spec)) {
                is Outcome.Success ->
                    _uiState.update { it.copy(resultUri = result.data, isRendering = false, isSaved = false) }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isRendering = false) }
                    emitMessage("Preview failed: ${result.error.message}")
                }
            }
        }
    }

    fun onSaveRequested() {
        val state = _uiState.value
        val result = state.resultUri
        if (result == null || !state.canSave) {
            emitMessage("Nothing to save yet.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val saved = saveImage(WatermarkImage(result), state.exportFormat)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                    emitMessage("Saved to gallery ✓")
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    emitMessage("Save failed: ${saved.error.message}")
                }
            }
        }
    }

    /** Drops a stale result so a superseded preview can't be saved. */
    private fun CutoutUiState.invalidatingResult(): CutoutUiState =
        copy(resultUri = null, isSaved = false)

    private fun emitMessage(message: String) {
        viewModelScope.launch { _effects.send(CutoutEffect.ShowMessage(message)) }
    }
}

/** One-shot side effects surfaced to the screen. */
sealed interface CutoutEffect {
    data class ShowMessage(val message: String) : CutoutEffect
}
