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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.momi.watermarker.domain.model.OverlayPosition
import com.momi.watermarker.domain.model.SlideTransition
import com.momi.watermarker.domain.model.VideoColorFilter
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

    // The overlay image currently open in the full-screen cropper, if any.
    var overlayCropUri by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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

            VideoOp.FILTER -> if (uiState.hasVideo) {
                FilterControls(
                    selected = uiState.colorFilter,
                    onSelect = viewModel::onColorFilterSelected,
                )
            }

            VideoOp.OVERLAY -> if (uiState.hasVideo) {
                OverlayControls(
                    uiState = uiState,
                    viewModel = viewModel,
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

        // Full-screen cropper for the overlay image, drawn on top when active.
        overlayCropUri?.let { uri ->
            ImageCropperScreen(
                imageUri = uri,
                title = "Crop overlay",
                onConfirm = { rect, shape ->
                    viewModel.onOverlayCropChanged(rect, shape)
                    overlayCropUri = null
                },
                onCancel = { overlayCropUri = null },
            )
        }
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
                Text(
                    "Speed: ${"%.2f".format(range.speed)}×",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = range.speed,
                    onValueChange = { viewModel.onKeepRangeSpeedChanged(index, it) },
                    valueRange = 0.25f..4f,
                )
                HorizontalDivider()
            }
        }
        TextButton(onClick = viewModel::onAddKeepRange) { Text("+ Add segment") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                // Per-clip reframe: "Original" keeps this clip's own ratio.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    val selected = uiState.mergeAspects.getOrElse(index) { AspectRatioOption.ORIGINAL }
                    AspectRatioOption.entries.forEach { option ->
                        FilterChip(
                            selected = option == selected,
                            onClick = { viewModel.onMergeAspectChanged(index, option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                HorizontalDivider()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterControls(
    selected: VideoColorFilter,
    onSelect: (VideoColorFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Color look:", style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            VideoColorFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                    label = { Text(filter.label) },
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
                    selected = uiState.transitions.getOrElse(index) { SlideTransition.NONE },
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
    selected: SlideTransition,
    onSelect: (SlideTransition) -> Unit,
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
            SlideTransition.entries.forEach { transition ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayControls(
    uiState: VideoEditorUiState,
    viewModel: VideoEditorViewModel,
    onPickImage: () -> Unit,
    onCropImage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Image/logo vs. text overlay.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            OverlayMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.overlayMode == mode,
                    onClick = { viewModel.onOverlayModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, OverlayMode.entries.size),
                ) {
                    Text(if (mode == OverlayMode.IMAGE) "Image" else "Text")
                }
            }
        }

        when (uiState.overlayMode) {
            OverlayMode.IMAGE -> {
                OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                    Text(if (uiState.overlayUri != null) "Choose a different image" else "Choose overlay image")
                }
                if (uiState.overlayUri != null) {
                    AsyncImage(
                        model = uiState.overlayUri,
                        contentDescription = "Overlay image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    OutlinedButton(onClick = onCropImage, modifier = Modifier.fillMaxWidth()) {
                        Text(if (uiState.overlayCropRect != null) "Adjust crop" else "Crop overlay")
                    }
                    if (uiState.overlayCropRect != null) {
                        TextButton(onClick = viewModel::onOverlayCropCleared) {
                            Text("Reset crop")
                        }
                    }
                }
            }

            OverlayMode.TEXT -> {
                OutlinedTextField(
                    value = uiState.overlayText,
                    onValueChange = viewModel::onOverlayTextChanged,
                    label = { Text("Overlay text") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Text color", style = MaterialTheme.typography.bodyMedium)
                OverlayColorRow(
                    selectedArgb = uiState.overlayTextColorArgb,
                    onSelect = viewModel::onOverlayTextColorChanged,
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
            Text("Position", style = MaterialTheme.typography.bodyMedium)
            OverlayPositionGrid(
                selected = uiState.overlayPosition,
                onSelect = viewModel::onOverlayPositionChanged,
            )

            val sizeLabel = if (uiState.overlayMode == OverlayMode.TEXT) "Text size" else "Size"
            Text(
                "$sizeLabel: ${(uiState.overlaySizeFraction * 100).toInt()}% of frame",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.overlaySizeFraction,
                onValueChange = viewModel::onOverlaySizeChanged,
                // Text reads best small; images can span most of the frame.
                valueRange = if (uiState.overlayMode == OverlayMode.TEXT) 0.03f..0.25f else 0.1f..1f,
            )

            Text(
                "Opacity: ${(uiState.overlayAlpha * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.overlayAlpha,
                onValueChange = viewModel::onOverlayAlphaChanged,
                valueRange = 0f..1f,
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
private fun OverlayColorRow(selectedArgb: Int, onSelect: (Int) -> Unit) {
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
                    .clickable { onSelect(argb) },
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OverlayPosition.entries.chunked(3).forEach { rowPositions ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPositions.forEach { pos ->
                    FilterChip(
                        selected = pos == selected,
                        onClick = { onSelect(pos) },
                        label = {
                            Text(
                                pos.label,
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
