package com.sttapp.core.format

import com.sttapp.core.recognition.TranscriptionSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFormatterTest {

    @Test
    fun `short gaps join with a space`() {
        val segments = listOf(
            TranscriptionSegment(0L, 1_500L, "Hello"),
            TranscriptionSegment(1_500L, 3_000L, "world"),
        )
        assertEquals("Hello world", TranscriptFormatter.toPlainText(segments))
    }

    @Test
    fun `pause over two seconds creates a paragraph break`() {
        val segments = listOf(
            TranscriptionSegment(0L, 2_000L, "First idea"),
            TranscriptionSegment(4_500L, 6_000L, "Second idea"),
        )
        assertEquals("First idea\n\nSecond idea", TranscriptFormatter.toPlainText(segments))
    }

    @Test
    fun `empty segments return empty string`() {
        assertEquals("", TranscriptFormatter.toPlainText(emptyList()))
    }

    @Test
    fun `custom threshold changes the break behaviour`() {
        val segments = listOf(
            TranscriptionSegment(0L, 2_000L, "A"),
            TranscriptionSegment(3_000L, 4_000L, "B"),
        )
        // 1 s gap: paragraph break only if threshold < 1000
        assertEquals("A B", TranscriptFormatter.toPlainText(segments, paragraphBreakMs = 1_500L))
        assertEquals("A\n\nB", TranscriptFormatter.toPlainText(segments, paragraphBreakMs = 500L))
    }
}
