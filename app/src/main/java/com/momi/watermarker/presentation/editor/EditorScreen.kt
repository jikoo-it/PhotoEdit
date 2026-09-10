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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.momi.watermarker.R
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import com.momi.watermarker.domain.model.AspectRatioPreset
import com.momi.watermarker.domain.model.CompressionMode
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.FrameStyle
import com.momi.watermarker.domain.model.ImageInfo
import com.momi.watermarker.domain.model.PhotoFilter
import com.momi.watermarker.domain.model.ResizeMode
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.model.WatermarkPattern
import com.momi.watermarker.domain.model.WatermarkType
import com.momi.watermarker.presentation.editor.components.ColorSwatchRow
import com.momi.watermarker.presentation.editor.components.ImageCropperScreen
import com.momi.watermarker.presentation.editor.components.OptionChipRow
import com.momi.watermarker.presentation.editor.components.PercentSlider
import com.momi.watermarker.presentation.editor.components.RgbColorPicker
import com.momi.watermarker.presentation.theme.extraColors

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

/** Starting color shown in the custom-tint RGB picker before the user picks one. */
private const val DEFAULT_CUSTOM_TINT = 0xFF2196F3.toInt()

/** Default fill offered when switching a shaped crop / frame away from transparent. */
private const val WHITE_ARGB = 0xFFFFFFFF.toInt()

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
    // Whether the "add images" source picker (camera/gallery) is showing.
    var showAddSheet by remember { mutableStateOf(false) }
    // The image index shown in the full-screen (swipeable) preview viewer, if any.
    var fullScreenIndex by rememberSaveable { mutableStateOf<Int?>(null) }

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
                    title = { Text(stringResource(R.string.editor_title)) },
                    actions = {
                        if (uiState.hasImage) {
                            IconButton(onClick = viewModel::onUndo, enabled = uiState.canUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
                            }
                            IconButton(onClick = viewModel::onRedo, enabled = uiState.canRedo) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.action_redo))
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
                                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.action_save_to_gallery))
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                val halfHeight = maxHeight * 0.5f
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top region — the swipeable preview pager and the tool
                    // switcher. Capped at half the screen height (when an image is
                    // loaded) so the editing form below always gets the other half.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (uiState.hasImage) Modifier.heightIn(max = halfHeight)
                                else Modifier.weight(1f)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PreviewPager(
                            images = uiState.sourceImages,
                            selectedIndex = uiState.selectedIndex,
                            previewUri = uiState.previewImage?.uri,
                            info = uiState.selectedImageInfo,
                            sizeBytes = uiState.displayedSizeBytes,
                            isRendering = uiState.isRendering,
                            onPageSettled = viewModel::onImageFocused,
                            onOpenFullScreen = { index -> fullScreenIndex = index },
                            onAdd = { showAddSheet = true },
                            onRemove = viewModel::onImageRemoved,
                            modifier = Modifier.weight(1f),
                        )
                        if (uiState.hasMultipleImages) {
                            Text(
                                text = stringResource(R.string.edits_apply_to_all, uiState.imageCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (uiState.hasImage) {
                            ToolSwitcher(
                                tools = uiState.visibleTools,
                                selected = uiState.selectedTool,
                                onSelect = viewModel::onToolSelected,
                            )
                        }
                    }

                    if (uiState.hasImage) {
                        // Only the editing form scrolls; it fills the lower half.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
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
                            EditorTool.ASPECT -> AspectControls(
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
                            EditorTool.PIXELATE -> PixelateControls(
                                state = uiState,
                                viewModel = viewModel,
                            )
                            EditorTool.FRAME -> FrameControls(
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
                                Text(stringResource(R.string.action_reset_all_edits))
                            }
                        }

                        Button(
                            onClick = ::startSave,
                            enabled = uiState.canSave,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Text(
                                if (uiState.hasMultipleImages) stringResource(R.string.action_save_n_images, uiState.imageCount)
                                else stringResource(R.string.action_save_to_gallery_spaced)
                            )
                        }
                        }
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

        // Crop of the main photo, in any of the supported shapes.
        mainCropUri?.let { uri ->
            ImageCropperScreen(
                imageUri = uri,
                title = stringResource(R.string.crop_photo),
                showShapeSelector = true,
                onConfirm = { rect, shape ->
                    viewModel.onCropChanged(rect, shape)
                    mainCropUri = null
                },
                onCancel = { mainCropUri = null },
            )
        }

        // Tap-to-zoom, swipeable full-screen preview of the images.
        fullScreenIndex?.let { index ->
            if (uiState.sourceImages.isNotEmpty()) {
                FullScreenPager(
                    images = uiState.sourceImages,
                    initialIndex = index.coerceIn(0, uiState.sourceImages.lastIndex),
                    onDismiss = { fullScreenIndex = null },
                )
            }
        }
    }

    if (showAddSheet) {
        AddSourceSheet(
            onPickGallery = {
                showAddSheet = false
                pickGallery()
            },
            onTakePhoto = {
                showAddSheet = false
                viewModel.onCaptureRequested()
            },
            onDismiss = { showAddSheet = false },
        )
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

/**
 * The large preview area as a swipeable pager: one page per source image plus a
 * trailing "+" page to add more. Swiping to an image page focuses it (driving a
 * re-render of the live preview for that image); the trailing page adds images.
 */
@Composable
private fun PreviewPager(
    images: List<WatermarkImage>,
    selectedIndex: Int,
    previewUri: String?,
    info: ImageInfo?,
    sizeBytes: Long?,
    isRendering: Boolean,
    onPageSettled: (Int) -> Unit,
    onOpenFullScreen: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One page per image, plus a trailing add page (so there's always a "+").
    val pageCount = images.size + 1
    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount },
    )

    // Focus the settled image page so its live preview re-renders. The trailing
    // add page doesn't change the selection.
    LaunchedEffect(pagerState, images.size) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page < images.size) onPageSettled(page)
        }
    }
    // Keep the pager aligned when the selection changes elsewhere (e.g. removal).
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < pageCount && selectedIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
    ) { page ->
        if (page < images.size) {
            val isCurrent = page == pagerState.currentPage
            // The rendered preview exists only for the focused image; other pages
            // show their raw source.
            val shownUri = if (isCurrent && previewUri != null) previewUri else images[page].uri
            PreviewPage(
                imageUri = shownUri,
                info = if (isCurrent) info else null,
                sizeBytes = if (isCurrent) sizeBytes else null,
                isRendering = isCurrent && isRendering,
                onOpenFullScreen = { onOpenFullScreen(page) },
                onRemove = { onRemove(page) },
            )
        } else {
            AddPage(onAdd = onAdd, hasImages = images.isNotEmpty())
        }
    }
}

