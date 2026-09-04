package com.momi.watermarker.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.OverlayPosition
import com.momi.watermarker.domain.model.TrimRange
import com.momi.watermarker.domain.model.SlideTransition
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoColorFilter
import com.momi.watermarker.domain.usecase.ApplyVideoFilterUseCase
import com.momi.watermarker.domain.usecase.ChangeAspectRatioUseCase
import com.momi.watermarker.domain.usecase.CreateSlideshowUseCase
import com.momi.watermarker.domain.usecase.CutAndJoinVideoUseCase
import com.momi.watermarker.domain.usecase.GetVideoDurationUseCase
import com.momi.watermarker.domain.usecase.MergeVideosUseCase
import com.momi.watermarker.domain.usecase.OverlayImageUseCase
import com.momi.watermarker.domain.usecase.RemoveAudioUseCase
import com.momi.watermarker.domain.usecase.SaveVideoUseCase
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
    private val cutAndJoin: CutAndJoinVideoUseCase,
    private val mergeVideos: MergeVideosUseCase,
    private val removeAudio: RemoveAudioUseCase,
    private val changeAspectRatio: ChangeAspectRatioUseCase,
    private val applyVideoFilter: ApplyVideoFilterUseCase,
    private val overlayImage: OverlayImageUseCase,
    private val createSlideshow: CreateSlideshowUseCase,
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
                keepRanges = emptyList(),
                resultClip = null,
            )
        }
        viewModelScope.launch {
            when (val result = getVideoDuration(clip)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        durationMs = result.data,
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
            it.copy(
                sources = uris.map(::VideoClip),
                // One reframe slot per source (defaults to keeping each source's ratio).
                mergeAspects = List(uris.size) { AspectRatioOption.ORIGINAL },
                resultClip = null,
            )
        }
    }

    /** Per-source reframe for a merged clip. */
    fun onMergeAspectChanged(index: Int, option: AspectRatioOption) {
        _uiState.update { state ->
            state.copy(
                mergeAspects = state.mergeAspects.mapIndexed { i, o ->
                    if (i == index) option else o
                },
            ).invalidatingResult()
        }
    }

    /** Selects the whole-video color look. */
    fun onColorFilterSelected(filter: VideoColorFilter) {
        _uiState.update { it.copy(colorFilter = filter).invalidatingResult() }
    }

    /** An overlay image was picked. Clears any crop from a previous image. */
    fun onOverlaySelected(uri: String) {
        _uiState.update {
            it.copy(overlayUri = uri, overlayCropRect = null, overlayCropShape = CropShape.RECTANGLE)
                .invalidatingResult()
        }
    }

    /** Switches between an image/logo overlay and a text overlay. */
    fun onOverlayModeChanged(mode: OverlayMode) {
        _uiState.update { it.copy(overlayMode = mode).invalidatingResult() }
    }

    fun onOverlayTextChanged(text: String) {
        _uiState.update { it.copy(overlayText = text).invalidatingResult() }
    }

    fun onOverlayTextColorChanged(argb: Int) {
        _uiState.update { it.copy(overlayTextColorArgb = argb).invalidatingResult() }
    }

    fun onOverlayPositionChanged(position: OverlayPosition) {
        _uiState.update { it.copy(overlayPosition = position).invalidatingResult() }
    }

    fun onOverlaySizeChanged(fraction: Float) {
        _uiState.update {
            it.copy(overlaySizeFraction = fraction.coerceIn(0.02f, 1f)).invalidatingResult()
        }
    }

    /** Stores a crop chosen for the overlay image. */
    fun onOverlayCropChanged(rect: NormalizedRect, shape: CropShape) {
        _uiState.update {
            it.copy(overlayCropRect = rect, overlayCropShape = shape).invalidatingResult()
        }
    }

    /** Clears the overlay-image crop (back to the whole image). */
    fun onOverlayCropCleared() {
        _uiState.update {
            it.copy(overlayCropRect = null, overlayCropShape = CropShape.RECTANGLE)
                .invalidatingResult()
        }
    }

    /** Images were picked for a slideshow, in the order chosen. */
    fun onSlidesSelected(uris: List<String>) {
        val slides = uris.map { SlideItem(uri = it) }
        _uiState.update {
            it.copy(slides = slides, transitions = defaultTransitions(slides.size))
                .invalidatingResult()
        }
    }

    // --- Slideshow controls ---------------------------------------------------

    fun onSlideDurationChanged(index: Int, durationMs: Long) {
        _uiState.update { state ->
            state.copy(
                slides = state.slides.mapIndexed { i, slide ->
                    if (i == index) slide.copy(durationMs = durationMs.coerceAtLeast(200L)) else slide
                },
            ).invalidatingResult()
        }
    }

    fun onSlideTransitionChanged(boundaryIndex: Int, transition: SlideTransition) {
        _uiState.update { state ->
            state.copy(
                transitions = state.transitions.mapIndexed { i, t ->
                    if (i == boundaryIndex) transition else t
                },
            ).invalidatingResult()
        }
    }

    fun onTransitionDurationChanged(durationMs: Long) {
        _uiState.update {
            it.copy(transitionDurationMs = durationMs.coerceIn(100L, 3_000L)).invalidatingResult()
        }
    }

    fun onSlideshowAspectSelected(option: AspectRatioOption) {
        _uiState.update { it.copy(slideshowAspect = option).invalidatingResult() }
    }

    fun onReorderSlide(from: Int, to: Int) {
        _uiState.update { state ->
            val list = state.slides.toMutableList()
            if (from in list.indices && to in list.indices) {
                list.add(to, list.removeAt(from))
            }
            // Boundaries change on reorder; reset transitions to the default.
            state.copy(slides = list, transitions = defaultTransitions(list.size))
                .invalidatingResult()
        }
    }

    fun onRemoveSlide(index: Int) {
        _uiState.update { state ->
            val list = state.slides.filterIndexed { i, _ -> i != index }
            state.copy(slides = list, transitions = defaultTransitions(list.size))
                .invalidatingResult()
        }
    }

    /** A slideshow defaults to a cross-dissolve between every pair of images. */
    private fun defaultTransitions(slideCount: Int): List<SlideTransition> =
        if (slideCount <= 1) emptyList()
        else List(slideCount - 1) { SlideTransition.DEFAULT }

    // --- Per-op controls ------------------------------------------------------

    fun onAddKeepRange() {
        val duration = _uiState.value.durationMs
        if (duration <= 0L) return
        _uiState.update {
            it.copy(keepRanges = it.keepRanges + TrimRange(0L, duration)).invalidatingResult()
        }
    }

    fun onKeepRangeChanged(index: Int, startMs: Long, endMs: Long) {
        val duration = _uiState.value.durationMs
        if (duration <= 0L) return
        val start = startMs.coerceIn(0L, duration)
        val end = endMs.coerceIn(start, duration)
        _uiState.update {
            it.copy(
                keepRanges = it.keepRanges.mapIndexed { i, range ->
                    if (i == index) range.copy(startMs = start, endMs = end) else range
                },
            ).invalidatingResult()
        }
    }

    /** Sets the playback speed of one kept range. */
    fun onKeepRangeSpeedChanged(index: Int, speed: Float) {
        _uiState.update {
            it.copy(
                keepRanges = it.keepRanges.mapIndexed { i, range ->
                    if (i == index) range.copy(speed = speed.coerceIn(0.25f, 4f)) else range
                },
            ).invalidatingResult()
        }
    }

    fun onRemoveKeepRange(index: Int) {
        _uiState.update {
            it.copy(keepRanges = it.keepRanges.filterIndexed { i, _ -> i != index })
                .invalidatingResult()
        }
    }

    fun onAspectRatioSelected(option: AspectRatioOption) {
        _uiState.update { it.copy(aspectRatio = option).invalidatingResult() }
    }

    fun onOverlayAlphaChanged(alpha: Float) {
        _uiState.update { it.copy(overlayAlpha = alpha.coerceIn(0f, 1f)).invalidatingResult() }
    }

    fun onReorderSource(from: Int, to: Int) {
        _uiState.update { state ->
            val list = state.sources.toMutableList()
            val aspects = state.mergeAspects.toMutableList()
            if (from in list.indices && to in list.indices) {
                list.add(to, list.removeAt(from))
                // Keep the per-source reframe aligned with its clip.
                if (from in aspects.indices && to in aspects.indices) {
                    aspects.add(to, aspects.removeAt(from))
                }
            }
            state.copy(sources = list, mergeAspects = aspects).invalidatingResult()
        }
    }

    /** Drops any previewed result so a stale export can't be saved after edits. */
    private fun VideoEditorUiState.invalidatingResult(): VideoEditorUiState =
        if (resultClip == null && !isSaved) this
        else copy(resultClip = null, isSaved = false)

    // --- Export ---------------------------------------------------------------

    /** Runs the active operation and shows the result for preview (no save yet). */
    fun onProcessRequested() {
        val state = _uiState.value
        val op = state.op
        if (op == null || !state.canExport) {
            emitMessage("Finish setting up the operation first.")
            return
        }
        val source = state.primarySource

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, resultClip = null, isSaved = false) }
            val edited: Outcome<VideoClip> = when (op) {
                VideoOp.CUT_JOIN ->
                    cutAndJoin(source!!, state.keepRanges)
                VideoOp.MERGE ->
                    mergeVideos(state.sources, state.mergeAspects.map { it.ratio })
                VideoOp.REMOVE_AUDIO ->
                    removeAudio(source!!)
                VideoOp.ASPECT_RATIO ->
                    changeAspectRatio(source!!, state.aspectRatio.ratio!!)
                VideoOp.FILTER ->
                    applyVideoFilter(source!!, state.colorFilter)
                VideoOp.OVERLAY ->
                    overlayImage(
                        source = source!!,
                        imageUri = state.overlayUri.takeIf { state.overlayMode == OverlayMode.IMAGE },
                        text = state.overlayText.takeIf { state.overlayMode == OverlayMode.TEXT },
                        textColorArgb = state.overlayTextColorArgb,
                        alpha = state.overlayAlpha,
                        position = state.overlayPosition,
                        sizeFraction = state.overlaySizeFraction,
                        cropRect = state.overlayCropRect,
                        cropShape = state.overlayCropShape,
                    )
                VideoOp.SLIDESHOW ->
                    createSlideshow(
                        frames = state.slides.map {
                            CreateSlideshowUseCase.Frame(it.uri, it.durationMs)
                        },
                        transitions = state.transitions,
                        transitionDurationMs = state.transitionDurationMs,
                        aspectRatio = state.slideshowAspect.ratio,
                    )
            }
            when (edited) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(resultClip = edited.data) }
                    emitMessage("${op.title} ready — preview below, then save.")
                }
                is Outcome.Failure ->
                    emitMessage("${op.title} failed: ${edited.error.message}")
            }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    /** Saves the already-previewed result to the gallery. */
    fun onSaveRequested() {
        val state = _uiState.value
        val result = state.resultClip
        if (result == null) {
            emitMessage("Preview the result first.")
            return
        }
        val op = state.op
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val name = "MomiVideo_${op?.name?.lowercase() ?: "clip"}_${System.currentTimeMillis()}"
            when (val saved = saveVideo(result, name)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isSaved = true) }
                    emitMessage("Saved to gallery ✓")
                }
                is Outcome.Failure ->
                    emitMessage("Save failed: ${saved.error.message}")
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _effects.send(VideoEditorEffect.ShowMessage(message)) }
    }
}
