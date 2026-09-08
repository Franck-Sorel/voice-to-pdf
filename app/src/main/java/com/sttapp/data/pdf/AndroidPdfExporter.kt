package com.sttapp.data.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.sttapp.core.pdf.PdfExporter
import com.sttapp.core.pdf.PdfExportRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal, dependency-free PDF rendering with Android's built-in
 * `PdfDocument` (Apache-2.0). Covers MVP 1 F4/F7:
 *
 *  - A4 page, 11 pt body, 1.5 line spacing, header with title + date.
 *  - Word wrapping and automatic page breaks.
 *
 * Layout stays deliberately plain (single column, no fancy styling) to keep
 * output predictable across printers — see PRD "PDF formatting" risk.
 */
@Singleton
class AndroidPdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) : PdfExporter {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun export(request: PdfExportRequest, destination: Uri) {
        withContext(ioDispatcher) {
            val doc = PdfDocument()
            try {
                val config = request.config
                val pageWidthPt = config.pageSize.widthPt
                val pageHeightPt = config.pageSize.heightPt
                val marginPt = config.marginPt

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = config.titleSizePt
                    isFakeBoldText = true
                    color = TITLE_COLOR
                }
                val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = config.fontSizePt
                    color = HEADER_COLOR
                }
                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = config.fontSizePt
                    color = BODY_COLOR
                }

                val lineHeightPt = config.fontSizePt * config.lineSpacing
                val contentWidthPt = pageWidthPt - 2 * marginPt

                val bodyLines = wrapText(request.bodyText, bodyPaint, contentWidthPt)

                // Page 1 includes the header block.
                val headerBlockHeightPt = 3 * lineHeightPt
                val bodyLinesPerPage =
                    ((pageHeightPt - 2 * marginPt - headerBlockHeightPt) / lineHeightPt).toInt().coerceAtLeast(1)

                val totalLines = bodyLines.size
                val remaining = (totalLines - bodyLinesPerPage).coerceAtLeast(0)
                val extraPages = if (remaining == 0) 0 else (remaining + bodyLinesPerPage - 1) / bodyLinesPerPage
                val pageCount = 1 + extraPages

                var lineIndex = 0
                for (pageNumber in 1..pageCount) {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        pageWidthPt.toInt(),
                        pageHeightPt.toInt(),
                        pageNumber,
                    ).create()
                    val page = doc.startPage(pageInfo)
                    val canvas = page.canvas

                    var y = marginPt
                    if (pageNumber == 1) {
                        y = drawHeader(
                            canvas = canvas,
                            request = request,
                            bodyPaint = bodyPaint,
                            titlePaint = titlePaint,
                            headerPaint = headerPaint,
                            startY = y,
                            contentWidthPt = contentWidthPt,
                        )
                    }

                    val linesOnThisPage = bodyLinesPerPage
                    val end = minOf(lineIndex + linesOnThisPage, totalLines)
                    while (lineIndex < end) {
                        canvas.drawText(bodyLines[lineIndex], marginPt, y, bodyPaint)
                        y += lineHeightPt
                        lineIndex++
                    }

                    doc.finishPage(page)
                }

                val output = context.contentResolver.openOutputStream(destination, "w")
                    ?: error("Unable to open destination $destination")
                output.use { doc.writeTo(it) }
            } finally {
                doc.close()
            }
        }
    }

    private fun drawHeader(
        canvas: android.graphics.Canvas,
        request: PdfExportRequest,
        bodyPaint: Paint,
        titlePaint: Paint,
        headerPaint: Paint,
        startY: Float,
        contentWidthPt: Float,
    ): Float {
        val lineHeightPt = request.config.fontSizePt * request.config.lineSpacing
        val marginPt = request.config.marginPt

        var y = startY + request.config.titleSizePt
        canvas.drawText(request.title, marginPt, y, titlePaint)
        y += lineHeightPt

        canvas.drawText(request.dateLabel, marginPt, y, headerPaint)
        y += lineHeightPt * 0.75f

        val rulePaint = Paint(bodyPaint).apply { strokeWidth = 0.75f }
        canvas.drawLine(marginPt, y, marginPt + contentWidthPt, y, rulePaint)
        y += lineHeightPt

        return y
    }

    /**
     * Splits [text] into lines that fit [maxWidthPt] using simple
     * word-by-word wrapping. Paragraph breaks (`\n\n`) become blank lines so
     * F7 paragraph detection is preserved in the PDF.
     */
    internal fun wrapText(text: String, paint: Paint, maxWidthPt: Float): List<String> {
        if (text.isBlank()) return listOf("")

        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n\n")

        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            if (paragraphIndex > 0) lines += ""
            var current = StringBuilder()
            for (word in paragraph.split(Regex("\\s+"))) {
                val candidate = if (current.isEmpty()) word else "${current} $word"
                if (paint.measureText(candidate) <= maxWidthPt) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines += current.toString()
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
        }

        return lines
    }

    private companion object {
        const val TITLE_COLOR = 0xFF000000.toInt()
        const val HEADER_COLOR = 0xFF444444.toInt()
        const val BODY_COLOR = 0xFF000000.toInt()
    }
}
