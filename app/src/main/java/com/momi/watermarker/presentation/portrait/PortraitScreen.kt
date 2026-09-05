package com.momi.watermarker.presentation.portrait

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

/**
 * Portrait "selective color + background blur" tool: the detected person(s) are
 * kept in color while the background is desaturated (and optionally blurred).
 * The person is isolated with an on-device segmentation mask — no manual masking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortraitScreen(
    modifier: Modifier = Modifier,
    viewModel: PortraitViewModel = hiltViewModel(),
    onExit: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PortraitEvent.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val sourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onImageSelected(uri.toString()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Portrait Color") },
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
            // --- Preview -------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        "Pick a portrait to keep the person in color.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.showOriginal && uiState.hasSource) {
                    Text(
                        "Original",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (uiState.isRendering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text(
                                "Isolating the person…",
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

            // --- Controls (only once a photo is loaded) ------------------------
            if (uiState.hasSource) {
                HorizontalDivider()

                ToggleRow(
                    label = "Selective color",
                    description = "Keep the person in color, background grayscale",
                    checked = uiState.selectiveColor,
                    onCheckedChange = viewModel::onSelectiveColorToggled,
                )

                ToggleRow(
                    label = "Background blur",
                    description = "Also Gaussian-blur the background",
                    checked = uiState.backgroundBlur,
                    enabled = uiState.selectiveColor,
                    onCheckedChange = viewModel::onBackgroundBlurToggled,
                )

                if (uiState.selectiveColor && uiState.backgroundBlur) {
                    Text(
                        "Blur intensity: ${(uiState.blurStrength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = uiState.blurStrength,
                        onValueChange = viewModel::onBlurChanged,
                        onValueChangeFinished = viewModel::onBlurCommitted,
                        valueRange = 0f..1f,
                    )
                }

                // --- Before/after compare --------------------------------------
                OutlinedButton(
                    onClick = {},
                    enabled = uiState.resultUri != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(uiState.resultUri) {
                            if (uiState.resultUri == null) return@pointerInput
                            detectTapGestures(
                                onPress = {
                                    viewModel.onShowOriginalChanged(true)
                                    tryAwaitRelease()
                                    viewModel.onShowOriginalChanged(false)
                                },
                            )
                        },
                ) { Text("Hold to compare with original") }

                // --- Save ------------------------------------------------------
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
                Text(
                    "Saved at full resolution. Works on any portrait — including " +
                        "multiple people — using on-device person segmentation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
