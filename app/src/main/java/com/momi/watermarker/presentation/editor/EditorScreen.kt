package com.momi.watermarker.presentation.editor

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.PhotoFilter
import com.momi.watermarker.domain.model.ResizeMode
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.model.WatermarkPattern
import com.momi.watermarker.domain.model.WatermarkType
import com.momi.watermarker.presentation.editor.components.ColorSwatchRow
import com.momi.watermarker.presentation.editor.components.ImageCropperScreen
import com.momi.watermarker.presentation.editor.components.OptionChipRow
import com.momi.watermarker.presentation.editor.components.PercentSlider

/** Predefined watermark colors offered to the user. */
private val PRESET_COLORS = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFF000000.toInt(), // black
    0xFFF44336.toInt(), // red
    0xFFFF9800.toInt(), // orange
    0xFFFFEB3B.toInt(), // yellow
    0xFF4CAF50.toInt(), // green
    0xFF2196F3.toInt(), // blue
    0xFF9C27B0.toInt(), // purple
    0xFFE91E63.toInt(), // pink
)

/** "Longest side" resize presets (in pixels) offered to the user. */
private val MAX_DIMENSION_PRESETS = listOf(1024, 2048, 4096)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Holds the destination the camera is currently writing into.
    var pendingCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    // Whether the save-options dialog (keep vs. delete originals) is showing.
    var showSaveDialog by remember { mutableStateOf(false) }
    // The image currently being cropped for use as a watermark, if any.
    var cropSourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    // The source image currently being cropped in the main editor, if any.
    var mainCropUri by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> -> viewModel.onImagesSelected(uris.map { it.toString() }) }

    val watermarkImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) cropSourceUri = uri.toString() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingCaptureUri?.let(viewModel::onImageCaptured)
        pendingCaptureUri = null
    }

    // System delete-consent dialog for removing the picked originals.
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onOriginalsDeleteResult(result.resultCode == Activity.RESULT_OK) }

    // One-shot effects: launch camera, request deletion, show snackbars.
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EditorEffect.LaunchCamera -> {
                    pendingCaptureUri = effect.destinationUri
                    cameraLauncher.launch(Uri.parse(effect.destinationUri))
                }
                is EditorEffect.RequestDeleteOriginals -> {
                    val request = buildDeleteRequest(context, effect.uris)
                    if (request != null) deleteLauncher.launch(request)
                    else viewModel.onOriginalsDeleteResult(false)
                }
                is EditorEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    fun startSave() {
        if (uiState.canDeleteOriginals) showSaveDialog = true
        else viewModel.onSaveRequested(deleteOriginals = false)
    }

    val pickGallery: () -> Unit = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("MomiWaterMarker") },
                    actions = {
                        if (uiState.hasImage) {
                            IconButton(onClick = viewModel::onUndo, enabled = uiState.canUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                            }
                            IconButton(onClick = viewModel::onRedo, enabled = uiState.canRedo) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                            }
                        }
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = ::startSave,
                                enabled = uiState.canSave,
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = "Save to gallery")
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PreviewArea(
                    imageUri = uiState.previewImage?.uri ?: uiState.selectedSource?.uri,
                    isRendering = uiState.isRendering,
                )

                if (uiState.hasImage) {
                    ThumbnailStrip(
                        images = uiState.sourceImages,
                        selectedIndex = uiState.selectedIndex,
                        onSelect = viewModel::onImageFocused,
                        onRemove = viewModel::onImageRemoved,
                        onAdd = pickGallery,
                    )
                    if (uiState.hasMultipleImages) {
                        Text(
                            text = "Watermark applies to all ${uiState.imageCount} images.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SourceButtons(
                    onPickGallery = pickGallery,
                    onTakePhoto = viewModel::onCaptureRequested,
                )

                if (uiState.hasImage) {
                    ToolSwitcher(
                        selected = uiState.selectedTool,
                        onSelect = viewModel::onToolSelected,
                    )

                    when (uiState.selectedTool) {
                        EditorTool.CROP -> CropControls(
                            state = uiState,
                            viewModel = viewModel,
                            onStartCrop = { mainCropUri = uiState.selectedSource?.uri },
                        )
                        EditorTool.TRANSFORM -> TransformControls(
                            state = uiState,
                            viewModel = viewModel,
                        )
                        EditorTool.RESIZE -> ResizeControls(
                            state = uiState,
                            viewModel = viewModel,
                        )
                        EditorTool.FILTER -> FilterControls(
                            state = uiState,
                            viewModel = viewModel,
                        )
                        EditorTool.ADJUST -> AdjustControls(
                            state = uiState,
                            viewModel = viewModel,
                        )
                        EditorTool.WATERMARK -> WatermarkControls(
                            state = uiState,
                            viewModel = viewModel,
                            onPickWatermarkImage = {
                                watermarkImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                        EditorTool.EXPORT -> ExportControls(
                            state = uiState,
                            viewModel = viewModel,
                        )
                    }

                    if (uiState.hasAnyEdits) {
                        TextButton(
                            onClick = viewModel::onResetAllEdits,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null)
                            Text("  Reset all edits")
                        }
                    }

                    Button(
                        onClick = ::startSave,
                        enabled = uiState.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Text(
                            if (uiState.hasMultipleImages) "  Save ${uiState.imageCount} images"
                            else "  Save to gallery"
                        )
                    }
                }
            }
        }

        // Full-screen crop overlay, shown on top of the editor while active.
        cropSourceUri?.let { uri ->
            ImageCropperScreen(
                imageUri = uri,
                onConfirm = { rect, shape ->
                    viewModel.onWatermarkImageCropped(uri, rect, shape)
                    cropSourceUri = null
                },
                onCancel = { cropSourceUri = null },
            )
        }

        // Rectangular crop of the main photo (no shape masking).
        mainCropUri?.let { uri ->
            ImageCropperScreen(
                imageUri = uri,
                title = "Crop photo",
                showShapeSelector = false,
                onConfirm = { rect, _ ->
                    viewModel.onCropChanged(rect)
                    mainCropUri = null
                },
                onCancel = { mainCropUri = null },
            )
        }
    }

    if (showSaveDialog) {
        SaveOptionsDialog(
            imageCount = uiState.imageCount,
            onKeepOriginals = {
                showSaveDialog = false
                viewModel.onSaveRequested(deleteOriginals = false)
            },
            onDeleteOriginals = {
                showSaveDialog = false
                viewModel.onSaveRequested(deleteOriginals = true)
            },
            onDismiss = { showSaveDialog = false },
        )
    }
}

