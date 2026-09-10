package com.momi.watermarker.presentation.single

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.momi.watermarker.R
import com.momi.watermarker.presentation.cutout.CutoutScreen
import com.momi.watermarker.presentation.portrait.PortraitScreen

/** The single-image tools available under this hub. */
private enum class SingleImageTool(@StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    PORTRAIT(R.string.tool_portrait_title, R.string.tool_portrait_subtitle),
    CUTOUT(R.string.tool_cutout_title, R.string.tool_cutout_subtitle),
}

/**
 * "Single Image Processing" hub: a chooser for the per-photo tools (portrait
 * selective color and cut-out). Picking one opens it; back returns here, and
 * back from the chooser leaves the hub via [onExit].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleImageScreen(
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {},
) {
    var tool by rememberSaveable { mutableStateOf<SingleImageTool?>(null) }

    when (tool) {
        null -> {
            BackHandler { onExit() }
            SingleImageHub(
                modifier = modifier,
                onExit = onExit,
                onSelect = { tool = it },
            )
        }

        SingleImageTool.PORTRAIT -> {
            BackHandler { tool = null }
            PortraitScreen(modifier = modifier, onExit = { tool = null })
        }

        SingleImageTool.CUTOUT -> {
            BackHandler { tool = null }
            CutoutScreen(modifier = modifier, onExit = { tool = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleImageHub(
    onSelect: (SingleImageTool) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_single_image_title)) },
                navigationIcon = { TextButton(onClick = onExit) { Text(stringResource(R.string.navigate_back)) } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleImageTool.entries.forEach { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry) },
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(entry.titleRes), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(entry.subtitleRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
