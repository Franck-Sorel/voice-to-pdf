package com.sttapp.core.pdf

enum class PdfPageSize(val widthPt: Float, val heightPt: Float) {
    A4(595f, 842f),
    LETTER(612f, 792f),
}

/**
 * Platform-neutral destination for an exported PDF.
 *
 * Core must stay Android-free, so we never leak `android.net.Uri` through
 * this interface. Whether the URI string points at a real `content://`
 * provider is an Android (data-layer) concern.
 */
sealed interface PdfDestination {
    /** A `content://` URI string supplied by the Storage Access Framework. */
    data class ContentUri(val uriString: String) : PdfDestination
}

/** Print layout, defaults to the MVP spec: A4, 11 pt, 1.5 line spacing. */
data class PrintConfig(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val fontSizePt: Float = 11f,
    val lineSpacing: Float = 1.5f,
    val marginPt: Float = 56f,
    val titleSizePt: Float = 14f,
)

data class PdfExportRequest(
    val title: String,
    val dateLabel: String,
    val bodyText: String,
    val config: PrintConfig = PrintConfig(),
)

interface PdfExporter {

    /**
     * Renders [request] to the destination given by [destination] (a content
     * URI supplied by the Storage Access Framework). Must be called on a
     * background dispatcher.
     */
    suspend fun export(request: PdfExportRequest, destination: PdfDestination)
}
