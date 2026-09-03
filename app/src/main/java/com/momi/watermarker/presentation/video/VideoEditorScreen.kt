package com.momi.watermarker.presentation.video

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.momi.watermarker.domain.model.VideoTransition

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
                title = { Text(op?.title ?: "Momi Video") },
                navigationIcon = {
                    TextButton(
                        onClick = { if (op != null) viewModel.onBack() else onExit() },
                    ) { Text("‹ Back") }
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

    val imagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.onSlidesSelected(uris.map { it.toString() }) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Slideshow works from images, not a source video, so it skips the
        // video preview + video picker and drives everything from its own list.
        if (op != VideoOp.SLIDESHOW) {
            VideoPreview(
                uri = uiState.primarySource?.uri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
                placeholder = when (op) {
                    VideoOp.MERGE -> "Pick videos to merge."
                    else -> "Choose a video to begin."
                },
            )

            // --- Source picking -----------------------------------------------
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

            VideoOp.SLIDESHOW -> SlideshowControls(
                uiState = uiState,
                viewModel = viewModel,
                onPickImages = {
                    imagesPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }

        // --- Apply / preview --------------------------------------------------
        Button(
            onClick = viewModel::onProcessRequested,
            enabled = uiState.canExport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isExporting) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text("  Processing…")
            } else {
                Text("Apply ${op.title} & preview")
            }
        }

        // --- Result: preview, then save --------------------------------------
        val result = uiState.resultClip
        if (result != null) {
            HorizontalDivider()
            Text(
                "Result — preview before saving",
                style = MaterialTheme.typography.titleMedium,
            )
            VideoPreview(
                uri = result.uri,
                autoPlay = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Button(
                onClick = viewModel::onSaveRequested,
                enabled = !uiState.isSaving && !uiState.isSaved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    uiState.isSaving -> {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text("  Saving…")
                    }
                    uiState.isSaved -> Text("Saved to gallery ✓")
                    else -> Text("Save to gallery")
                }
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
private fun SlideshowControls(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
    onPickImages: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onPickImages, modifier = Modifier.fillMaxWidth()) {
            Text(if (uiState.slides.isEmpty()) "Pick images" else "Pick different images")
        }
        if (uiState.slides.isEmpty()) {
            Text(
                "Pick two or more photos. Set how long each one shows and the " +
                    "transition played between them — every transition can differ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        AspectRatioControls(
            selected = uiState.slideshowAspect,
            onSelect = viewModel::onSlideshowAspectSelected,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Transition length: ${"%.1f".format(uiState.transitionDurationMs / 1000f)}s",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.transitionDurationMs.toFloat(),
                onValueChange = { viewModel.onTransitionDurationChanged(it.toLong()) },
                valueRange = 100f..3000f,
            )
        }

        HorizontalDivider()

        uiState.slides.forEachIndexed { index, slide ->
            SlideRow(
                index = index,
                slide = slide,
                slideCount = uiState.slides.size,
                viewModel = viewModel,
            )
            if (index < uiState.slides.lastIndex) {
                TransitionRow(
                    selected = uiState.transitions.getOrElse(index) { VideoTransition.NONE },
                    onSelect = { viewModel.onSlideTransitionChanged(index, it) },
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
                "Image ${index + 1}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { viewModel.onReorderSlide(index, index - 1) },
                enabled = index > 0,
            ) { Text("↑") }
            TextButton(
                onClick = { viewModel.onReorderSlide(index, index + 1) },
                enabled = index < slideCount - 1,
            ) { Text("↓") }
            if (slideCount > 2) {
                TextButton(onClick = { viewModel.onRemoveSlide(index) }) { Text("Remove") }
            }
        }
        Text(
            "Shows for ${"%.1f".format(slide.durationMs / 1000f)}s",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = slide.durationMs.toFloat(),
            onValueChange = { viewModel.onSlideDurationChanged(index, it.toLong()) },
            valueRange = 500f..10_000f,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitionRow(
    selected: VideoTransition,
    onSelect: (VideoTransition) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "↕ transition to next image",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            VideoTransition.entries.forEach { transition ->
                FilterChip(
                    selected = transition == selected,
                    onClick = { onSelect(transition) },
                    label = { Text(transition.label) },
                )
            }
        }
        HorizontalDivider()
    }
}

/** Human-readable label for a transition chip. */
private val VideoTransition.label: String
    get() = when (this) {
        VideoTransition.NONE -> "Cut"
        VideoTransition.FADE -> "Fade"
        VideoTransition.FLASH -> "Flash"
        VideoTransition.SLIDE -> "Slide"
        VideoTransition.ZOOM -> "Zoom"
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