/** A single image page: fit-scaled photo, size badge, and a remove button. */
@Composable
private fun PreviewPage(
    imageUri: String,
    info: ImageInfo?,
    sizeBytes: Long?,
    isRendering: Boolean,
    onOpenFullScreen: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onOpenFullScreen),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = stringResource(R.string.cd_preview_tap_fullscreen),
            // Fit shows the whole image without cropping it.
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (isRendering) {
            CircularProgressIndicator()
        }
        // Remove-this-image button.
        Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = stringResource(R.string.cd_remove_image),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .clickable(onClick = onRemove)
                .padding(4.dp),
        )
        // Dimensions / file-size badge in the corner.
        info?.let {
            val extras = MaterialTheme.extraColors
            Text(
                text = formatImageInfo(it, sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = extras.overlayContent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(extras.overlayScrim.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** The trailing pager page: a big "+" that opens the add-source sheet. */
@Composable
private fun AddPage(onAdd: () -> Unit, hasImages: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_add_images),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(if (hasImages) R.string.add_more_images else R.string.add_images_to_begin),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "1024 × 768 · 245 KB" — dimensions from [info] and (when known) the [sizeBytes] file size. */
@Composable
private fun formatImageInfo(info: ImageInfo, sizeBytes: Long?): String {
    return if (sizeBytes != null) {
        stringResource(R.string.image_info_with_size, info.width, info.height, formatBytes(sizeBytes))
    } else {
        stringResource(R.string.image_info_dimensions, info.width, info.height)
    }
}

@Composable
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> stringResource(R.string.size_mb, bytes / 1_000_000.0)
    bytes >= 1_000 -> stringResource(R.string.size_kb, bytes / 1_000)
    else -> stringResource(R.string.size_bytes, bytes)
}

/**
 * Full-screen, dark, swipeable viewer over all [images], opening at
 * [initialIndex]. Swipe left/right to change image; the close button dismisses.
 */
@Composable
private fun FullScreenPager(
    images: List<WatermarkImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size },
    )
    val extras = MaterialTheme.extraColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(extras.immersiveBackground),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = images[page].uri,
                contentDescription = stringResource(R.string.cd_fullscreen_preview, page + 1),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (images.size > 1) {
            Text(
                text = stringResource(R.string.pager_position, pagerState.currentPage + 1, images.size),
                style = MaterialTheme.typography.labelLarge,
                color = extras.overlayContent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(extras.overlayScrim.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = extras.immersiveOnBackground,
            )
        }
    }
}

/** Bottom sheet offering the two ways to add images: camera or gallery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceSheet(
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.take_a_photo)) },
                leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onTakePhoto),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.choose_from_gallery)) },
                leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onPickGallery),
            )
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
                    Text(stringResource(type.labelRes))
                }
            }
        }

        when (config.type) {
            WatermarkType.TEXT -> {
                OutlinedTextField(
                    value = config.text,
                    onValueChange = viewModel::onTextChanged,
                    label = { Text(stringResource(R.string.watermark_text)) },
                    supportingText = { Text(stringResource(R.string.watermark_text_hint)) },
                    minLines = 1,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                ControlLabel(stringResource(R.string.label_font))
                OptionChipRow(
                    options = state.availableFonts,
                    selected = config.font,
                    labelOf = { stringResource(it.labelRes) },
                    onSelected = viewModel::onFontSelected,
                )

                ControlLabel(stringResource(R.string.label_color))
                ColorSwatchRow(
                    colors = PRESET_COLORS,
                    selectedArgb = config.colorArgb,
                    onSelected = viewModel::onColorSelected,
                )

                PercentSlider(
                    label = stringResource(R.string.label_text_size),
                    value = config.textSizeRatio,
                    onValueChange = viewModel::onTextSizeChanged,
                    valueRange = 0.02f..0.2f,
                )
            }

            WatermarkType.IMAGE -> {
                OutlinedButton(onClick = onPickWatermarkImage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Text(stringResource(if (config.hasImageWatermark) R.string.change_watermark_image else R.string.choose_watermark_image))
                }

                config.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = stringResource(R.string.cd_chosen_watermark),
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
                    label = stringResource(R.string.label_watermark_size),
                    value = config.imageSizeRatio,
                    onValueChange = viewModel::onImageSizeChanged,
                    valueRange = 0.05f..0.8f,
                )
            }
        }

        // Shared controls (apply to both text and image watermarks).
        ControlLabel(stringResource(R.string.label_pattern))
        OptionChipRow(
            options = state.availablePatterns,
            selected = config.pattern,
            labelOf = { stringResource(it.labelRes) },
            onSelected = viewModel::onPatternSelected,
        )

        // Spacing only affects the repeated (tiled/diagonal) layouts.
        if (config.pattern == WatermarkPattern.TILED || config.pattern == WatermarkPattern.DIAGONAL) {
            PercentSlider(
                label = stringResource(R.string.label_item_spacing),
                value = config.tileSpacingRatio,
                onValueChange = viewModel::onTileSpacingChanged,
                valueRange = 0f..3f,
            )
            PercentSlider(
                label = stringResource(R.string.label_line_spacing),
                value = config.lineSpacingRatio,
                onValueChange = viewModel::onLineSpacingChanged,
                valueRange = 0f..3f,
            )
        }

        PercentSlider(
            label = stringResource(R.string.label_opacity),
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
            Text(stringResource(if (cropped) R.string.adjust_crop else R.string.crop_photo_button))
        }

        Text(
            text = if (cropped) {
                val r = state.crop.rect
                val w = (r.width * 100).toInt()
                val h = (r.height * 100).toInt()
                if (state.crop.shape != CropShape.RECTANGLE) {
                    stringResource(
                        R.string.crop_applied_shaped,
                        w,
                        h,
                        stringResource(state.crop.shape.labelRes),
                    )
                } else {
                    stringResource(R.string.crop_applied, w, h)
                }
            } else {
                stringResource(R.string.crop_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // For a shaped (non-rectangular) crop the exported file is still
        // rectangular, so let the user pick whether the masked area is left
        // transparent (a cut-out) or filled with a solid color.
        if (state.crop.shape != CropShape.RECTANGLE) {
            ControlLabel(stringResource(R.string.label_masked_area))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.crop.backgroundArgb == null,
                    onClick = { viewModel.onCropBackgroundChanged(null) },
                    label = { Text(stringResource(R.string.label_transparent)) },
                )
                FilterChip(
                    selected = state.crop.backgroundArgb != null,
                    onClick = {
                        viewModel.onCropBackgroundChanged(state.crop.backgroundArgb ?: WHITE_ARGB)
                    },
                    label = { Text(stringResource(R.string.label_fill_color)) },
                )
            }
            state.crop.backgroundArgb?.let { bg ->
                ColorSwatchRow(
                    colors = PRESET_COLORS,
                    selectedArgb = bg,
                    onSelected = { viewModel.onCropBackgroundChanged(it) },
                )
            }
        }

        if (cropped) {
            TextButton(onClick = viewModel::onResetCrop) { Text(stringResource(R.string.action_reset_crop)) }
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
        ControlLabel(stringResource(R.string.label_rotate_flip))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::onRotateClockwise, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.RotateRight, contentDescription = null)
                Text(stringResource(R.string.action_rotate))
            }
            OutlinedButton(onClick = viewModel::onFlipHorizontal, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Flip, contentDescription = null)
                Text(stringResource(R.string.action_flip_h))
            }
            OutlinedButton(onClick = viewModel::onFlipVertical, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Flip, contentDescription = null)
                Text(stringResource(R.string.action_flip_v))
            }
        }

        Text(
            text = buildString {
                append(stringResource(R.string.transform_rotation, transform.rotationDegrees))
                if (transform.flipHorizontal) append(stringResource(R.string.transform_flipped_h))
                if (transform.flipVertical) append(stringResource(R.string.transform_flipped_v))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!transform.isIdentity) {
            TextButton(onClick = viewModel::onResetTransform) { Text(stringResource(R.string.action_reset_transform)) }
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
                    Text(stringResource(mode.labelRes))
                }
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
                Text(
                    text = stringResource(R.string.resize_percent_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ResizeMode.LONGEST_SIDE -> {
                ControlLabel(stringResource(R.string.label_longest_side))
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
                    text = stringResource(R.string.resize_max_hint, resize.maxDimensionPx),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!resize.isIdentity) {
            TextButton(onClick = viewModel::onResetResize) { Text(stringResource(R.string.action_reset_to_full_size)) }
        }
    }
}

@Composable
private fun AspectControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val aspectPad = state.aspectPad
    val padded = !aspectPad.isIdentity

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlLabel(stringResource(R.string.label_aspect_ratio))
        OptionChipRow(
            options = AspectRatioPreset.entries,
            selected = aspectPad.preset,
            labelOf = { stringResource(it.labelRes) },
            onSelected = viewModel::onAspectPresetSelected,
        )

        Text(
            text = stringResource(R.string.aspect_pad_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The added bars can be transparent (a cut-out on export) or a solid fill.
        if (padded) {
            ControlLabel(stringResource(R.string.label_bars))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = aspectPad.fillArgb == null,
                    onClick = { viewModel.onAspectFillChanged(null) },
                    label = { Text(stringResource(R.string.label_transparent)) },
                )
                FilterChip(
                    selected = aspectPad.fillArgb != null,
                    onClick = {
                        viewModel.onAspectFillChanged(aspectPad.fillArgb ?: WHITE_ARGB)
                    },
                    label = { Text(stringResource(R.string.label_fill_color)) },
                )
            }
            aspectPad.fillArgb?.let { fill ->
                ColorSwatchRow(
                    colors = PRESET_COLORS,
                    selectedArgb = fill,
                    onSelected = { viewModel.onAspectFillChanged(it) },
                )
            }

            TextButton(onClick = viewModel::onResetAspect) { Text(stringResource(R.string.action_reset_aspect)) }
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
        ControlLabel(stringResource(R.string.label_format))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ExportFormat.entries.forEachIndexed { index, format ->
                SegmentedButton(
                    selected = export.format == format,
                    onClick = { viewModel.onExportFormatSelected(format) },
                    shape = SegmentedButtonDefaults.itemShape(index, ExportFormat.entries.size),
                ) {
                    Text(stringResource(format.labelRes))
                }
            }
        }

        if (export.format.supportsQuality) {
            ControlLabel(stringResource(R.string.label_compression))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CompressionMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = export.mode == mode,
                        onClick = { viewModel.onCompressionModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, CompressionMode.entries.size),
                    ) {
                        Text(stringResource(if (mode == CompressionMode.QUALITY) R.string.compression_quality else R.string.compression_target_size))
                    }
                }
            }

            when (export.mode) {
                CompressionMode.QUALITY -> {
                    ControlLabel(stringResource(R.string.label_quality_value, export.quality))
                    Slider(
                        value = export.quality.toFloat(),
                        onValueChange = { viewModel.onExportQualityChanged(it.toInt()) },
                        valueRange = 10f..100f,
                    )
                    Text(
                        text = stringResource(R.string.quality_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CompressionMode.TARGET_SIZE -> {
                    ControlLabel(stringResource(R.string.label_target_size))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ExportOptions.TARGET_SIZE_PRESETS.forEachIndexed { index, bytes ->
                            SegmentedButton(
                                selected = export.targetSizeBytes == bytes,
                                onClick = { viewModel.onTargetSizeSelected(bytes) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index,
                                    ExportOptions.TARGET_SIZE_PRESETS.size,
                                ),
                            ) {
                                Text(formatBytes(bytes))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.target_size_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.format_lossless, stringResource(export.format.labelRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.estimatedExportSize?.let { estimate ->
            Text(
                text = stringResource(R.string.estimated_export_size, formatBytes(estimate)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun FilterControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val filter = state.filter

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlLabel(stringResource(R.string.label_filter))
        // Presets plus a "Custom" pill in one scrollable row; exactly one is
        // selected at a time (a preset, or the custom color tint).
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhotoFilter.entries.forEach { preset ->
                FilterChip(
                    selected = !filter.hasCustomTint && filter.filter == preset,
                    onClick = { viewModel.onFilterSelected(preset) },
                    label = { Text(stringResource(preset.labelRes)) },
                )
            }
            FilterChip(
                selected = filter.hasCustomTint,
                onClick = {
                    viewModel.onCustomTintChanged(filter.customTintArgb ?: DEFAULT_CUSTOM_TINT)
                },
                label = { Text(stringResource(R.string.filter_custom)) },
            )
        }

        // The RGB sliders appear only once the custom pill is selected.
        if (filter.hasCustomTint) {
            Text(
                text = stringResource(R.string.custom_tint_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RgbColorPicker(
                colorArgb = filter.customTintArgb ?: DEFAULT_CUSTOM_TINT,
                onColorChanged = viewModel::onCustomTintChanged,
            )
            TextButton(onClick = { viewModel.onFilterSelected(PhotoFilter.NONE) }) {
                Text(stringResource(R.string.remove_color_tint))
            }
        }
    }
}

@Composable
private fun AdjustControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val adjust = state.adjust

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SignedSlider(stringResource(R.string.adjust_brightness), adjust.brightness, viewModel::onBrightnessChanged)
        SignedSlider(stringResource(R.string.adjust_contrast), adjust.contrast, viewModel::onContrastChanged)
        SignedSlider(stringResource(R.string.adjust_saturation), adjust.saturation, viewModel::onSaturationChanged)
        SignedSlider(stringResource(R.string.adjust_warmth), adjust.warmth, viewModel::onWarmthChanged)

        if (!adjust.isIdentity) {
            TextButton(onClick = viewModel::onResetAdjust) { Text(stringResource(R.string.action_reset_adjustments)) }
        }
    }
}

@Composable
private fun PixelateControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val block = state.pixelate.blockSizePx

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlLabel(
            if (state.pixelate.isIdentity) stringResource(R.string.pixelate_off)
            else stringResource(R.string.pixelate_block_size, block)
        )
        Slider(
            value = block.toFloat(),
            onValueChange = { viewModel.onPixelateBlockChanged(it.toInt()) },
            valueRange = 1f..64f,
        )
        Text(
            text = stringResource(R.string.pixelate_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!state.pixelate.isIdentity) {
            TextButton(onClick = viewModel::onResetPixelate) { Text(stringResource(R.string.action_turn_off_pixelate)) }
        }
    }
}

@Composable
private fun FrameControls(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val frame = state.frame

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlLabel(stringResource(R.string.label_style))
        OptionChipRow(
            options = FrameStyle.entries,
            selected = frame.style,
            labelOf = { stringResource(it.labelRes) },
            onSelected = viewModel::onFrameStyleSelected,
        )

        if (frame.style != FrameStyle.NONE) {
            PercentSlider(
                label = stringResource(if (frame.style == FrameStyle.SHADOW) R.string.label_shadow_margin else R.string.label_frame_width),
                value = frame.widthRatio,
                onValueChange = viewModel::onFrameWidthChanged,
                valueRange = 0.01f..0.25f,
            )

            // Rounded frames always reveal the transparent background; the other
            // styles let the user choose a fill color or make the area outside
            // the photo see-through.
            if (frame.style != FrameStyle.ROUNDED) {
                ControlLabel(stringResource(R.string.label_background))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !frame.transparentBackground,
                        onClick = { viewModel.onFrameTransparentChanged(false) },
                        label = { Text(stringResource(R.string.label_fill_color)) },
                    )
                    FilterChip(
                        selected = frame.transparentBackground,
                        onClick = { viewModel.onFrameTransparentChanged(true) },
                        label = { Text(stringResource(R.string.label_transparent)) },
                    )
                }
                if (!frame.transparentBackground) {
                    ColorSwatchRow(
                        colors = PRESET_COLORS,
                        selectedArgb = frame.colorArgb,
                        onSelected = viewModel::onFrameColorSelected,
                    )
                }
            }

            if (frame.style == FrameStyle.ROUNDED) {
                PercentSlider(
                    label = stringResource(R.string.label_corner_radius),
                    value = frame.cornerRadiusRatio,
                    onValueChange = viewModel::onFrameCornerRadiusChanged,
                    valueRange = 0f..0.5f,
                )
            }

            TextButton(onClick = viewModel::onResetFrame) { Text(stringResource(R.string.action_remove_frame)) }
        } else {
            Text(
                text = stringResource(R.string.frame_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        ControlLabel(stringResource(R.string.signed_percent_label, label, if (percent > 0) stringResource(R.string.signed_percent_positive, percent) else stringResource(R.string.signed_percent_value, percent)))
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
    tools: List<EditorTool>,
    selected: EditorTool,
    onSelect: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tools, key = { it.name }) { tool ->
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
                    text = stringResource(tool.labelRes),
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
        EditorTool.ASPECT -> Icons.Filled.AspectRatio
        EditorTool.FILTER -> Icons.Filled.FilterVintage
        EditorTool.ADJUST -> Icons.Filled.Tune
        EditorTool.PIXELATE -> Icons.Filled.GridOn
        EditorTool.FRAME -> Icons.Filled.BorderOuter
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.save_watermarked_title, imageCount, imageCount)) },
        text = {
            Text(pluralStringResource(R.plurals.save_options_message, imageCount, imageCount))
        },
        confirmButton = {
            TextButton(onClick = onDeleteOriginals) {
                Text(pluralStringResource(R.plurals.delete_originals, imageCount, imageCount))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepOriginals) {
                Text(pluralStringResource(R.plurals.keep_originals, imageCount, imageCount))
            }
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
