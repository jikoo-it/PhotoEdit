package com.momi.watermarker.presentation.video

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momi.watermarker.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.momi.watermarker.domain.model.OverlayPosition
import com.momi.watermarker.domain.model.SlideTransition
import com.momi.watermarker.domain.model.TrimRange
import com.momi.watermarker.domain.model.VideoColorFilter
import com.momi.watermarker.presentation.editor.components.DigitField
import com.momi.watermarker.presentation.editor.components.ImageCropperScreen

/**
 * Root video-editing screen. Shows an operation picker (home) and, once an
 * operation is chosen, that operation's dedicated flow. Every flow ends in the
 * same "export → save to gallery" step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoEditorViewModel = hiltViewModel(),
    onExit: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is VideoEditorEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val op = uiState.op
    // Within an op, back returns to the op-picker; at the op-picker, back
    // leaves the video flow entirely (to the app section chooser).
    BackHandler { if (op != null) viewModel.onBack() else onExit() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(op?.titleRes ?: R.string.video_home_title)) },
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
            VideoHome(
                modifier = contentModifier,
                onOpSelected = viewModel::onOpSelected,
            )
        } else {
            OperationContent(
                op = op,
                uiState = uiState,
                viewModel = viewModel,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun VideoHome(
    onOpSelected: (VideoOp) -> Unit,
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
        VideoOp.entries.forEach { op ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpSelected(op) },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(op.titleRes), style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun OperationContent(
    op: VideoOp,
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onVideoSelected(uri.toString()) }

    val videosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.onVideosSelected(uris.map { it.toString() }) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onOverlaySelected(uri.toString()) }

    val imagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.onSlidesSelected(uris.map { it.toString() }) }

    // The overlay image currently open in the full-screen cropper, if any.
    var overlayCropUri by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.isDemoPreview) {
        if (uiState.isDemoPreview) scrollState.animateScrollTo(0)
    }

    Box(modifier = modifier) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Slideshow works from images, not a source video, so it skips the
        // player until a demo result exists.
        if (op != VideoOp.SLIDESHOW || uiState.resultClip != null) {
            SourcePlayerSection(
                uiState = uiState,
                viewModel = viewModel,
                placeholder = stringResource(
                    when {
                        op == VideoOp.SLIDESHOW -> R.string.slideshow_empty_hint
                        op == VideoOp.MERGE -> R.string.pick_videos_to_merge
                        else -> R.string.choose_video_to_begin
                    },
                ),
            )

            if (op != VideoOp.SLIDESHOW) {
                val pickingEnabled = !uiState.isDemoPreview
                if (op == VideoOp.MERGE) {
                    OutlinedButton(
                        onClick = {
                            videosPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        },
                        enabled = pickingEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (uiState.hasVideo) R.string.pick_different_videos else R.string.pick_videos)) }
                } else {
                    OutlinedButton(
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        },
                        enabled = pickingEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (uiState.hasVideo) R.string.choose_different_video else R.string.choose_a_video)) }
                }
            }
        }

        val controlsEnabled = !uiState.isDemoPreview

        // --- Per-op controls --------------------------------------------------
        when (op) {
            VideoOp.CUT_JOIN -> if (uiState.isReady) {
                CutJoinControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    enabled = controlsEnabled,
                )
            }

            VideoOp.MERGE -> if (uiState.hasVideo) {
                MergeList(uiState = uiState, viewModel = viewModel, enabled = controlsEnabled)
            }

            VideoOp.REMOVE_AUDIO -> if (uiState.hasVideo) {
                Text(
                    stringResource(R.string.remove_audio_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            VideoOp.ASPECT_RATIO -> if (uiState.hasVideo) {
                AspectRatioControls(
                    selected = uiState.aspectRatio,
                    onSelect = viewModel::onAspectRatioSelected,
                    enabled = controlsEnabled,
                )
            }

            VideoOp.FILTER -> if (uiState.hasVideo) {
                FilterControls(
                    selected = uiState.colorFilter,
                    onSelect = viewModel::onColorFilterSelected,
                    enabled = controlsEnabled,
                )
            }

            VideoOp.OVERLAY -> if (uiState.hasVideo) {
                OverlayControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    enabled = controlsEnabled,
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onCropImage = { overlayCropUri = uiState.overlayUri },
                )
            }

            VideoOp.SLIDESHOW -> SlideshowControls(
                uiState = uiState,
                viewModel = viewModel,
                enabled = controlsEnabled,
                onPickImages = {
                    imagesPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }

        // Apply stays on the original; demo is saved from the player section.
        if (!uiState.isDemoPreview) {
            Button(
                onClick = viewModel::onProcessRequested,
                enabled = uiState.canExport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isExporting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.action_processing))
                } else {
                    Text(stringResource(R.string.apply_op_and_preview, stringResource(op.titleRes)))
                }
            }
        }
    }

        // Full-screen cropper for the overlay image, drawn on top when active.
        overlayCropUri?.let { uri ->
            ImageCropperScreen(
                imageUri = uri,
                title = stringResource(R.string.crop_overlay),
                onConfirm = { rect, shape ->
                    viewModel.onOverlayCropChanged(rect, shape)
                    overlayCropUri = null
                },
                onCancel = { overlayCropUri = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePlayerSection(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
    placeholder: String,
) {
    val demo = uiState.isDemoPreview
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (uiState.resultClip != null && uiState.primarySource != null) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !demo,
                    onClick = { viewModel.onDemoPreviewChanged(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    enabled = !uiState.isExporting,
                ) {
                    Text(stringResource(R.string.video_preview_original))
                }
                SegmentedButton(
                    selected = demo,
                    onClick = { viewModel.onDemoPreviewChanged(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = !uiState.isExporting,
                ) {
                    Text(stringResource(R.string.video_preview_demo))
                }
            }
        }
        Box {
            VideoPreview(
                uri = uiState.playerUri,
                rotationDegrees = uiState.previewRotationDegrees,
                autoPlay = demo,
                placeholder = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            if (uiState.isExporting) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        if (demo) {
            OutlinedTextField(
                value = uiState.outputFileName,
                onValueChange = viewModel::onOutputFileNameChanged,
                label = { Text(stringResource(R.string.save_file_name)) },
                placeholder = { Text(stringResource(R.string.save_file_name_hint)) },
                singleLine = true,
                enabled = !uiState.isSaving && !uiState.isSaved,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::onSaveRequested,
                enabled = !uiState.isSaving && !uiState.isSaved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    uiState.isSaving -> {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.action_saving))
                    }
                    uiState.isSaved -> Text(stringResource(R.string.action_saved_to_gallery))
                    else -> Text(stringResource(R.string.action_save_to_gallery))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutJoinControls(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
    enabled: Boolean = true,
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(editingIndex, uiState.keepRanges.size) {
        val index = editingIndex ?: return@LaunchedEffect
        if (index !in uiState.keepRanges.indices) editingIndex = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.exclude_sections),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.exclude_sections_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.excludeSections,
                onCheckedChange = viewModel::onExcludeSectionsChanged,
                enabled = enabled,
            )
        }
        Text(
            stringResource(
                if (uiState.excludeSections) R.string.segments_to_exclude else R.string.segments_to_keep,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(
                R.string.trim_clip_duration,
                formatMs(uiState.durationMs),
                uiState.durationMs,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.tap_segment_to_edit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uiState.keepRanges.forEachIndexed { index, range ->
            val sliderMax = uiState.durationMs.toFloat().coerceAtLeast(1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = enabled,
                        onClickLabel = stringResource(R.string.cd_edit_segment, index + 1),
                    ) { editingIndex = index },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            R.string.segment_range,
                            index + 1,
                            formatMs(range.startMs),
                            formatMs(range.endMs),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.keepRanges.size > 1) {
                        TextButton(
                            onClick = { viewModel.onRemoveKeepRange(index) },
                            enabled = enabled,
                        ) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
                Box {
                    RangeSlider(
                        value = range.startMs.toFloat()..
                            range.endMs.toFloat().coerceAtLeast(range.startMs.toFloat()),
                        onValueChange = {},
                        valueRange = 0f..sliderMax,
                        enabled = false,
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                enabled = enabled,
                                onClick = { editingIndex = index },
                            ),
                    )
                }
                HorizontalDivider()
            }
        }
        if (uiState.excludeSections && uiState.keepRanges.isNotEmpty() && uiState.resolvedKeepRanges.isEmpty()) {
            Text(
                stringResource(R.string.exclude_covers_all),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(
            onClick = viewModel::onAddKeepRange,
            enabled = enabled,
        ) { Text(stringResource(R.string.add_segment)) }
    }

    val editing = editingIndex?.let { index ->
        uiState.keepRanges.getOrNull(index)?.let { index to it }
    }
    if (editing != null) {
        val (index, range) = editing
        SegmentRangeSheet(
            index = index,
            range = range,
            durationMs = uiState.durationMs,
            showSpeed = !uiState.excludeSections,
            enabled = enabled,
            onRangeChanged = { start, end -> viewModel.onKeepRangeChanged(index, start, end) },
            onSpeedChanged = { viewModel.onKeepRangeSpeedChanged(index, it) },
            onDismiss = { editingIndex = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentRangeSheet(
    index: Int,
    range: TrimRange,
    durationMs: Long,
    showSpeed: Boolean,
    enabled: Boolean,
    onRangeChanged: (Long, Long) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sliderMax = durationMs.toFloat().coerceAtLeast(0f)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.edit_segment, index + 1),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.trim_clip_duration, formatMs(durationMs), durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.trim_time_ms_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            key(index) {
                RangeSlider(
                    value = range.startMs.toFloat()..range.endMs.toFloat().coerceAtLeast(range.startMs.toFloat()),
                    onValueChange = { r ->
                        onRangeChanged(r.start.toLong(), r.endInclusive.toLong())
                    },
                    valueRange = 0f..sliderMax.coerceAtLeast(1f),
                    enabled = enabled && durationMs > 0L,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DigitField(
                        value = range.startMs,
                        onValueChange = { onRangeChanged(it, range.endMs) },
                        label = stringResource(R.string.trim_start),
                        suffix = stringResource(R.string.unit_ms),
                        allowZero = true,
                        maxDigits = 8,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                    DigitField(
                        value = range.endMs,
                        onValueChange = { onRangeChanged(range.startMs, it) },
                        label = stringResource(R.string.trim_end),
                        suffix = stringResource(R.string.unit_ms),
                        allowZero = true,
                        maxDigits = 8,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (showSpeed) {
                Text(
                    stringResource(R.string.speed_value, range.speed),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = range.speed,
                    onValueChange = onSpeedChanged,
                    valueRange = 0.25f..4f,
                    enabled = enabled,
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeList(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.merge_videos_count, uiState.sources.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.merge_orientation_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.merge_output_frame),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            AspectRatioOption.entries.forEach { option ->
                FilterChip(
                    selected = option == uiState.mergeCanvas,
                    onClick = { viewModel.onMergeCanvasChanged(option) },
                    enabled = enabled,
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        uiState.sources.forEachIndexed { index, clip ->
            val selected = index == uiState.selectedSourceIndex
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (selected) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(8.dp),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { viewModel.onMergeClipSelected(index) }
                    .padding(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.clip_n, index + 1, index + 1),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { viewModel.onReorderSource(index, index - 1) },
                        enabled = enabled && index > 0,
                    ) { Text(stringResource(R.string.move_up)) }
                    TextButton(
                        onClick = { viewModel.onReorderSource(index, index + 1) },
                        enabled = enabled && index < uiState.sources.lastIndex,
                    ) { Text(stringResource(R.string.move_down)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.merge_clip_orientation, clip.rotationDegrees),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(
                        onClick = { viewModel.onMergeRotationChanged(index, -90) },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = stringResource(R.string.cd_rotate_left),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onMergeRotationChanged(index, 90) },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.cd_rotate_right),
                        )
                    }
                }
                Text(
                    stringResource(R.string.merge_clip_reframe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    val aspect = uiState.mergeAspects.getOrElse(index) { AspectRatioOption.ORIGINAL }
                    AspectRatioOption.entries.forEach { option ->
                        FilterChip(
                            selected = option == aspect,
                            onClick = { viewModel.onMergeAspectChanged(index, option) },
                            enabled = enabled,
                            label = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AspectRatioControls(
    selected: AspectRatioOption,
    onSelect: (AspectRatioOption) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.target_aspect_ratio), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatioOption.entries.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterControls(
    selected: VideoColorFilter,
    onSelect: (VideoColorFilter) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.color_look), style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            VideoColorFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                    enabled = enabled,
                    label = { Text(stringResource(filter.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun SlideshowControls(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
    onPickImages: () -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onPickImages,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (uiState.slides.isEmpty()) R.string.pick_images else R.string.pick_different_images))
        }
        if (uiState.slides.isEmpty()) {
            Text(
                stringResource(R.string.slideshow_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        AspectRatioControls(
            selected = uiState.slideshowAspect,
            onSelect = viewModel::onSlideshowAspectSelected,
            enabled = enabled,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.transition_length, uiState.transitionDurationMs / 1000f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.transitionDurationMs.toFloat(),
                onValueChange = { viewModel.onTransitionDurationChanged(it.toLong()) },
                valueRange = 100f..3000f,
                enabled = enabled,
            )
        }

        HorizontalDivider()

        uiState.slides.forEachIndexed { index, slide ->
            SlideRow(
                index = index,
                slide = slide,
                slideCount = uiState.slides.size,
                viewModel = viewModel,
                enabled = enabled,
            )
            if (index < uiState.slides.lastIndex) {
                TransitionRow(
                    selected = uiState.transitions.getOrElse(index) { SlideTransition.NONE },
                    onSelect = { viewModel.onSlideTransitionChanged(index, it) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun SlideRow(
    index: Int,
    slide: SlideItem,
    slideCount: Int,
    viewModel: VideoEditorViewModel,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = slide.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.slide_image_n, index + 1),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { viewModel.onReorderSlide(index, index - 1) },
                enabled = enabled && index > 0,
            ) { Text(stringResource(R.string.move_up)) }
            TextButton(
                onClick = { viewModel.onReorderSlide(index, index + 1) },
                enabled = enabled && index < slideCount - 1,
            ) { Text(stringResource(R.string.move_down)) }
            if (slideCount > 2) {
                TextButton(
                    onClick = { viewModel.onRemoveSlide(index) },
                    enabled = enabled,
                ) { Text(stringResource(R.string.action_remove)) }
            }
        }
        Text(
            stringResource(R.string.slide_shows_for, slide.durationMs / 1000f),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = slide.durationMs.toFloat(),
            onValueChange = { viewModel.onSlideDurationChanged(index, it.toLong()) },
            valueRange = 500f..10_000f,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitionRow(
    selected: SlideTransition,
    onSelect: (SlideTransition) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.transition_to_next),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            SlideTransition.entries.forEach { transition ->
                FilterChip(
                    selected = transition == selected,
                    onClick = { onSelect(transition) },
                    enabled = enabled,
                    label = { Text(stringResource(transition.labelRes)) },
                )
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayControls(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
    onPickImage: () -> Unit,
    onCropImage: () -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Image/logo vs. text overlay.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            OverlayMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.overlayMode == mode,
                    onClick = { viewModel.onOverlayModeChanged(mode) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, OverlayMode.entries.size),
                ) {
                    Text(stringResource(if (mode == OverlayMode.IMAGE) R.string.label_image else R.string.label_text))
                }
            }
        }

        when (uiState.overlayMode) {
            OverlayMode.IMAGE -> {
                OutlinedButton(
                    onClick = onPickImage,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (uiState.overlayUri != null) R.string.choose_different_image else R.string.choose_overlay_image))
                }
                if (uiState.overlayUri != null) {
                    AsyncImage(
                        model = uiState.overlayUri,
                        contentDescription = stringResource(R.string.cd_overlay_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    OutlinedButton(
                        onClick = onCropImage,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (uiState.overlayCropRect != null) R.string.adjust_crop_plain else R.string.crop_overlay_plain))
                    }
                    if (uiState.overlayCropRect != null) {
                        TextButton(
                            onClick = viewModel::onOverlayCropCleared,
                            enabled = enabled,
                        ) {
                            Text(stringResource(R.string.action_reset_crop))
                        }
                    }
                }
            }

            OverlayMode.TEXT -> {
                OutlinedTextField(
                    value = uiState.overlayText,
                    onValueChange = viewModel::onOverlayTextChanged,
                    label = { Text(stringResource(R.string.overlay_text)) },
                    minLines = 1,
                    maxLines = 3,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.label_text_color), style = MaterialTheme.typography.bodyMedium)
                OverlayColorRow(
                    selectedArgb = uiState.overlayTextColorArgb,
                    onSelect = viewModel::onOverlayTextColorChanged,
                    enabled = enabled,
                )
            }
        }

        // Shared positioning / sizing / opacity (only once there's something to show).
        val hasOverlay = when (uiState.overlayMode) {
            OverlayMode.IMAGE -> uiState.overlayUri != null
            OverlayMode.TEXT -> uiState.overlayText.isNotBlank()
        }
        if (hasOverlay) {
            HorizontalDivider()
            Text(stringResource(R.string.label_position), style = MaterialTheme.typography.bodyMedium)
            OverlayPositionGrid(
                selected = uiState.overlayPosition,
                onSelect = viewModel::onOverlayPositionChanged,
                enabled = enabled,
            )

            val sizeLabel = stringResource(if (uiState.overlayMode == OverlayMode.TEXT) R.string.label_text_size else R.string.label_size)
            Text(
                stringResource(
                    R.string.overlay_size_of_frame,
                    sizeLabel,
                    (uiState.overlaySizeFraction * 100).toInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.overlaySizeFraction,
                onValueChange = viewModel::onOverlaySizeChanged,
                // Text reads best small; images can span most of the frame.
                valueRange = if (uiState.overlayMode == OverlayMode.TEXT) 0.03f..0.25f else 0.1f..1f,
                enabled = enabled,
            )

            Text(
                stringResource(R.string.opacity_percent, (uiState.overlayAlpha * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.overlayAlpha,
                onValueChange = viewModel::onOverlayAlphaChanged,
                valueRange = 0f..1f,
                enabled = enabled,
            )
        }
    }
}

/** Preset overlay-text colors offered to the user. */
private val OVERLAY_COLORS = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFF000000.toInt(), // black
    0xFFF44336.toInt(), // red
    0xFFFFEB3B.toInt(), // yellow
    0xFF4CAF50.toInt(), // green
    0xFF2196F3.toInt(), // blue
)

/** A row of tappable color swatches; the selected one is ringed. */
@Composable
private fun OverlayColorRow(
    selectedArgb: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OVERLAY_COLORS.forEach { argb ->
            val selected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable(enabled = enabled) { onSelect(argb) },
            )
        }
    }
}

/** A 3×3 grid of anchor positions matching where the overlay lands in the frame. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayPositionGrid(
    selected: OverlayPosition,
    onSelect: (OverlayPosition) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OverlayPosition.entries.chunked(3).forEach { rowPositions ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPositions.forEach { pos ->
                    FilterChip(
                        selected = pos == selected,
                        onClick = { onSelect(pos) },
                        enabled = enabled,
                        label = {
                            Text(
                                stringResource(pos.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
