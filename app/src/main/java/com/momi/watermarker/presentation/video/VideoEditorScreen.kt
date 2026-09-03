package com.momi.watermarker.presentation.video

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    if (op != null) {
        BackHandler { viewModel.onBack() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(op?.title ?: "Momi Video") },
                navigationIcon = {
                    if (op != null) {
                        TextButton(onClick = viewModel::onBack) { Text("‹ Back") }
                    }
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
            text = "Choose an operation",
            style = MaterialTheme.typography.titleMedium,
        )
        VideoOp.entries.forEach { op ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpSelected(op) },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(op.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        op.subtitle,
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

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VideoPreview(
            uri = uiState.previewUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
            placeholder = when (op) {
                VideoOp.MERGE -> "Pick videos to merge."
                else -> "Choose a video to begin."
            },
        )

        // --- Source picking ---------------------------------------------------
        if (op == VideoOp.MERGE) {
            OutlinedButton(
                onClick = {
                    videosPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (uiState.hasVideo) "Pick different videos" else "Pick videos") }
        } else {
            OutlinedButton(
                onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (uiState.hasVideo) "Choose a different video" else "Choose a video") }
        }

        // --- Per-op controls --------------------------------------------------
        when (op) {
            VideoOp.TRIM -> if (uiState.isReady) {
                TrimControls(
                    startMs = uiState.trimStartMs,
                    endMs = uiState.trimEndMs,
                    durationMs = uiState.durationMs,
                    onRangeChange = viewModel::onTrimRangeChanged,
                )
            }

            VideoOp.CUT_JOIN -> if (uiState.isReady) {
                CutJoinControls(uiState = uiState, viewModel = viewModel)
            }

            VideoOp.MERGE -> if (uiState.hasVideo) {
                MergeList(uiState = uiState, viewModel = viewModel)
            }

            VideoOp.REMOVE_AUDIO -> if (uiState.hasVideo) {
                Text(
                    "The exported video will have no audio track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            VideoOp.ASPECT_RATIO -> if (uiState.hasVideo) {
                AspectRatioControls(
                    selected = uiState.aspectRatio,
                    onSelect = viewModel::onAspectRatioSelected,
                )
            }

            VideoOp.OVERLAY -> if (uiState.hasVideo) {
                OverlayControls(
                    uiState = uiState,
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onAlphaChange = viewModel::onOverlayAlphaChanged,
                )
            }
        }

        // --- Export -----------------------------------------------------------
        Button(
            onClick = viewModel::onExportRequested,
            enabled = uiState.canExport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isExporting) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text("  Exporting…")
            } else {
                Text("${op.title} & save to gallery")
            }
        }
    }
}

@Composable
private fun TrimControls(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    onRangeChange: (Long, Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Trim: ${formatMs(startMs)} – ${formatMs(endMs)} " +
                "(${formatMs(endMs - startMs)} of ${formatMs(durationMs)})",
            style = MaterialTheme.typography.bodyMedium,
        )
        RangeSlider(
            value = startMs.toFloat()..endMs.toFloat(),
            onValueChange = { range ->
                onRangeChange(range.start.toLong(), range.endInclusive.toLong())
            },
            valueRange = 0f..durationMs.toFloat(),
        )
    }
}

@Composable
private fun CutJoinControls(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Segments to keep (joined in order):",
            style = MaterialTheme.typography.bodyMedium,
        )
        uiState.keepRanges.forEachIndexed { index, range ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${index + 1}: ${formatMs(range.startMs)} – ${formatMs(range.endMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.keepRanges.size > 1) {
                        TextButton(onClick = { viewModel.onRemoveKeepRange(index) }) {
                            Text("Remove")
                        }
                    }
                }
                RangeSlider(
                    value = range.startMs.toFloat()..range.endMs.toFloat(),
                    onValueChange = { r ->
                        viewModel.onKeepRangeChanged(index, r.start.toLong(), r.endInclusive.toLong())
                    },
                    valueRange = 0f..uiState.durationMs.toFloat(),
                )
                HorizontalDivider()
            }
        }
        TextButton(onClick = viewModel::onAddKeepRange) { Text("+ Add segment") }
    }
}

@Composable
private fun MergeList(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${uiState.sources.size} videos — played top to bottom:",
            style = MaterialTheme.typography.bodyMedium,
        )
        uiState.sources.forEachIndexed { index, _ ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. Clip ${index + 1}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { viewModel.onReorderSource(index, index - 1) },
                    enabled = index > 0,
                ) { Text("↑") }
                TextButton(
                    onClick = { viewModel.onReorderSource(index, index + 1) },
                    enabled = index < uiState.sources.lastIndex,
                ) { Text("↓") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AspectRatioControls(
    selected: AspectRatioOption,
    onSelect: (AspectRatioOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Target aspect ratio:", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatioOption.entries.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun OverlayControls(
    uiState: VideoEditorUiState,
    onPickImage: () -> Unit,
    onAlphaChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Text(if (uiState.overlayUri != null) "Choose a different image" else "Choose overlay image")
        }
        if (uiState.overlayUri != null) {
            Text(
                "Opacity: ${(uiState.overlayAlpha * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.overlayAlpha,
                onValueChange = onAlphaChange,
                valueRange = 0f..1f,
            )
        }
    }
}