@Composable
private fun PreviewArea(imageUri: String?, isRendering: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Bounded height (rather than a 1:1 square) so the source buttons and
            // controls stay on-screen on wide/landscape/tablet layouts.
            .heightIn(min = 220.dp, max = 380.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Watermark preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (isRendering) {
                CircularProgressIndicator()
            }
        } else {
            Text(
                text = "Pick images from your gallery or take a photo to begin.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun ThumbnailStrip(
    images: List<WatermarkImage>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(images, key = { _, image -> image.uri }) { index, image ->
            val selected = index == selectedIndex
            Box(modifier = Modifier.size(64.dp)) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = "Image ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(index) },
                )
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "Remove image ${index + 1}",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onRemove(index) },
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add images",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SourceButtons(onPickGallery: () -> Unit, onTakePhoto: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onPickGallery, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text("  Gallery")
        }
        OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Text("  Camera")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatermarkControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
    onPickWatermarkImage: () -> Unit,
) {
    val config = state.config

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Text vs. image watermark toggle.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WatermarkType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = config.type == type,
                    onClick = { viewModel.onWatermarkTypeSelected(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, WatermarkType.entries.size),
                ) {
                    Text(type.displayName)
                }
            }
        }

        when (config.type) {
            WatermarkType.TEXT -> {
                OutlinedTextField(
                    value = config.text,
                    onValueChange = viewModel::onTextChanged,
                    label = { Text("Watermark text") },
                    supportingText = { Text("Press Enter for a new line") },
                    minLines = 1,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                ControlLabel("Font")
                OptionChipRow(
                    options = state.availableFonts,
                    selected = config.font,
                    labelOf = { it.displayName },
                    onSelected = viewModel::onFontSelected,
                )

                ControlLabel("Color")
                ColorSwatchRow(
                    colors = PRESET_COLORS,
                    selectedArgb = config.colorArgb,
                    onSelected = viewModel::onColorSelected,
                )

                PercentSlider(
                    label = "Text size",
                    value = config.textSizeRatio,
                    onValueChange = viewModel::onTextSizeChanged,
                    valueRange = 0.02f..0.2f,
                )
            }

            WatermarkType.IMAGE -> {
                OutlinedButton(onClick = onPickWatermarkImage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Text(if (config.hasImageWatermark) "  Change watermark image" else "  Choose watermark image")
                }

                config.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Chosen watermark",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp),
                            ),
                    )
                }

                PercentSlider(
                    label = "Watermark size",
                    value = config.imageSizeRatio,
                    onValueChange = viewModel::onImageSizeChanged,
                    valueRange = 0.05f..0.8f,
                )
            }
        }

        // Shared controls (apply to both text and image watermarks).
        ControlLabel("Pattern")
        OptionChipRow(
            options = state.availablePatterns,
            selected = config.pattern,
            labelOf = { it.displayName },
            onSelected = viewModel::onPatternSelected,
        )

        // Spacing only affects the repeated (tiled/diagonal) layouts.
        if (config.pattern == WatermarkPattern.TILED || config.pattern == WatermarkPattern.DIAGONAL) {
            PercentSlider(
                label = "Item spacing",
                value = config.tileSpacingRatio,
                onValueChange = viewModel::onTileSpacingChanged,
                valueRange = 0f..3f,
            )
            PercentSlider(
                label = "Line spacing",
                value = config.lineSpacingRatio,
                onValueChange = viewModel::onLineSpacingChanged,
                valueRange = 0f..3f,
            )
        }

        PercentSlider(
            label = "Opacity",
            value = config.opacity,
            onValueChange = viewModel::onOpacityChanged,
        )
    }
}

