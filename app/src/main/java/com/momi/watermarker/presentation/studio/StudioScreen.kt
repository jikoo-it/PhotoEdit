package com.momi.watermarker.presentation.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.momi.watermarker.R
import com.momi.watermarker.domain.model.Layer
import com.momi.watermarker.domain.model.LayerContent
import com.momi.watermarker.domain.model.LayerIds
import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.model.StudioBackdrop
import com.momi.watermarker.domain.model.containsPoint
import com.momi.watermarker.presentation.theme.extraColors
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Single-image layered Studio: cut-out, portrait look, and backdrop layers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    modifier: Modifier = Modifier,
    viewModel: StudioViewModel = hiltViewModel(),
    onExit: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var cutoutFullscreen by remember { mutableStateOf(false) }
    val liveOutline = remember { mutableStateListOf<NormalizedPoint>() }

    LaunchedEffect(uiState.isTracing) {
        if (uiState.isTracing) cutoutFullscreen = true
        else liveOutline.clear()
    }
    LaunchedEffect(uiState.inCutoutSession) {
        if (!uiState.inCutoutSession) cutoutFullscreen = false
    }

    BackHandler(enabled = cutoutFullscreen) { cutoutFullscreen = false }
    BackHandler(enabled = uiState.inCutoutSession && !cutoutFullscreen) {
        viewModel.onCancelCutout()
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StudioEvent.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val sourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onImageSelected(uri.toString()) }

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onReplacementImageSelected(uri.toString()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.studio_title)) },
                navigationIcon = {
                    TextButton(onClick = onExit) { Text(stringResource(R.string.navigate_back)) }
                },
                actions = {
                    if (uiState.hasSource) {
                        IconButton(onClick = viewModel::onUndo, enabled = uiState.canUndo) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.action_undo),
                            )
                        }
                        IconButton(onClick = viewModel::onRedo, enabled = uiState.canRedo) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = stringResource(R.string.action_redo),
                            )
                        }
                    }
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = viewModel::onSaveRequested,
                            enabled = uiState.canSave,
                        ) {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = stringResource(R.string.action_save_to_gallery),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = !uiState.inCutoutSession)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewBox(
                uiState = uiState,
                viewModel = viewModel,
                liveOutline = liveOutline,
                interactive = !cutoutFullscreen,
                onRequestFullscreen = { cutoutFullscreen = true },
            )

            OutlinedButton(
                onClick = {
                    sourcePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !uiState.inCutoutSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (uiState.hasSource) R.string.action_choose_different_photo
                        else R.string.action_choose_photo,
                    ),
                )
            }

            if (uiState.hasSource) {
                ToolsSection(uiState, viewModel)
                if (!uiState.inCutoutSession) {
                    BackdropSection(uiState, viewModel, onPickBackground = {
                        backgroundPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    })
                    LayersSection(uiState, viewModel)
                    Button(
                        onClick = viewModel::onSaveRequested,
                        enabled = uiState.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                uiState.isSaving -> stringResource(R.string.action_saving)
                                uiState.isSaved -> stringResource(R.string.action_saved_to_gallery)
                                else -> stringResource(
                                    R.string.save_to_gallery_format,
                                    stringResource(uiState.exportFormat.labelRes),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (cutoutFullscreen && uiState.inCutoutSession) {
        CutoutFullscreenDialog(
            uiState = uiState,
            viewModel = viewModel,
            liveOutline = liveOutline,
            onDismiss = { cutoutFullscreen = false },
        )
    }
}

@Composable
private fun PreviewBox(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    liveOutline: MutableList<NormalizedPoint>,
    interactive: Boolean,
    onRequestFullscreen: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .checkerboard(),
        contentAlignment = Alignment.Center,
    ) {
        ZoomableCutoutStage(
            uiState = uiState,
            viewModel = viewModel,
            liveOutline = liveOutline,
            zoomEnabled = interactive && uiState.inCutoutSession,
            interactive = interactive,
            modifier = Modifier.fillMaxSize(),
        )
        if (uiState.inCutoutSession) {
            IconButton(
                onClick = onRequestFullscreen,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Filled.Fullscreen,
                    contentDescription = stringResource(R.string.cd_studio_trace_fullscreen),
                    tint = MaterialTheme.extraColors.overlayContent,
                )
            }
        }
    }
}

@Composable
private fun CutoutFullscreenDialog(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    liveOutline: MutableList<NormalizedPoint>,
    onDismiss: () -> Unit,
) {
    val extras = MaterialTheme.extraColors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(extras.immersiveBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (uiState.isTracing) R.string.studio_cut_out_trace
                        else R.string.studio_cut_out,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = extras.immersiveOnBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_studio_exit_fullscreen),
                        tint = extras.immersiveOnBackground,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .checkerboard(),
            ) {
                ZoomableCutoutStage(
                    uiState = uiState,
                    viewModel = viewModel,
                    liveOutline = liveOutline,
                    zoomEnabled = true,
                    interactive = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                stringResource(
                    if (uiState.isTracing) R.string.studio_trace_hint
                    else R.string.studio_cutout_review_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = extras.immersiveOnBackground.copy(alpha = 0.8f),
            )
            if (uiState.isReviewingCutout) {
                Button(
                    onClick = viewModel::onConfirmCutout,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.studio_cutout_confirm))
                }
            }
            OutlinedButton(
                onClick = viewModel::onCancelCutout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun ZoomableCutoutStage(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    liveOutline: MutableList<NormalizedPoint>,
    zoomEnabled: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val extras = MaterialTheme.extraColors
    val zoom = remember { ZoomHolder() }
    LaunchedEffect(zoomEnabled) {
        if (!zoomEnabled) zoom.reset()
    }
    val imageAspect = uiState.imageAspect
    val outlineState = rememberUpdatedState(uiState.cutoutOutline)
    val handleSlop = with(LocalDensity.current) { 28.dp.toPx() }
    val pathSlop = with(LocalDensity.current) { 18.dp.toPx() }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                if (interactive && uiState.inCutoutSession) {
                    Modifier.pointerInput(zoomEnabled, imageAspect, uiState.isTracing, uiState.isReviewingCutout) {
                        var handle = -1
                        var translating = false
                        var last = Offset.Zero
                        detectDrawOrZoom(
                            onZoom = { centroid, pan, factor ->
                                if (zoomEnabled) {
                                    val box = Size(size.width.toFloat(), size.height.toFloat())
                                    val next = applyZoomPan(zoom.scale, zoom.pan, factor, pan, centroid, box)
                                    zoom.scale = next.first
                                    zoom.pan = next.second
                                }
                            },
                            onDrawStart = { visual ->
                                val box = Size(size.width.toFloat(), size.height.toFloat())
                                val content = visualToContent(visual, zoom.scale, zoom.pan, box)
                                val imageRect = fitImageRect(box, imageAspect)
                                if (uiState.isTracing) {
                                    liveOutline.clear()
                                    content.toNormalized(imageRect)?.let(liveOutline::add)
                                } else {
                                    val current = outlineState.value
                                    val pts = current.map { it.toOffset(imageRect) }
                                    val slopScale = zoom.scale.coerceAtLeast(1f)
                                    handle = nearestIndex(content, pts, handleSlop / slopScale)
                                    if (handle < 0) handle = nearestIndex(content, pts, pathSlop / slopScale)
                                    val n = content.toNormalized(imageRect)
                                    translating = handle < 0 && n != null && current.containsPoint(n.x, n.y)
                                    last = content
                                }
                            },
                            onDraw = { visual ->
                                val box = Size(size.width.toFloat(), size.height.toFloat())
                                val content = visualToContent(visual, zoom.scale, zoom.pan, box)
                                val imageRect = fitImageRect(box, imageAspect)
                                if (uiState.isTracing) {
                                    val next = content.toNormalized(imageRect)
                                    if (next != null) {
                                        val lastPoint = liveOutline.lastOrNull()
                                        if (lastPoint == null || farEnough(lastPoint, next)) liveOutline.add(next)
                                    }
                                } else {
                                    val n = content.toNormalized(imageRect)
                                    if (n != null) {
                                        if (handle >= 0) {
                                            viewModel.onOutlinePointMoved(handle, n)
                                        } else if (translating) {
                                            val prev = last.toNormalized(imageRect)
                                            if (prev != null) {
                                                viewModel.onOutlineTranslated(n.x - prev.x, n.y - prev.y)
                                            }
                                        }
                                        last = content
                                    }
                                }
                            },
                            onDrawEnd = {
                                if (uiState.isTracing) viewModel.onTraceCompleted(liveOutline.toList())
                                handle = -1
                                translating = false
                            },
                            onDrawCancel = {
                                if (uiState.isTracing) liveOutline.clear()
                                handle = -1
                                translating = false
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.pan.x
                    translationY = zoom.pan.y
                },
        ) {
            val preview = uiState.displayUri
            if (preview != null) {
                AsyncImage(
                    model = preview,
                    contentDescription = stringResource(R.string.cd_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    stringResource(R.string.studio_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (interactive && uiState.isTracing) {
                TraceOverlay(imageAspect = imageAspect, outline = liveOutline)
            } else if (interactive && uiState.isReviewingCutout) {
                ReviewOverlay(imageAspect = imageAspect, outline = uiState.cutoutOutline)
            }
        }
        if (uiState.isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(extras.overlayScrim.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = extras.overlayContent)
                    Text(
                        stringResource(
                            if (uiState.isSegmenting) R.string.cutout_finding_subject
                            else R.string.cutout_rendering,
                        ),
                        color = extras.overlayContent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsSection(uiState: StudioUiState, viewModel: StudioViewModel) {
    Text(stringResource(R.string.studio_tools), style = MaterialTheme.typography.titleMedium)
    if (uiState.isTracing) {
        Text(
            stringResource(R.string.studio_trace_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = viewModel::onCancelCutout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
        return
    }
    if (uiState.isReviewingCutout) {
        Text(
            stringResource(R.string.studio_cutout_review_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = viewModel::onConfirmCutout,
            enabled = !uiState.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.studio_cutout_confirm))
        }
        OutlinedButton(
            onClick = viewModel::onCancelCutout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
        return
    }
    Button(
        onClick = viewModel::onCutOut,
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.studio_cut_out_auto))
    }
    OutlinedButton(
        onClick = viewModel::onStartTrace,
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.studio_cut_out_trace))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.studio_portrait_look), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.studio_portrait_look_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = uiState.portraitLook,
            onCheckedChange = viewModel::onPortraitLookToggled,
            enabled = !uiState.isBusy,
        )
    }
    Text(
        stringResource(R.string.studio_background_blur),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        stringResource(R.string.studio_background_blur_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.blur_intensity, (uiState.blurStrength * 100).roundToInt()),
        style = MaterialTheme.typography.labelLarge,
    )
    Slider(
        value = uiState.blurStrength,
        onValueChange = viewModel::onBlurChanged,
        onValueChangeFinished = viewModel::onBlurCommitted,
        enabled = !uiState.isBusy,
    )
}

@Composable
private fun TraceOverlay(
    imageAspect: Float,
    outline: List<NormalizedPoint>,
) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val imageRect = fitImageRect(size, imageAspect)
        if (outline.size < 2) return@Canvas
        val path = outline.toComposePath(imageRect)
        drawPath(path, color = accent.copy(alpha = 0.22f), style = Fill)
        drawPath(
            path,
            color = accent,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawLine(
            color = accent.copy(alpha = 0.7f),
            start = outline.last().toOffset(imageRect),
            end = outline.first().toOffset(imageRect),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
    }
}

@Composable
private fun ReviewOverlay(
    imageAspect: Float,
    outline: List<NormalizedPoint>,
) {
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.extraColors.overlayScrim.copy(alpha = 0.45f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val imageRect = fitImageRect(size, imageAspect)
        if (outline.size < 2) return@Canvas
        val path = outline.toComposePath(imageRect)
        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(dim)
        }
        drawPath(path, color = accent.copy(alpha = 0.18f), style = Fill)
        drawPath(
            path,
            color = accent,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        val radius = 6.dp.toPx()
        outline.forEach { point ->
            val center = point.toOffset(imageRect)
            drawCircle(Color.White, radius = radius + 1.5f, center = center)
            drawCircle(accent, radius = radius, center = center)
        }
    }
}

@Composable
private fun BackdropSection(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    onPickBackground: () -> Unit,
) {
    HorizontalDivider()
    Text(stringResource(R.string.label_background), style = MaterialTheme.typography.titleMedium)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        BackdropChip(StudioBackdrop.ORIGINAL, R.string.studio_backdrop_original, uiState, viewModel)
        BackdropChip(
            StudioBackdrop.TRANSPARENT,
            R.string.background_mode_transparent,
            uiState,
            viewModel,
            enabled = !uiState.portraitLook,
        )
        BackdropChip(StudioBackdrop.COLOR, R.string.background_mode_color, uiState, viewModel)
        FilterChip(
            selected = uiState.backdrop == StudioBackdrop.IMAGE,
            onClick = {
                if (uiState.hasReplacement) viewModel.onBackdropSelected(StudioBackdrop.IMAGE)
                else onPickBackground()
            },
            label = { Text(stringResource(R.string.background_mode_image)) },
        )
    }
    when (uiState.backdrop) {
        StudioBackdrop.COLOR -> ColorSwatchRow(
            selectedArgb = uiState.fillColorArgb,
            onSelect = viewModel::onFillColorSelected,
        )
        StudioBackdrop.IMAGE -> OutlinedButton(
            onClick = onPickBackground,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (uiState.hasReplacement) R.string.choose_different_background
                    else R.string.studio_choose_background_image,
                ),
            )
        }
        else -> Unit
    }
}

@Composable
private fun BackdropChip(
    backdrop: StudioBackdrop,
    labelRes: Int,
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = uiState.backdrop == backdrop,
        onClick = { viewModel.onBackdropSelected(backdrop) },
        enabled = enabled,
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun LayersSection(uiState: StudioUiState, viewModel: StudioViewModel) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.studio_layers),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = viewModel::onDeleteSelectedLayer,
            enabled = uiState.canDeleteSelected,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_delete_layer),
            )
        }
    }
    Text(
        stringResource(R.string.studio_layers_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    uiState.layers.forEach { layer ->
        LayerRow(
            layer = layer,
            selected = layer.id == uiState.selectedLayerId,
            onSelect = { viewModel.onLayerSelected(layer.id) },
            onToggleVisibility = { viewModel.onToggleLayerVisibility(layer.id) },
            allLayers = uiState.document?.layers.orEmpty(),
        )
    }
}

@Composable
private fun LayerRow(
    layer: Layer,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    allLayers: List<Layer>,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleVisibility) {
            Icon(
                imageVector = if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = stringResource(R.string.cd_toggle_layer_visibility),
                tint = content,
            )
        }
        Text(
            text = layerTitle(layer, allLayers),
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun layerTitleRes(layer: Layer): Int = when {
    layer.id == LayerIds.BACKGROUND -> R.string.studio_layer_background
    LayerIds.isSubject(layer.id) -> R.string.studio_layer_subject
    layer.id == LayerIds.FILL -> R.string.studio_layer_fill
    layer.id == LayerIds.REPLACEMENT -> R.string.studio_layer_replacement
    layer.id == LayerIds.ADJUSTMENT -> {
        val grayscale = (layer.content as? LayerContent.Adjustment)?.grayscale == true
        if (grayscale) R.string.studio_layer_adjustment else R.string.studio_background_blur
    }
    else -> R.string.studio_layer_generic
}

@Composable
private fun layerTitle(layer: Layer, allLayers: List<Layer>): String {
    if (LayerIds.isSubject(layer.id)) {
        val subjects = allLayers.filter { LayerIds.isSubject(it.id) }
        if (subjects.size > 1) {
            val n = subjects.indexOfFirst { it.id == layer.id } + 1
            return stringResource(R.string.studio_layer_subject_n, n)
        }
    }
    return stringResource(layerTitleRes(layer))
}

private val FILL_COLORS = listOf(
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt(),
    0xFFF44336.toInt(),
    0xFFFFEB3B.toInt(),
    0xFF4CAF50.toInt(),
    0xFF2196F3.toInt(),
    0xFFE91E63.toInt(),
    0xFF9C27B0.toInt(),
)

@Composable
private fun ColorSwatchRow(selectedArgb: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        FILL_COLORS.forEach { argb ->
            val selected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .size(36.dp)
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

@Composable
private fun Modifier.checkerboard(cell: Float = 24f): Modifier {
    val extras = MaterialTheme.extraColors
    val light = extras.checkerLight
    val dark = extras.checkerDark
    return this
        .background(light)
        .drawBehind {
            val cols = (size.width / cell).toInt() + 1
            val rows = (size.height / cell).toInt() + 1
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if ((row + col) % 2 == 0) continue
                    drawRect(
                        color = dark,
                        topLeft = Offset(col * cell, row * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
}

private fun fitImageRect(box: Size, imageAspect: Float): Rect {
    if (box.width <= 0f || box.height <= 0f || imageAspect <= 0f) return Rect.Zero
    val boxAspect = box.width / box.height
    val (drawW, drawH) = if (imageAspect > boxAspect) {
        box.width to box.width / imageAspect
    } else {
        box.height * imageAspect to box.height
    }
    val left = (box.width - drawW) / 2f
    val top = (box.height - drawH) / 2f
    return Rect(left, top, left + drawW, top + drawH)
}

private fun List<NormalizedPoint>.toComposePath(imageRect: Rect): Path = Path().apply {
    if (isEmpty()) return@apply
    val start = first().toOffset(imageRect)
    moveTo(start.x, start.y)
    drop(1).forEach { point ->
        val o = point.toOffset(imageRect)
        lineTo(o.x, o.y)
    }
    close()
}

private fun nearestIndex(pos: Offset, pts: List<Offset>, slop: Float): Int {
    var best = -1
    var bestD = slop
    pts.forEachIndexed { i, p ->
        val d = hypot(pos.x - p.x, pos.y - p.y)
        if (d <= bestD) {
            best = i
            bestD = d
        }
    }
    return best
}

private fun Offset.toNormalized(imageRect: Rect): NormalizedPoint? {
    if (imageRect.width <= 0f || imageRect.height <= 0f) return null
    return NormalizedPoint(
        ((x - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
        ((y - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
    )
}

private fun NormalizedPoint.toOffset(imageRect: Rect): Offset =
    Offset(
        imageRect.left + x * imageRect.width,
        imageRect.top + y * imageRect.height,
    )

private fun farEnough(a: NormalizedPoint, b: NormalizedPoint): Boolean {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy >= MIN_TRACE_STEP_SQ
}

private class ZoomHolder {
    var scale by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    fun reset() {
        scale = 1f
        pan = Offset.Zero
    }
}

private suspend fun PointerInputScope.detectDrawOrZoom(
    onZoom: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onDrawStart: (Offset) -> Unit,
    onDraw: (Offset) -> Unit,
    onDrawEnd: () -> Unit,
    onDrawCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var zoomed = false
        var drawing = false
        onDrawStart(down.position)
        drawing = true
        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    if (drawing) {
                        onDrawCancel()
                        drawing = false
                    }
                    zoomed = true
                    onZoom(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
                    event.changes.forEach { it.consume() }
                } else if (!zoomed) {
                    drawing = true
                    val pos = pressed.first().position
                    onDraw(pos)
                    pressed.forEach { it.consume() }
                }
            }
            if (drawing) onDrawEnd()
        } catch (c: CancellationException) {
            if (drawing) onDrawCancel()
            throw c
        }
    }
}

private fun visualToContent(visual: Offset, scale: Float, pan: Offset, box: Size): Offset {
    val center = Offset(box.width / 2f, box.height / 2f)
    return Offset(
        (visual.x - pan.x - center.x) / scale + center.x,
        (visual.y - pan.y - center.y) / scale + center.y,
    )
}

private fun applyZoomPan(
    scale: Float,
    pan: Offset,
    zoom: Float,
    panDelta: Offset,
    centroid: Offset,
    box: Size,
): Pair<Float, Offset> {
    val newScale = (scale * zoom).coerceIn(MIN_CUTOUT_ZOOM, MAX_CUTOUT_ZOOM)
    val actualZoom = if (scale == 0f) 1f else newScale / scale
    val center = Offset(box.width / 2f, box.height / 2f)
    var newPan = Offset(
        centroid.x - (centroid.x - pan.x - center.x) * actualZoom - center.x + panDelta.x,
        centroid.y - (centroid.y - pan.y - center.y) * actualZoom - center.y + panDelta.y,
    )
    if (newScale <= 1.01f) {
        newPan = Offset.Zero
    } else {
        val maxX = (box.width * (newScale - 1f)) / 2f
        val maxY = (box.height * (newScale - 1f)) / 2f
        newPan = Offset(newPan.x.coerceIn(-maxX, maxX), newPan.y.coerceIn(-maxY, maxY))
    }
    return newScale to newPan
}

private const val MIN_TRACE_STEP_SQ = 0.000016f
private const val MIN_CUTOUT_ZOOM = 1f
private const val MAX_CUTOUT_ZOOM = 8f

