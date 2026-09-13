package com.momi.watermarker.presentation.batch

import androidx.annotation.StringRes
import com.momi.watermarker.R
import com.momi.watermarker.domain.model.CompressionMode
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.WatermarkImage

/** The three dedicated bulk jobs offered on this flow. */
enum class BatchResizeOp(@StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    DIMENSIONS(R.string.batch_op_dimensions, R.string.batch_op_dimensions_subtitle),
    COMPRESS(R.string.batch_op_compress, R.string.batch_op_compress_subtitle),
    FILE_SIZE(R.string.batch_op_file_size, R.string.batch_op_file_size_subtitle),
}

data class BatchResizeUiState(
    val op: BatchResizeOp? = null,
    val sources: List<WatermarkImage> = emptyList(),
    val resize: ImageOp.Resize = ImageOp.Resize(),
    val exportOptions: ExportOptions = ExportOptions(),
    val isProcessing: Boolean = false,
    val processedCount: Int = 0,
    val saved: Boolean = false,
) {
    val hasImages: Boolean get() = sources.isNotEmpty()
    val imageCount: Int get() = sources.size

    val canProcess: Boolean
        get() = hasImages && !isProcessing && when (op) {
            BatchResizeOp.DIMENSIONS -> !resize.isIdentity
            BatchResizeOp.COMPRESS -> true
            BatchResizeOp.FILE_SIZE ->
                exportOptions.targetSizeBytes != null &&
                    exportOptions.format.supportsQuality
            null -> false
        }
}

sealed interface BatchResizeEffect {
    data class ShowMessage(val message: String) : BatchResizeEffect
}

fun defaultExportFor(op: BatchResizeOp): ExportOptions = when (op) {
    BatchResizeOp.DIMENSIONS -> ExportOptions()
    BatchResizeOp.COMPRESS -> ExportOptions(mode = CompressionMode.QUALITY)
    BatchResizeOp.FILE_SIZE -> ExportOptions(
        format = ExportFormat.JPEG,
        mode = CompressionMode.TARGET_SIZE,
        targetSizeBytes = 200_000L,
    )
}
