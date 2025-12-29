package com.letrasacordes.application.logic

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.letrasacordes.application.database.Cancion

class PdfGenerator(private val context: Context) {

    private val pageHeight = 842
    private val pageWidth = 595
    private val margin = 40f
    private val columnGap = 20f
    private val contentWidth = pageWidth - (margin * 2)
    private val columnWidth = (contentWidth - columnGap) / 2

    private val titlePaint = Paint().apply {
        color = 0xFF000000.toInt()
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val chordPaint = Paint().apply {
        color = -16777088
        textSize = 11f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val lyricPaint = Paint().apply {
        color = 0xFF000000.toInt()
        textSize = 11f
        typeface = Typeface.MONOSPACE
    }

    private val indexTitlePaint = Paint().apply {
        color = 0xFF000000.toInt()
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val indexTextPaint = Paint().apply {
        color = 0xFF000000.toInt()
        textSize = 12f
    }

    private var pdfDocument: PdfDocument? = null
    private var currentPage: PdfDocument.Page? = null
    private var currentY: Float = margin
    private var currentColumn: Int = 0

    fun generateSongbookPdf(
        songs: List<Cancion>,
        withChords: Boolean,
        includeIndex: Boolean,
        compactMode: Boolean,
        outputUri: Uri
    ) {
        val sortedSongs = songs.sortedBy { it.titulo }
        pdfDocument = PdfDocument()

        if (includeIndex && sortedSongs.size > 1) {
            createIndexPage(sortedSongs)
        }

        startNewPage()
        
        sortedSongs.forEachIndexed { index, song ->
            if (!compactMode && index > 0) {
                startNewPage()
            }
            renderSong(song, index + 1, withChords, compactMode)
        }

        currentPage?.let { pdfDocument?.finishPage(it) }
        currentPage = null

        try {
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                pdfDocument?.writeTo(outputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument?.close()
            pdfDocument = null
        }
    }

    private fun createIndexPage(songs: List<Cancion>) {
        val doc = pdfDocument ?: return
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, doc.pages.size + 1).create())
        var y = margin

        page.canvas.drawText("Índice de Canciones", pageWidth / 2f, y + 10, indexTitlePaint)
        y += 50

        songs.forEachIndexed { index, song ->
            if (y > pageHeight - margin) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, doc.pages.size + 1).create())
                y = margin
            }
            page.canvas.drawText("${index + 1}. ${song.titulo}", margin, y, indexTextPaint)
            y += 20
        }
        doc.finishPage(page)
    }

    private fun startNewPage() {
        val doc = pdfDocument ?: return
        currentPage?.let { doc.finishPage(it) }
        
        currentPage = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, doc.pages.size + 1).create())
        currentY = margin
        currentColumn = 0
    }

    private fun moveToNextColumn() {
        if (currentColumn == 0) {
            currentColumn = 1
            currentY = margin
        } else {
            startNewPage()
        }
    }

    private fun renderSong(song: Cancion, songNumber: Int, withChords: Boolean, compactMode: Boolean) {
        if (currentY + 25f > pageHeight - margin) {
            moveToNextColumn()
        }

        val x = if (currentColumn == 0) margin else margin + columnWidth + columnGap

        val headerBuilder = StringBuilder("$songNumber. ${song.titulo}")
        if (!song.autor.isNullOrBlank()) {
            headerBuilder.append(", ${song.autor}")
        }
        if (withChords && !song.ritmo.isNullOrBlank()) {
             headerBuilder.append(" (${song.ritmo})")
        }
        val headerText = headerBuilder.toString()

        currentPage?.canvas?.drawText(headerText, x, currentY + 14, titlePaint)
        currentY += 25f

        val formattedLines = SongTextFormatter.formatSongTextForDisplay(song.letraOriginal, 0, withChords)

        for ((chordLine, lyricLine) in formattedLines) {
            val wrappedLines = wrapLines(chordLine, lyricLine, columnWidth)
            for ((wChord, wLyric) in wrappedLines) {
                val lineHeight = if (!withChords || wChord.isBlank()) 16f else 30f
                
                if (currentY + lineHeight > pageHeight - margin) {
                    moveToNextColumn()
                }

                // Recalculate X as currentColumn might have changed
                val currentX = if (currentColumn == 0) margin else margin + columnWidth + columnGap

                if (!withChords || wChord.isBlank()) {
                    currentPage?.canvas?.drawText(wLyric, currentX, currentY + 12, lyricPaint)
                    currentY += 16f
                } else {
                    currentPage?.canvas?.drawText(wChord, currentX, currentY + 10, chordPaint)
                    currentPage?.canvas?.drawText(wLyric, currentX, currentY + 24, lyricPaint)
                    currentY += 30f
                }
            }
        }
        
        if (compactMode) {
            currentY += 20f
        }
    }

    private fun wrapLines(chordLine: String, lyricLine: String, maxWidth: Float): List<Pair<String, String>> {
        val wrapped = mutableListOf<Pair<String, String>>()
        val charWidth = lyricPaint.measureText("M") 
        if (charWidth == 0f) return listOf(chordLine to lyricLine)

        val maxChars = (maxWidth / charWidth).toInt()
        val maxLen = maxOf(chordLine.length, lyricLine.length)

        if (maxLen <= maxChars) {
            return listOf(chordLine to lyricLine)
        }

        var currentStart = 0
        while (currentStart < maxLen) {
            var splitIndex = minOf(currentStart + maxChars, maxLen)
            
            if (splitIndex < maxLen && currentStart < lyricLine.length) {
                val limit = minOf(splitIndex, lyricLine.length)
                val lastSpace = lyricLine.lastIndexOf(' ', limit - 1)
                if (lastSpace >= currentStart) {
                    splitIndex = lastSpace + 1
                }
            }

            val cSeg = if (currentStart < chordLine.length) {
                val end = minOf(splitIndex, chordLine.length)
                chordLine.substring(currentStart, end)
            } else ""

            val lSeg = if (currentStart < lyricLine.length) {
                val end = minOf(splitIndex, lyricLine.length)
                lyricLine.substring(currentStart, end)
            } else ""

            wrapped.add(cSeg to lSeg)
            currentStart = splitIndex
        }

        return wrapped
    }
}