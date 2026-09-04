package com.momi.watermarker.presentation.cutout

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.momi.watermarker.domain.model.BackgroundMode

/**
 * Single-image "cut-out studio": extract the subject on-device, then place it
 * on a transparent, solid-color, blurred, or replacement-image background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutoutScreen(
    modifier: Modifier = Modifier,
    viewModel: CutoutViewModel = hiltViewModel(),
    onExit: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CutoutEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val sourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onImageSelected(uri.toString()) }

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onBackgroundImageSelected(uri.toString()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cut-out Studio") },
                navigationIcon = {
                    TextButton(onClick = onExit) { Text("‹ Back") }
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
            // --- Preview (checkerboard reveals transparency) -------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .checkerboard(),
                contentAlignment = Alignment.Center,
            ) {
                val preview = uiState.previewUri
                if (preview != null) {
                    AsyncImage(
                        model = preview,
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        "Pick a photo to cut out its subject.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.isBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text(
                                if (uiState.isSegmenting) "Finding the subject…" else "Rendering…",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    sourcePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (uiState.hasSource) "Choose a different photo" else "Choose a photo") }

            // --- Background controls (only once a subject exists) --------------
            if (uiState.hasCutout) {
                HorizontalDivider()
                Text("Background", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    BackgroundMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.mode == mode,
                            onClick = { viewModel.onModeSelected(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }

                when (uiState.mode) {
                    BackgroundMode.TRANSPARENT -> Text(
                        "Only the subject is kept; the background is transparent " +
                            "(saved as PNG).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    BackgroundMode.COLOR -> {
                        Text("Fill color", style = MaterialTheme.typography.bodyMedium)
                        ColorSwatchRow(
                            selectedArgb = uiState.backgroundColorArgb,
                            onSelect = viewModel::onColorSelected,
                        )
                    }

                    BackgroundMode.BLUR -> {
                        Text(
                            "Blur: ${(uiState.blurStrength * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = uiState.blurStrength,
                            onValueChange = viewModel::onBlurChanged,
                            onValueChangeFinished = viewModel::onBlurCommitted,
                            valueRange = 0f..1f,
                        )
                    }

                    BackgroundMode.IMAGE -> {
                        OutlinedButton(
                            onClick = {
                                backgroundPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (uiState.backgroundImageUri != null) "Choose a different background"
                                else "Choose background image",
                            )
                        }
                        if (uiState.backgroundImageUri != null) {
                            AsyncImage(
                                model = uiState.backgroundImageUri,
                                contentDescription = "Background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }

                // --- Save --------------------------------------------------------
                HorizontalDivider()
                Button(
                    onClick = viewModel::onSaveRequested,
                    enabled = uiState.canSave && !uiState.isSaved,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when {
                        uiState.isSaving -> {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Text("  Saving…")
                        }
                        uiState.isSaved -> Text("Saved to gallery ✓")
                        else -> Text("Save to gallery (${uiState.exportFormat.label})")
                    }
                }
            }
        }
    }
}

/** Preset background fill colors. */
private val FILL_COLORS = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFF000000.toInt(), // black
    0xFFF44336.toInt(), // red
    0xFFFFEB3B.toInt(), // yellow
    0xFF4CAF50.toInt(), // green
    0xFF2196F3.toInt(), // blue
    0xFFE91E63.toInt(), // pink
    0xFF9C27B0.toInt(), // purple
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

/** Draws a light checkerboard so transparent regions of the preview read clearly. */
private fun Modifier.checkerboard(
    cell: Float = 24f,
    light: Color = Color(0xFFECECEC),
    dark: Color = Color(0xFFCFCFCF),
): Modifier = this
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
