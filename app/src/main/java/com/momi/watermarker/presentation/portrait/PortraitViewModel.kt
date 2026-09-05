package com.momi.watermarker.presentation.portrait

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.domain.model.PortraitEffect
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.usecase.ApplyPortraitEffectUseCase
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
 * Drives the portrait "selective color + background blur" tool: pick a photo,
 * toggle selective color / background blur, tune the blur, compare before/after,
 * and save a full-resolution export.
 *
 * Previews render at a small resolution for responsiveness; the export re-renders
 * at full resolution only when the user saves.
 */
@HiltViewModel
class PortraitViewModel @Inject constructor(
    private val applyPortraitEffect: ApplyPortraitEffectUseCase,
    private val saveImage: SaveImageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortraitUiState())
    val uiState: StateFlow<PortraitUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PortraitEvent>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Latest preview render; cancelled when a newer request supersedes it.
    private var renderJob: Job? = null

    fun onImageSelected(uri: String) {
        renderJob?.cancel()
        _uiState.value = PortraitUiState(sourceUri = uri)
        render()
    }

    fun onSelectiveColorToggled(enabled: Boolean) {
        _uiState.update { it.copy(selectiveColor = enabled).invalidatingResult() }
        render()
    }

    fun onBackgroundBlurToggled(enabled: Boolean) {
        _uiState.update { it.copy(backgroundBlur = enabled).invalidatingResult() }
        render()
    }

    /** Updates the blur amount without re-rendering (call [onBlurCommitted] on release). */
    fun onBlurChanged(strength: Float) {
        _uiState.update { it.copy(blurStrength = strength.coerceIn(0f, 1f)) }
    }

    /** Re-renders after the blur slider settles. */
    fun onBlurCommitted() {
        if (_uiState.value.backgroundBlur && _uiState.value.selectiveColor) render()
    }

    /** Toggles the before/after compare view. */
    fun onShowOriginalChanged(show: Boolean) {
        _uiState.update { it.copy(showOriginal = show) }
    }

    /** Renders a downscaled preview of the current effect. No-op without a source. */
    private fun render() {
        val state = _uiState.value
        val source = state.sourceUri ?: return
        renderJob?.cancel()
        val effect = state.effect
        if (effect == null) {
            // Selective color off: nothing to process; preview is the original.
            _uiState.update { it.copy(resultUri = null, isRendering = false) }
            return
        }
        renderJob = viewModelScope.launch {
            _uiState.update { it.copy(isRendering = true) }
            when (val result = applyPortraitEffect(source, effect, PREVIEW_MAX_LONG_EDGE)) {
                is Outcome.Success -> {
                    // Ignore a result whose source has since been replaced.
                    if (_uiState.value.sourceUri != source) return@launch
                    _uiState.update { it.copy(resultUri = result.data, isRendering = false, isSaved = false) }
                }
                is Outcome.Failure -> {
                    if (_uiState.value.sourceUri != source) return@launch
                    _uiState.update { it.copy(isRendering = false) }
                    emitMessage("Couldn't apply the effect: ${result.error.message}")
                }
            }
        }
    }

    fun onSaveRequested() {
        val state = _uiState.value
        val source = state.sourceUri
        val effect = state.effect
        if (source == null || effect == null || !state.canSave) {
            emitMessage("Nothing to save yet.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            // Re-render at full resolution for the export.
            when (val rendered = applyPortraitEffect(source, effect, EXPORT_MAX_LONG_EDGE)) {
                is Outcome.Success ->
                    when (val saved = saveImage(WatermarkImage(rendered.data), state.exportFormat)) {
                        is Outcome.Success -> {
                            _uiState.update { it.copy(isSaving = false, isSaved = true) }
                            emitMessage("Saved to gallery ✓")
                        }
                        is Outcome.Failure -> {
                            _uiState.update { it.copy(isSaving = false) }
                            emitMessage("Save failed: ${saved.error.message}")
                        }
                    }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    emitMessage("Export failed: ${rendered.error.message}")
                }
            }
        }
    }

    /** Drops a stale result so a superseded preview can't be saved. */
    private fun PortraitUiState.invalidatingResult(): PortraitUiState =
        copy(resultUri = null, isSaved = false)

    private fun emitMessage(message: String) {
        viewModelScope.launch { _effects.send(PortraitEvent.ShowMessage(message)) }
    }

    private companion object {
        // Small enough to stay responsive; large enough to judge the effect.
        const val PREVIEW_MAX_LONG_EDGE = 1080
        // Full-resolution ceiling for the export (guards against OOM on huge images).
        const val EXPORT_MAX_LONG_EDGE = 2560
    }
}

/** One-shot side effects surfaced to the screen. */
sealed interface PortraitEvent {
    data class ShowMessage(val message: String) : PortraitEvent
}
