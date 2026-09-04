package com.momi.watermarker.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momi.watermarker.presentation.cutout.CutoutScreen
import com.momi.watermarker.presentation.editor.EditorScreen
import com.momi.watermarker.presentation.video.VideoEditorScreen

/** The top-level flows the app offers. */
enum class AppSection(val title: String, val subtitle: String) {
    IMAGE("Bulk Image Processing", "Watermark, crop, filter, frame, and resize your photos"),
    CUTOUT("Single Image Cut-out", "Remove or replace the background of one photo"),
    VIDEO("Video Processing", "Trim, cut & join, merge, overlay, and more"),
}

/**
 * App entry point: a chooser between the image and video flows. Selecting a
 * section shows that flow; system back (or the flow's own back) returns here.
 */
@Composable
fun AppRootScreen(modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableStateOf<AppSection?>(null) }

    when (section) {
        null -> SectionChooser(
            modifier = modifier,
            onSelect = { section = it },
        )

        AppSection.IMAGE -> {
            BackHandler { section = null }
            EditorScreen(modifier = modifier)
        }

        AppSection.CUTOUT -> {
            BackHandler { section = null }
            CutoutScreen(modifier = modifier, onExit = { section = null })
        }

        AppSection.VIDEO -> VideoEditorScreen(
            modifier = modifier,
            onExit = { section = null },
        )
    }
}

@Composable
private fun SectionChooser(
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Momi Studio",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "What would you like to work on?",
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
                    Text(section.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        section.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
