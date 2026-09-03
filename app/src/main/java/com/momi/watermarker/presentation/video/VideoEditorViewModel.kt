package com.momi.watermarker.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.domain.model.TrimRange
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.usecase.ChangeAspectRatioUseCase
import com.momi.watermarker.domain.usecase.CutAndJoinVideoUseCase
import com.momi.watermarker.domain.usecase.GetVideoDurationUseCase
import com.momi.watermarker.domain.usecase.MergeVideosUseCase
import com.momi.watermarker.domain.usecase.OverlayImageUseCase
import com.momi.watermarker.domain.usecase.RemoveAudioUseCase
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
 * Drives the whole video editor: op selection, source picking, per-op controls,
 * and the shared edit → save pipeline. Holds no Android UI or storage types.
 */
@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    private val getVideoDuration: GetVideoDurationUseCase,
    private val trimVideo: TrimVideoUseCase,
    private val cutAndJoin: CutAndJoinVideoUseCase,
    private val mergeVideos: MergeVideosUseCase,
    private val removeAudio: RemoveAudioUseCase,
    private val changeAspectRatio: ChangeAspectRatioUseCase,
    private val overlayImage: OverlayImageUseCase,
    private val saveVideo: SaveVideoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoEditorUiState())
    val uiState: StateFlow<VideoEditorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<VideoEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // --- Navigation between the home picker and an operation ------------------

    /** Enters an operation from the home screen with a clean slate. */
    fun onOpSelected(op: VideoOp) {
        _uiState.value = VideoEditorUiState(op = op)
    }

    /** Returns to the home op-picker, discarding the in-progress operation. */
    fun onBack() {
        _uiState.value = VideoEditorUiState()
    }

    // --- Source selection -----------------------------------------------------

    /** A single video was picked (trim, cut & join, remove-audio, aspect, overlay). */
    fun onVideoSelected(uri: String) {
        val clip = VideoClip(uri)
        _uiState.update {
            it.copy(
                sources = listOf(clip),
                durationMs = 0L,
                trimStartMs = 0L,
                trimEndMs = 0L,
                keepRanges = emptyList(),
                resultClip = null,
            )
        }
        viewModelScope.launch {
            when (val result = getVideoDuration(clip)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        durationMs = result.data,
                        trimStartMs = 0L,
                        trimEndMs = result.data,
                        keepRanges = listOf(TrimRange(0L, result.data)),
                    )
                }
                is Outcome.Failure ->
                    emitMessage("Couldn't read the video: ${result.error.message}")
            }
        }
    }

    /** Multiple videos were picked (merge), in the order chosen. */
    fun onVideosSelected(uris: List<String>) {
        _uiState.update {
            it.copy(sources = uris.map(::VideoClip), resultClip = null)
        }
    }

    /** An overlay image was picked. */
    fun onOverlaySelected(uri: String) {
        _uiState.update { it.copy(overlayUri = uri, resultClip = null) }
    }

    // --- Per-op controls ------------------------------------------------------

    fun onTrimRangeChanged(startMs: Long, endMs: Long) {
        val duration = _uiState.value.durationMs
        if (duration <= 0L) return
        val start = startMs.coerceIn(0L, duration)
        val end = endMs.coerceIn(start, duration)
        _uiState.update { it.copy(trimStartMs = start, trimEndMs = end) }
    }

    fun onAddKeepRange() {
        val duration = _uiState.value.durationMs
        if (duration <= 0L) return
        _uiState.update { it.copy(keepRanges = it.keepRanges + TrimRange(0L, duration)) }
    }

    fun onKeepRangeChanged(index: Int, startMs: Long, endMs: Long) {
        val duration = _uiState.value.durationMs
        if (duration <= 0L) return
        val start = startMs.coerceIn(0L, duration)
        val end = endMs.coerceIn(start, duration)
        _uiState.update {
            it.copy(
                keepRanges = it.keepRanges.mapIndexed { i, range ->
                    if (i == index) TrimRange(start, end) else range
                },
            )
        }
    }

    fun onRemoveKeepRange(index: Int) {
        _uiState.update {
            it.copy(keepRanges = it.keepRanges.filterIndexed { i, _ -> i != index })
        }
    }

    fun onAspectRatioSelected(option: AspectRatioOption) {
        _uiState.update { it.copy(aspectRatio = option) }
    }

    fun onOverlayAlphaChanged(alpha: Float) {
        _uiState.update { it.copy(overlayAlpha = alpha.coerceIn(0f, 1f)) }
    }

    fun onReorderSource(from: Int, to: Int) {
        _uiState.update { state ->
            val list = state.sources.toMutableList()
            if (from in list.indices && to in list.indices) {
                list.add(to, list.removeAt(from))
            }
            state.copy(sources = list)
        }
    }

    // --- Export ---------------------------------------------------------------

    /** Runs the active operation, then saves the result to the gallery. */
    fun onExportRequested() {
        val state = _uiState.value
        val op = state.op
        if (op == null || !state.canExport) {
            emitMessage("Finish setting up the operation first.")
            return
        }
        val source = state.primarySource

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val edited: Outcome<VideoClip> = when (op) {
                VideoOp.TRIM ->
                    trimVideo(source!!, state.trimStartMs, state.trimEndMs)
                VideoOp.CUT_JOIN ->
                    cutAndJoin(source!!, state.keepRanges)
                VideoOp.MERGE ->
                    mergeVideos(state.sources)
                VideoOp.REMOVE_AUDIO ->
                    removeAudio(source!!)
                VideoOp.ASPECT_RATIO ->
                    changeAspectRatio(source!!, state.aspectRatio.ratio!!)
                VideoOp.OVERLAY ->
                    overlayImage(source!!, state.overlayUri!!, state.overlayAlpha)
            }
            when (edited) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(resultClip = edited.data) }
                    val name = "MomiVideo_${op.name.lowercase()}_${System.currentTimeMillis()}"
                    when (val saved = saveVideo(edited.data, name)) {
                        is Outcome.Success -> emitMessage("${op.title} saved to gallery ✓")
                        is Outcome.Failure ->
                            emitMessage("Edited, but save failed: ${saved.error.message}")
                    }
                }
                is Outcome.Failure ->
                    emitMessage("${op.title} failed: ${edited.error.message}")
            }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _effects.send(VideoEditorEffect.ShowMessage(message)) }
    }
}
