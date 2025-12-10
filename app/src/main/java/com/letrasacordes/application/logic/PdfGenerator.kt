package com.letrasacordes.application.logic

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.letrasacordes.application.database.Cancion
import java.io.FileOutputStream

class PdfGenerator(private val context: Context) {

    private val pageHeight = 1120
    private val pageWidth = 792
    private val margin = 72f
    private val contentWidth = pageWidth - 2 * margin

    private val titlePaint = Paint().apply { color = 0xFF000000.toInt(); textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val authorPaint = Paint().apply { color = 0xFF333333.toInt(); textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) }
    private val chordPaint = Paint().apply { color = 0xFF0000FF.toInt(); textSize = 11f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val lyricPaint = Paint().apply { color = 0xFF000000.toInt(); textSize = 12f; typeface = Typeface.MONOSPACE }
    private val indexTitlePaint = Paint().apply { color = 0xFF000000.toInt(); textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val indexTextPaint = Paint().apply { color = 0xFF000000.toInt(); textSize = 12f }

    fun generateSongbookPdf(
        songs: List<Cancion>,
        withChords: Boolean,
        outputUri: Uri
    ) {
        val sortedSongs = songs.sortedBy { it.titulo }
        val pdfDocument = PdfDocument()

        createIndexPage(pdfDocument, sortedSongs)

        sortedSongs.forEach {
            createSongPage(pdfDocument, it, withChords)
        }

        try {
            context.contentResolver.openOutputStream(outputUri)?.use { fileOutputStream ->
                pdfDocument.writeTo(fileOutputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace() // Consider logging this error properly
        } finally {
            pdfDocument.close()
        }
    }

    private fun createIndexPage(pdfDocument: PdfDocument, songs: List<Cancion>) {
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create())
        var y = margin

        page.canvas.drawText("Índice de Canciones", (pageWidth / 2).toFloat(), y, indexTitlePaint)
        y += 50

        songs.forEachIndexed { index, song ->
            if (y > pageHeight - margin) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create())
                y = margin
            }
            page.canvas.drawText("${index + 1}. ${song.titulo}", margin, y, indexTextPaint)
            y += 20
        }
        pdfDocument.finishPage(page)
    }

    private fun createSongPage(pdfDocument: PdfDocument, song: Cancion, withChords: Boolean) {
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create())
        var y = margin

        page.canvas.drawText(song.titulo, margin, y, titlePaint)
        y += 30

        song.autor?.let {
            page.canvas.drawText(it, margin, y, authorPaint)
            y += 40
        }

        val formattedLines = SongTextFormatter.formatSongTextForDisplay(song.letraOriginal, 0, withChords)

        formattedLines.forEach { (chordLine, lyricLine) ->
            if (y > pageHeight - margin - 30) { // Check for space before drawing lines
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create())
                y = margin
            }

            if (withChords && chordLine.isNotBlank()) {
                page.canvas.drawText(chordLine, margin, y, chordPaint)
                y += 15
            }
            page.canvas.drawText(lyricLine, margin, y, lyricPaint)
            y += 25 // Space between full lines (chord + lyric)
        }

        pdfDocument.finishPage(page)
    }
}
