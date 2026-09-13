package com.momi.watermarker.presentation.batch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momi.watermarker.R
import com.momi.watermarker.domain.model.CompressionMode
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.Pipeline
import com.momi.watermarker.domain.model.ResizeMode
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.usecase.BatchSaveResult
import com.momi.watermarker.domain.usecase.FitImagesToSizeUseCase
import com.momi.watermarker.domain.usecase.ProcessAndSaveImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BatchResizeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val processAndSaveImages: ProcessAndSaveImagesUseCase,
    private val fitImagesToSize: FitImagesToSizeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchResizeUiState())
    val uiState: StateFlow<BatchResizeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BatchResizeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onOpSelected(op: BatchResizeOp) {
        _uiState.value = BatchResizeUiState(
            op = op,
            exportOptions = defaultExportFor(op),
        )
    }

    fun onBack() {
        _uiState.value = BatchResizeUiState()
    }

    fun onImagesSelected(uris: List<String>) {
        _uiState.update {
            it.copy(
                sources = uris.map(::WatermarkImage),
                saved = false,
            )
        }
    }

    fun onResizeModeSelected(mode: ResizeMode) =
        updateResize { it.copy(mode = mode) }

    fun onResizePercentChanged(percent: Float) =
        updateResize {
            it.copy(percent = percent.coerceIn(ImageOp.Resize.MIN_PERCENT, ImageOp.Resize.MAX_PERCENT))
        }

    fun onResizeMaxDimensionChanged(maxDimensionPx: Int) =
        updateResize { it.copy(maxDimensionPx = maxDimensionPx.coerceAtLeast(1)) }

    fun onResizeWidthChanged(widthPx: Int) =
        updateResize { it.withExactWidth(widthPx) }

    fun onResizeHeightChanged(heightPx: Int) =
        updateResize { it.withExactHeight(heightPx) }

    fun onResizeLockAspectChanged(lock: Boolean) =
        updateResize { it.copy(lockAspectRatio = lock) }

    fun onExportFormatSelected(format: ExportFormat) {
        _uiState.update { state ->
            val nextFormat =
                if (state.op == BatchResizeOp.FILE_SIZE && !format.supportsQuality) {
                    ExportFormat.JPEG
                } else {
                    format
                }
            state.copy(exportOptions = state.exportOptions.copy(format = nextFormat), saved = false)
        }
    }

    fun onExportQualityChanged(quality: Int) {
        _uiState.update {
            it.copy(
                exportOptions = it.exportOptions.copy(quality = quality.coerceIn(0, 100)),
                saved = false,
            )
        }
    }

    fun onCompressionModeSelected(mode: CompressionMode) {
        _uiState.update { state ->
            val target = if (mode == CompressionMode.TARGET_SIZE) {
                state.exportOptions.targetSizeBytes ?: ExportOptions.TARGET_SIZE_PRESETS.first()
            } else {
                state.exportOptions.targetSizeBytes
            }
            state.copy(
                exportOptions = state.exportOptions.copy(mode = mode, targetSizeBytes = target),
                saved = false,
            )
        }
    }

    fun onTargetSizeSelected(bytes: Long) {
        _uiState.update {
            it.copy(
                exportOptions = it.exportOptions.copy(targetSizeBytes = bytes),
                saved = false,
            )
        }
    }

    fun onCustomTargetSizeKbChanged(kb: Long) {
        _uiState.update {
            it.copy(
                exportOptions = it.exportOptions.copy(
                    targetSizeBytes = ExportOptions.bytesFromKb(kb),
                ),
                saved = false,
            )
        }
    }

    fun onProcessRequested() {
        val state = _uiState.value
        val op = state.op
        if (op == null || !state.canProcess) {
            emitMessage(R.string.error_finish_setup)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processedCount = 0, saved = false) }
            val result: BatchSaveResult = when (op) {
                BatchResizeOp.DIMENSIONS -> processAndSaveImages(
                    sources = state.sources,
                    pipeline = Pipeline(listOf(state.resize)),
                    export = state.exportOptions,
                    onProgress = { done, _ -> _uiState.update { it.copy(processedCount = done) } },
                )
                BatchResizeOp.COMPRESS -> processAndSaveImages(
                    sources = state.sources,
                    pipeline = Pipeline.EMPTY,
                    export = state.exportOptions,
                    onProgress = { done, _ -> _uiState.update { it.copy(processedCount = done) } },
                )
                BatchResizeOp.FILE_SIZE -> {
                    val target = state.exportOptions.targetSizeBytes
                    if (target == null) {
                        emitMessage(R.string.error_finish_setup)
                        _uiState.update { it.copy(isProcessing = false) }
                        return@launch
                    }
                    fitImagesToSize(
                        sources = state.sources,
                        targetBytes = target,
                        format = state.exportOptions.format,
                        onProgress = { done, _ -> _uiState.update { it.copy(processedCount = done) } },
                    )
                }
            }
            emitSaved(result)
            _uiState.update { it.copy(isProcessing = false, saved = result.anySucceeded) }
        }
    }

    private fun updateResize(reduce: (ImageOp.Resize) -> ImageOp.Resize) {
        _uiState.update { it.copy(resize = reduce(it.resize), saved = false) }
    }

    private fun emitSaved(result: BatchSaveResult) {
        val message = when {
            result.requested == 1 && result.allSucceeded ->
                appContext.getString(R.string.saved_one_to_gallery)
            result.allSucceeded ->
                appContext.getString(R.string.saved_n_to_gallery, result.savedCount)
            else -> appContext.getString(
                R.string.saved_partial,
                result.savedCount,
                result.requested,
                result.errors.size,
            )
        }
        emitMessage(message)
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _effects.send(BatchResizeEffect.ShowMessage(message)) }
    }

    private fun emitMessage(resId: Int) {
        emitMessage(appContext.getString(resId))
    }
}
