package com.momi.watermarker.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.usecase.GetVideoDurationUseCase
import com.momi.watermarker.domain.usecase.SaveVideoUseCase
import com.momi.watermarker.domain.usecase.TrimVideoUseCase
import com.momi.watermarker.domain.util.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the video editor (Phase 0 spike): probes duration on selection, tracks
 * the trim window, and runs the trim → save pipeline. Holds no Android UI or
 * storage types.
 */
@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    private val getVideoDuration: GetVideoDurationUseCase,
    private val trimVideo: TrimVideoUseCase,
    private val saveVideo: SaveVideoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoEditorUiState())
    val uiState: StateFlow<VideoEditorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<VideoEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Called after the user picks a video from the gallery. */
    fun onVideoSelected(uri: String) {
        val clip = VideoClip(uri)
        _uiState.update {
            VideoEditorUiState(sourceClip = clip)
        }
        viewModelScope.launch {
            when (val result = getVideoDuration(clip)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        durationMs = result.data,
                        trimStartMs = 0L,
                        trimEndMs = result.data,
                    )
                }
                is Outcome.Failure ->
                    emitMessage("Couldn't read the video: ${result.error.message}")
            }
        }
    }

    /** Updates the trim window as the user drags the range slider (ms). */
    fun onTrimRangeChanged(startMs: Long, endMs: Long) {
        val duration = _uiState.value.durationMs
        if (duration <= 0L) return
        val clampedStart = startMs.coerceIn(0L, duration)
        val clampedEnd = endMs.coerceIn(clampedStart, duration)
        _uiState.update { it.copy(trimStartMs = clampedStart, trimEndMs = clampedEnd) }
    }

    /**
     * Trims the source to the selected window and saves the result to the
     * gallery — the full end-to-end pipeline for the spike.
     */
    fun onExportRequested() {
        val state = _uiState.value
        val source = state.sourceClip
        if (source == null || !state.canExport) {
            emitMessage("Pick a video and choose a trim range first.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val trimmed = trimVideo(source, state.trimStartMs, state.trimEndMs)
            when (trimmed) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(resultClip = trimmed.data) }
                    when (val saved = saveVideo(trimmed.data, "MomiVideo_${state.trimStartMs}_${state.trimEndMs}")) {
                        is Outcome.Success -> emitMessage("Trimmed video saved to gallery ✓")
                        is Outcome.Failure -> emitMessage("Trimmed, but save failed: ${saved.error.message}")
                    }
                }
                is Outcome.Failure ->
                    emitMessage("Trim failed: ${trimmed.error.message}")
            }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _effects.send(VideoEditorEffect.ShowMessage(message)) }
    }
}
