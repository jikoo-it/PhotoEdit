package com.momi.watermarker.presentation.batch

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.momi.watermarker.R
import com.momi.watermarker.domain.model.CompressionMode
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ResizeMode
import com.momi.watermarker.presentation.editor.components.DigitField
import com.momi.watermarker.presentation.editor.components.PercentSlider

private val MAX_DIMENSION_PRESETS = listOf(1024, 2048, 4096)

/**
 * Dedicated bulk flow: pick photos, choose resize / compress / fit-to-file-size,
 * then save every result to the gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchResizeScreen(
    modifier: Modifier = Modifier,
    viewModel: BatchResizeViewModel = hiltViewModel(),
    onExit: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BatchResizeEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val op = uiState.op
    BackHandler { if (op != null) viewModel.onBack() else onExit() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(op?.titleRes ?: R.string.section_resize_compress_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = { if (op != null) viewModel.onBack() else onExit() },
                    ) { Text(stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        if (op == null) {
            BatchResizeHome(
                modifier = contentModifier,
                onOpSelected = viewModel::onOpSelected,
            )
        } else {
            BatchResizeOpContent(
                modifier = contentModifier,
                uiState = uiState,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun BatchResizeHome(
    onOpSelected: (BatchResizeOp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.choose_an_operation),
            style = MaterialTheme.typography.titleMedium,
        )
        BatchResizeOp.entries.forEach { op ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpSelected(op) },
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(op.titleRes), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(op.subtitleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchResizeOpContent(
    uiState: BatchResizeUiState,
    viewModel: BatchResizeViewModel,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> -> viewModel.onImagesSelected(uris.map { it.toString() }) }
    val pick = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val op = uiState.op ?: return

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isProcessing) {
            Text(
                stringResource(
                    if (uiState.hasImages) R.string.pick_different_images else R.string.pick_images,
                ),
            )
        }
        if (uiState.hasImages) {
            Text(
                stringResource(R.string.batch_images_picked, uiState.imageCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when (op) {
            BatchResizeOp.DIMENSIONS -> DimensionsControls(uiState = uiState, viewModel = viewModel)
            BatchResizeOp.COMPRESS -> CompressControls(uiState = uiState, viewModel = viewModel)
            BatchResizeOp.FILE_SIZE -> FileSizeControls(uiState = uiState, viewModel = viewModel)
        }

        Button(
            onClick = viewModel::onProcessRequested,
            enabled = uiState.canProcess,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isProcessing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.action_processing))
            } else {
                Text(stringResource(R.string.batch_process_and_save, uiState.imageCount.coerceAtLeast(1)))
            }
        }
        if (uiState.isProcessing && uiState.imageCount > 0) {
            LinearProgressIndicator(
                progress = { (uiState.processedCount / uiState.imageCount.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.batch_progress, uiState.processedCount, uiState.imageCount),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (uiState.saved && !uiState.isProcessing) {
            Text(
                stringResource(R.string.action_saved_to_gallery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DimensionsControls(
    uiState: BatchResizeUiState,
    viewModel: BatchResizeViewModel,
) {
    val resize = uiState.resize
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.batch_dimensions_hint), style = MaterialTheme.typography.bodySmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ResizeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = resize.mode == mode,
                    onClick = { viewModel.onResizeModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ResizeMode.entries.size),
                ) { Text(stringResource(mode.labelRes)) }
            }
        }
        when (resize.mode) {
            ResizeMode.PERCENT -> {
                PercentSlider(
                    label = stringResource(R.string.label_scale),
                    value = resize.percent,
                    onValueChange = viewModel::onResizePercentChanged,
                    valueRange = 0.05f..4f,
                )
            }
            ResizeMode.LONGEST_SIDE -> {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MAX_DIMENSION_PRESETS.forEachIndexed { index, px ->
                        SegmentedButton(
                            selected = resize.maxDimensionPx == px,
                            onClick = { viewModel.onResizeMaxDimensionChanged(px) },
                            shape = SegmentedButtonDefaults.itemShape(index, MAX_DIMENSION_PRESETS.size),
                        ) { Text("$px") }
                    }
                }
            }
            ResizeMode.EXACT -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DigitField(
                        value = resize.widthPx.toLong(),
                        onValueChange = { viewModel.onResizeWidthChanged(it.toInt()) },
                        label = stringResource(R.string.label_width_px),
                        suffix = stringResource(R.string.suffix_px),
                        maxDigits = 4,
                        modifier = Modifier.weight(1f),
                    )
                    DigitField(
                        value = resize.heightPx.toLong(),
                        onValueChange = { viewModel.onResizeHeightChanged(it.toInt()) },
                        label = stringResource(R.string.label_height_px),
                        suffix = stringResource(R.string.suffix_px),
                        maxDigits = 4,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.keep_aspect_ratio),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = resize.lockAspectRatio,
                        onCheckedChange = viewModel::onResizeLockAspectChanged,
                    )
                }
            }
        }
        FormatRow(
            selected = uiState.exportOptions.format,
            formats = ExportFormat.entries,
            onSelect = viewModel::onExportFormatSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompressControls(
    uiState: BatchResizeUiState,
    viewModel: BatchResizeViewModel,
) {
    val export = uiState.exportOptions
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.batch_compress_hint), style = MaterialTheme.typography.bodySmall)
        FormatRow(
            selected = export.format,
            formats = ExportFormat.entries,
            onSelect = viewModel::onExportFormatSelected,
        )
        if (export.format.supportsQuality) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CompressionMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = export.mode == mode,
                        onClick = { viewModel.onCompressionModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, CompressionMode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                if (mode == CompressionMode.QUALITY) {
                                    R.string.compression_quality
                                } else {
                                    R.string.compression_target_size
                                },
                            ),
                        )
                    }
                }
            }
            when (export.mode) {
                CompressionMode.QUALITY -> {
                    Text(stringResource(R.string.label_quality_value, export.quality))
                    Slider(
                        value = export.quality.toFloat(),
                        onValueChange = { viewModel.onExportQualityChanged(it.toInt()) },
                        valueRange = 10f..100f,
                    )
                }
                CompressionMode.TARGET_SIZE -> TargetSizeFields(export = export, viewModel = viewModel)
            }
        } else {
            Text(
                stringResource(R.string.format_lossless, stringResource(export.format.labelRes)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSizeControls(
    uiState: BatchResizeUiState,
    viewModel: BatchResizeViewModel,
) {
    val export = uiState.exportOptions
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.batch_file_size_hint), style = MaterialTheme.typography.bodySmall)
        FormatRow(
            selected = export.format,
            formats = ExportFormat.entries.filter { it.supportsQuality },
            onSelect = viewModel::onExportFormatSelected,
        )
        TargetSizeFields(export = export, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSizeFields(
    export: ExportOptions,
    viewModel: BatchResizeViewModel,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ExportOptions.TARGET_SIZE_PRESETS.forEachIndexed { index, bytes ->
            SegmentedButton(
                selected = export.targetSizeBytes == bytes,
                onClick = { viewModel.onTargetSizeSelected(bytes) },
                shape = SegmentedButtonDefaults.itemShape(index, ExportOptions.TARGET_SIZE_PRESETS.size),
            ) { Text(formatKb(bytes)) }
        }
    }
    DigitField(
        value = export.targetSizeBytes?.let { ExportOptions.kbFromBytes(it) } ?: 0L,
        onValueChange = viewModel::onCustomTargetSizeKbChanged,
        label = stringResource(R.string.label_custom_size_kb),
        suffix = stringResource(R.string.suffix_kb),
        maxDigits = 5,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatRow(
    selected: ExportFormat,
    formats: List<ExportFormat>,
    onSelect: (ExportFormat) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        formats.forEachIndexed { index, format ->
            SegmentedButton(
                selected = format == selected,
                onClick = { onSelect(format) },
                shape = SegmentedButtonDefaults.itemShape(index, formats.size),
            ) { Text(stringResource(format.labelRes)) }
        }
    }
}

@Composable
private fun formatKb(bytes: Long): String = stringResource(R.string.size_kb, bytes / 1_000)
