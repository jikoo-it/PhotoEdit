package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.TrimRange
import com.momi.watermarker.domain.model.complementWithin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimRangeTest {

    @Test
    fun `empty excludes keep the whole duration`() {
        val keep = emptyList<TrimRange>().complementWithin(10_000L)
        assertEquals(listOf(TrimRange(0L, 10_000L)), keep)
    }

    @Test
    fun `middle exclude leaves a prefix and a suffix`() {
        val keep = listOf(TrimRange(2_000L, 5_000L)).complementWithin(10_000L)
        assertEquals(
            listOf(TrimRange(0L, 2_000L), TrimRange(5_000L, 10_000L)),
            keep,
        )
    }

    @Test
    fun `exclude from the start leaves a suffix`() {
        val keep = listOf(TrimRange(0L, 3_000L)).complementWithin(10_000L)
        assertEquals(listOf(TrimRange(3_000L, 10_000L)), keep)
    }

    @Test
    fun `exclude to the end leaves a prefix`() {
        val keep = listOf(TrimRange(7_000L, 10_000L)).complementWithin(10_000L)
        assertEquals(listOf(TrimRange(0L, 7_000L)), keep)
    }

    @Test
    fun `overlapping excludes are merged`() {
        val keep = listOf(
            TrimRange(1_000L, 4_000L),
            TrimRange(3_000L, 6_000L),
        ).complementWithin(10_000L)
        assertEquals(
            listOf(TrimRange(0L, 1_000L), TrimRange(6_000L, 10_000L)),
            keep,
        )
    }

    @Test
    fun `touching excludes leave no gap`() {
        val keep = listOf(
            TrimRange(0L, 4_000L),
            TrimRange(4_000L, 7_000L),
        ).complementWithin(10_000L)
        assertEquals(listOf(TrimRange(7_000L, 10_000L)), keep)
    }

    @Test
    fun `full-span exclude leaves nothing`() {
        assertTrue(listOf(TrimRange(0L, 10_000L)).complementWithin(10_000L).isEmpty())
    }

    @Test
    fun `out-of-bounds excludes are clipped`() {
        val keep = listOf(TrimRange(-500L, 2_000L), TrimRange(8_000L, 12_000L))
            .complementWithin(10_000L)
        assertEquals(listOf(TrimRange(2_000L, 8_000L)), keep)
    }

    @Test
    fun `centeredSlice is a quarter in the middle`() {
        val range = TrimRange.centeredSlice(1_000L)
        assertEquals(375L, range.startMs)
        assertEquals(625L, range.endMs)
    }
}
