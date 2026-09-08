package com.sttapp.core.format

import com.sttapp.core.recognition.TranscriptionSegment

/**
 * Pure formatting logic (no Android dependencies) so it can be unit-tested
 * on the JVM.
 *
 * MVP 1 F7: insert an automatic paragraph break wherever the pause between
 * two transcript segments is longer than [paragraphBreakMs] (default 2 s).
 */
object TranscriptFormatter {

    private const val DEFAULT_PARAGRAPH_BREAK_MS = 2_000L

    fun toPlainText(
        segments: List<TranscriptionSegment>,
        paragraphBreakMs: Long = DEFAULT_PARAGRAPH_BREAK_MS,
    ): String {
        if (segments.isEmpty()) return ""

        val out = StringBuilder()
        var previousEndMs = segments.first().startMs
        var isFirst = true

        for (segment in segments) {
            val gapMs = segment.startMs - previousEndMs
            if (isFirst) {
                isFirst = false
            } else if (gapMs > paragraphBreakMs) {
                out.append("\n\n")
            } else {
                out.append(" ")
            }
            out.append(segment.text.trim())
            previousEndMs = segment.endMs
        }

        return out.toString().trim()
    }
}
