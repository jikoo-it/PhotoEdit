package com.momi.watermarker.presentation

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momi.watermarker.R
import com.momi.watermarker.presentation.editor.EditorScreen
import com.momi.watermarker.presentation.settings.SettingsScreen
import com.momi.watermarker.presentation.single.SingleImageScreen
import com.momi.watermarker.presentation.video.VideoEditorScreen

/** The top-level flows the app offers. */
enum class AppSection(@StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    IMAGE(R.string.section_image_title, R.string.section_image_subtitle),
    SINGLE_IMAGE(R.string.section_single_image_title, R.string.section_single_image_subtitle),
    VIDEO(R.string.section_video_title, R.string.section_video_subtitle),
}

/**
 * App entry point: a chooser between the image and video flows. Selecting a
 * section shows that flow; system back (or the flow's own back) returns here.
 * Settings is a sibling destination from the home chooser.
 */
@Composable
fun AppRootScreen(modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableStateOf<AppSection?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    when {
        showSettings -> SettingsScreen(
            modifier = modifier,
            onExit = { showSettings = false },
        )

        section == null -> SectionChooser(
            modifier = modifier,
            onSelect = { section = it },
            onOpenSettings = { showSettings = true },
        )

        section == AppSection.IMAGE -> {
            BackHandler { section = null }
            EditorScreen(modifier = modifier)
        }

        section == AppSection.SINGLE_IMAGE -> {
            SingleImageScreen(modifier = modifier, onExit = { section = null })
        }

        section == AppSection.VIDEO -> VideoEditorScreen(
            modifier = modifier,
            onExit = { section = null },
        )
    }
}

@Composable
private fun SectionChooser(
    onSelect: (AppSection) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings_title),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.app_studio_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.home_prompt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            AppSection.entries.forEach { section ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(section) },
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(section.titleRes), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(section.subtitleRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
