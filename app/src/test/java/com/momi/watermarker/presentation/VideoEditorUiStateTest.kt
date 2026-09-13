package com.momi.watermarker.presentation

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.presentation.video.VideoEditorUiState
import com.momi.watermarker.presentation.video.VideoOp
import com.momi.watermarker.presentation.video.movedSelectionIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEditorUiStateTest {

    private val clips = listOf(
        VideoClip(
            uri = "content://a",
            metadataRotationDegrees = 90,
            rotationDegrees = 90,
        ),
        VideoClip(
            uri = "content://b",
            metadataRotationDegrees = 0,
            rotationDegrees = 90,
        ),
    )

    @Test
    fun playerShowsSelectedSourceUntilDemo() {
        val state = VideoEditorUiState(
            op = VideoOp.MERGE,
            sources = clips,
            selectedSourceIndex = 1,
        )
        assertEquals("content://b", state.playerUri)
        assertEquals(90, state.previewRotationDegrees)

        val demo = state.copy(
            resultClip = VideoClip(uri = "file://out"),
            showDemoPreview = true,
        )
        assertEquals("file://out", demo.playerUri)
        assertEquals(0, demo.previewRotationDegrees)
    }

    @Test
    fun movedSelectionFollowsTheMovedItem() {
        assertEquals(2, movedSelectionIndex(selected = 0, from = 0, to = 2, size = 3))
        assertEquals(0, movedSelectionIndex(selected = 1, from = 0, to = 2, size = 3))
        assertEquals(2, movedSelectionIndex(selected = 1, from = 2, to = 0, size = 3))
        assertEquals(1, movedSelectionIndex(selected = 1, from = 0, to = 0, size = 3))
    }
}
