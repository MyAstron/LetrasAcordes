package com.letrasacordes.application.logic

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.letrasacordes.application.database.Cancion
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class PdfGenerator(private val context: Context) {

    // A4 size in points at 72 dpi (approximate)
    private val pageHeight = 842 
    private val pageWidth = 595
    private val margin = 40f
    private val columnGap = 20f
    
    // Effective content area
    private val contentWidth = pageWidth - 2 * margin
    private val columnWidth = (contentWidth - columnGap) / 2

    // Paints
    private val titlePaint = Paint().apply { color = 0xFF000000.toInt(); textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    
    // Azul Marino (Navy) y tamaño 11
    private val chordPaint = Paint().apply { 
        color = 0xFF000080.toInt() // Navy Blue 
        textSize = 11f 
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) 
    }
    
    // Tamaño 11
    private val lyricPaint = Paint().apply { 
        color = 0xFF000000.toInt() 
        textSize = 11f 
        typeface = Typeface.MONOSPACE 
    }
    
    // Index specific paints
    private val indexTitlePaint = Paint().apply { color = 0xFF000000.toInt(); textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val indexTextPaint = Paint().apply { color = 0xFF000000.toInt(); textSize = 12f }

    // State variables for layout
    private var currentY = margin
    private var currentColumn = 0 // 0 = left, 1 = right
    private var currentPage: PdfDocument.Page? = null
    private var pdfDocument: PdfDocument? = null

    fun generateSongbookPdf(
        songs: List<Cancion>,
        withChords: Boolean,
        includeIndex: Boolean,
        compactMode: Boolean, // NUEVO PARÁMETRO
        outputUri: Uri
    ) {
        // Reiniciar estado
        currentPage = null
        currentY = margin
        currentColumn = 0
        
        val sortedSongs = songs.sortedBy { it.titulo }
        pdfDocument = PdfDocument()

        // 1. Generate Index Page
        if (includeIndex && songs.size > 1) { 
            createIndexPage(sortedSongs)
        }
        
        // Reset layout state for songs (start fresh page after index)
        startNewPage() 

        // 2. Generate Songs in columns passing the index (1-based)
        sortedSongs.forEachIndexed { index, song ->
            // Si NO estamos en modo compacto, forzamos inicio de página para cada nueva canción
            // a menos que sea la primera canción (que ya empieza en página nueva)
            if (!compactMode && index > 0) {
                 startNewPage()
            }
            
            renderSong(song, index + 1, withChords, compactMode)
        }

        // Finish the last page if exists
        currentPage?.let { pdfDocument?.finishPage(it) }
        currentPage = null

        try {
            context.contentResolver.openOutputStream(outputUri)?.use { fileOutputStream ->
                pdfDocument?.writeTo(fileOutputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument?.close()
            pdfDocument = null 
        }
    }

    private fun createIndexPage(songs: List<Cancion>) {
        var page = pdfDocument!!.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument!!.pages.size + 1).create())
        var y = margin

        page.canvas.drawText("Índice de Canciones", (pageWidth / 2).toFloat(), y + 10, indexTitlePaint)
        y += 50

        songs.forEachIndexed { index, song ->
            if (y > pageHeight - margin) {
                pdfDocument!!.finishPage(page)
                page = pdfDocument!!.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument!!.pages.size + 1).create())
                y = margin
            }
            page.canvas.drawText("${index + 1}. ${song.titulo}", margin, y, indexTextPaint)
            y += 20
        }
        pdfDocument!!.finishPage(page)
    }

    private fun startNewPage() {
        currentPage?.let { pdfDocument?.finishPage(it) }
        
        currentPage = pdfDocument!!.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument!!.pages.size + 1).create())
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
        // Calculate header height
        val headerHeight = 25f 

        // Check if header fits in current column
        if (currentY + headerHeight > pageHeight - margin) {
            moveToNextColumn()
        }

        // Draw Header
        val x = if (currentColumn == 0) margin else margin + columnWidth + columnGap
        
        val headerText = buildString {
            append("$songNumber. ${song.titulo}")
            if (!song.autor.isNullOrBlank()) {
                append(", ${song.autor}")
            }
            if (withChords && !song.ritmo.isNullOrBlank()) {
                append(" (${song.ritmo})")
            }
        }

        currentPage?.canvas?.drawText(headerText, x, currentY + 14, titlePaint)
        currentY += headerHeight

        val formattedLines = SongTextFormatter.formatSongTextForDisplay(song.letraOriginal, 0, withChords)
        
        formattedLines.forEach { (chordLine, lyricLine) ->
            val wrappedLines = wrapLines(chordLine, lyricLine, columnWidth)
            
            wrappedLines.forEach { (wChord, wLyric) ->
                val lineHeight = if (withChords && wChord.isNotBlank()) 30f else 16f
                
                if (currentY + lineHeight > pageHeight - margin) {
                    moveToNextColumn()
                }
                
                val currentX = if (currentColumn == 0) margin else margin + columnWidth + columnGap

                currentPage?.canvas?.apply {
                    if (withChords && wChord.isNotBlank()) {
                        drawText(wChord, currentX, currentY + 10, chordPaint)
                        drawText(wLyric, currentX, currentY + 24, lyricPaint)
                        currentY += 30f
                    } else {
                        drawText(wLyric, currentX, currentY + 12, lyricPaint)
                        currentY += 16f
                    }
                }
            }
        }
        
        // Add spacing after song only if in compact mode (otherwise next song starts on new page anyway)
        if (compactMode) {
            currentY += 20f
        }
    }

    private fun wrapLines(chordLine: String, lyricLine: String, maxWidth: Float): List<Pair<String, String>> {
        val wrapped = mutableListOf<Pair<String, String>>()
        val charWidth = lyricPaint.measureText("M")
        if (charWidth == 0f) return listOf(chordLine to lyricLine)
        
        val maxChars = (maxWidth / charWidth).toInt()
        val maxLen = max(chordLine.length, lyricLine.length)

        if (maxLen <= maxChars) {
             return listOf(chordLine to lyricLine)
        }

        var currentStart = 0
        while (currentStart < maxLen) {
            var splitIndex = min(currentStart + maxChars, maxLen)
            
            if (splitIndex < maxLen) {
                if (currentStart < lyricLine.length) {
                    val limit = min(splitIndex, lyricLine.length)
                    val lastSpace = lyricLine.lastIndexOf(' ', limit - 1)
                    if (lastSpace >= currentStart) {
                        splitIndex = lastSpace + 1 
                    }
                }
            }

            val cSeg = if (currentStart < chordLine.length) {
                val end = min(splitIndex, chordLine.length)
                chordLine.substring(currentStart, end)
            } else ""
            
            val lSeg = if (currentStart < lyricLine.length) {
                val end = min(splitIndex, lyricLine.length)
                lyricLine.substring(currentStart, end)
            } else ""
            
            wrapped.add(cSeg to lSeg)
            currentStart = splitIndex
        }
        
        return wrapped
    }
}