@Composable
private fun CropControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
    onStartCrop: () -> Unit,
) {
    val cropped = !state.crop.isIdentity

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onStartCrop, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Crop, contentDescription = null)
            Text(if (cropped) "  Adjust crop" else "  Crop photo")
        }

        Text(
            text = if (cropped) {
                val r = state.crop.rect
                "Cropped to ${(r.width * 100).toInt()}% × ${(r.height * 100).toInt()}% of the original."
            } else {
                "Tap to open the cropper. The same crop is applied to every image in the batch."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (cropped) {
            TextButton(onClick = viewModel::onResetCrop) { Text("Reset crop") }
        }
    }
}

@Composable
private fun TransformControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val transform = state.transform

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlLabel("Rotate & flip")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::onRotateClockwise, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.RotateRight, contentDescription = null)
                Text("  Rotate")
            }
            OutlinedButton(onClick = viewModel::onFlipHorizontal, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Flip, contentDescription = null)
                Text("  Flip H")
            }
            OutlinedButton(onClick = viewModel::onFlipVertical, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Flip, contentDescription = null)
                Text("  Flip V")
            }
        }

        Text(
            text = buildString {
                append("Rotation ${transform.rotationDegrees}°")
                if (transform.flipHorizontal) append(" · flipped horizontally")
                if (transform.flipVertical) append(" · flipped vertically")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!transform.isIdentity) {
            TextButton(onClick = viewModel::onResetTransform) { Text("Reset transform") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResizeControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val resize = state.resize

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ResizeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = resize.mode == mode,
                    onClick = { viewModel.onResizeModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ResizeMode.entries.size),
                ) {
                    Text(mode.label)
                }
            }
        }

        when (resize.mode) {
            ResizeMode.PERCENT -> {
                PercentSlider(
                    label = "Scale",
                    value = resize.percent,
                    onValueChange = viewModel::onResizePercentChanged,
                    valueRange = 0.05f..1f,
                )
            }
            ResizeMode.LONGEST_SIDE -> {
                ControlLabel("Longest side")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MAX_DIMENSION_PRESETS.forEachIndexed { index, px ->
                        SegmentedButton(
                            selected = resize.maxDimensionPx == px,
                            onClick = { viewModel.onResizeMaxDimensionChanged(px) },
                            shape = SegmentedButtonDefaults.itemShape(index, MAX_DIMENSION_PRESETS.size),
                        ) {
                            Text("$px")
                        }
                    }
                }
                Text(
                    text = "Images larger than ${resize.maxDimensionPx}px on their longest " +
                        "side are scaled down; smaller ones are left as-is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!resize.isIdentity) {
            TextButton(onClick = viewModel::onResetResize) { Text("Reset to full size") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val export = state.exportOptions

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlLabel("Format")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ExportFormat.entries.forEachIndexed { index, format ->
                SegmentedButton(
                    selected = export.format == format,
                    onClick = { viewModel.onExportFormatSelected(format) },
                    shape = SegmentedButtonDefaults.itemShape(index, ExportFormat.entries.size),
                ) {
                    Text(format.label)
                }
            }
        }

        if (export.format.supportsQuality) {
            ControlLabel("Quality: ${export.quality}")
            Slider(
                value = export.quality.toFloat(),
                onValueChange = { viewModel.onExportQualityChanged(it.toInt()) },
                valueRange = 10f..100f,
            )
            Text(
                text = "Lower quality means smaller files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "${export.format.label} is lossless; quality doesn't apply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlLabel("Filter")
        OptionChipRow(
            options = PhotoFilter.entries,
            selected = state.filter.filter,
            labelOf = { it.label },
            onSelected = viewModel::onFilterSelected,
        )
    }
}

@Composable
private fun AdjustControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val adjust = state.adjust

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SignedSlider("Brightness", adjust.brightness, viewModel::onBrightnessChanged)
        SignedSlider("Contrast", adjust.contrast, viewModel::onContrastChanged)
        SignedSlider("Saturation", adjust.saturation, viewModel::onSaturationChanged)
        SignedSlider("Warmth", adjust.warmth, viewModel::onWarmthChanged)

        if (!adjust.isIdentity) {
            TextButton(onClick = viewModel::onResetAdjust) { Text("Reset adjustments") }
        }
    }
}

