package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.usecase.MergeVideosUseCase
import com.momi.watermarker.domain.util.Outcome
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeVideosUseCaseTest {

    private val repository = mockk<VideoRepository>()
    private val useCase = MergeVideosUseCase(repository)

    @Test
    fun letterboxesToFirstClipDisplayAspectAndPassesRotation() = runTest {
        val request = slot<VideoEditRequest>()
        coEvery { repository.export(capture(request)) } returns Outcome.Success(VideoClip("out"))

        val clips = listOf(
            VideoClip(
                uri = "content://portrait",
                encodedWidth = 1920,
                encodedHeight = 1080,
                rotationDegrees = 90,
            ),
            VideoClip(
                uri = "content://landscape",
                encodedWidth = 1920,
                encodedHeight = 1080,
                rotationDegrees = 0,
            ),
        )

        val result = useCase(clips)

        assertTrue(result is Outcome.Success)
        val segments = request.captured.segments
        assertEquals(2, segments.size)
        assertEquals(90, segments[0].rotationDegrees)
        assertEquals(0, segments[1].rotationDegrees)
        assertEquals(9f / 16f, segments[0].aspectRatio!!, 0.001f)
        assertEquals(9f / 16f, segments[1].aspectRatio!!, 0.001f)
        assertTrue(segments[0].scaleToFit)
        assertTrue(segments[1].scaleToFit)
    }
}
