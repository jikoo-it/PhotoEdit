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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momi.watermarker.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.momi.watermarker.presentation.theme.extraColors

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
                title = { Text(stringResource(R.string.tool_portrait_title)) },
                navigationIcon = {
                    TextButton(onClick = onExit) { Text(stringResource(R.string.navigate_back)) }
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
                        contentDescription = stringResource(R.string.cd_preview),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        stringResource(R.string.portrait_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.showOriginal && uiState.hasSource) {
                    val extras = MaterialTheme.extraColors
                    Text(
                        stringResource(R.string.label_original),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(extras.overlayScrim.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = extras.overlayContent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (uiState.isRendering) {
                    val extras = MaterialTheme.extraColors
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(extras.overlayScrim.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = extras.overlayContent)
                            Text(
                                stringResource(R.string.portrait_isolating),
                                color = extras.overlayContent,
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
            ) { Text(stringResource(if (uiState.hasSource) R.string.action_choose_different_photo else R.string.action_choose_photo)) }

            // --- Controls (only once a photo is loaded) ------------------------
            if (uiState.hasSource) {
                HorizontalDivider()

                ToggleRow(
                    label = stringResource(R.string.portrait_selective_color),
                    description = stringResource(R.string.portrait_selective_color_desc),
                    checked = uiState.selectiveColor,
                    onCheckedChange = viewModel::onSelectiveColorToggled,
                )

                ToggleRow(
                    label = stringResource(R.string.portrait_background_blur),
                    description = stringResource(R.string.portrait_background_blur_desc),
                    checked = uiState.backgroundBlur,
                    enabled = uiState.selectiveColor,
                    onCheckedChange = viewModel::onBackgroundBlurToggled,
                )

                if (uiState.selectiveColor && uiState.backgroundBlur) {
                    Text(
                        stringResource(R.string.blur_intensity, (uiState.blurStrength * 100).toInt()),
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
                ) { Text(stringResource(R.string.hold_to_compare)) }

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
                            Text(stringResource(R.string.action_saving))
                        }
                        uiState.isSaved -> Text(stringResource(R.string.action_saved_to_gallery))
                        else -> Text(stringResource(R.string.save_to_gallery_format, stringResource(uiState.exportFormat.labelRes)))
                    }
                }
                Text(
                    stringResource(R.string.portrait_save_hint),
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