/** A slider for a bipolar `-1f..1f` adjustment, labeled with a signed percentage. */
@Composable
private fun SignedSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val percent = (value * 100).toInt()
    Column {
        ControlLabel("$label: ${if (percent > 0) "+$percent" else "$percent"}")
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -1f..1f,
        )
    }
}

/** A horizontally-scrolling row of editing tools; the selected one is highlighted. */
@Composable
private fun ToolSwitcher(
    selected: EditorTool,
    onSelect: (EditorTool) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(EditorTool.entries, key = { it.name }) { tool ->
            val isSelected = tool == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(tool) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Material icon shown for each tool in the switcher. */
private val EditorTool.icon
    get() = when (this) {
        EditorTool.CROP -> Icons.Filled.Crop
        EditorTool.TRANSFORM -> Icons.Filled.Rotate90DegreesCw
        EditorTool.RESIZE -> Icons.Filled.PhotoSizeSelectLarge
        EditorTool.FILTER -> Icons.Filled.FilterVintage
        EditorTool.ADJUST -> Icons.Filled.Tune
        EditorTool.WATERMARK -> Icons.Filled.BrandingWatermark
        EditorTool.EXPORT -> Icons.Filled.Save
    }

@Composable
private fun SaveOptionsDialog(
    imageCount: Int,
    onKeepOriginals: () -> Unit,
    onDeleteOriginals: () -> Unit,
    onDismiss: () -> Unit,
) {
    val originalWord = if (imageCount > 1) "originals" else "original"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save watermarked ${if (imageCount > 1) "images" else "image"}") },
        text = {
            Text(
                "Keep the $originalWord in your gallery, or delete " +
                    "${if (imageCount > 1) "them" else "it"} and keep only the watermarked " +
                    "${if (imageCount > 1) "copies" else "copy"}?"
            )
        },
        confirmButton = {
            TextButton(onClick = onDeleteOriginals) { Text("Delete $originalWord") }
        },
        dismissButton = {
            TextButton(onClick = onKeepOriginals) { Text("Keep $originalWord") }
        },
    )
}

@Composable
private fun ControlLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

/**
 * Builds a system delete-consent request for the picked originals.
 *
 * The photo picker hands back per-item content URIs whose ID matches the
 * MediaStore image ID for on-device photos, so we rebuild a deletable
 * `MediaStore.Images` URI from that ID. Returns null if none can be resolved
 * (e.g. non-local items), in which case nothing is deleted.
 */
private fun buildDeleteRequest(context: Context, uriStrings: List<String>): IntentSenderRequest? =
    runCatching {
        val mediaUris = uriStrings.mapNotNull(::toMediaStoreImageUri)
        if (mediaUris.isEmpty()) return null
        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaUris)
        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    }.getOrNull()

private fun toMediaStoreImageUri(uriString: String): Uri? = runCatching {
    val id = ContentUris.parseId(Uri.parse(uriString))
    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
}.getOrNull()
