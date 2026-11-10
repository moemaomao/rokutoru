package com.rokutoru.pipeline

import com.rokutoru.model.MangaPage
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object PageExporter {
    fun exportPage(page: MangaPage, outputDirectory: File): File {
        require(page.file.isFile) { "File gambar tidak ditemukan: ${page.file.name}" }
        val source = ImageIO.read(page.file)
            ?: error("Format gambar tidak didukung untuk ekspor: ${page.file.extension}")
        val output = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val graphics = output.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, null)
            page.textBlocks.forEach { block ->
                if (block.translatedText.isNotBlank()) {
                    drawTranslation(graphics, block, source.width, source.height)
                }
            }
        } finally {
            graphics.dispose()
        }

        check(outputDirectory.exists() || outputDirectory.mkdirs()) {
            "Tidak bisa membuat folder ekspor: ${outputDirectory.absolutePath}"
        }
        val outputFile = File(outputDirectory, "${page.file.nameWithoutExtension}_translated.png")
        check(ImageIO.write(output, "png", outputFile)) { "Gagal menulis file PNG." }
        return outputFile
    }

    fun exportAll(pages: List<MangaPage>, outputDirectory: File): List<File> =
        pages.map { exportPage(it, outputDirectory) }

    private fun drawTranslation(graphics: Graphics2D, block: com.rokutoru.model.TextBlock, imageWidth: Int, imageHeight: Int) {
        val x = (block.x * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val y = (block.y * imageHeight).toInt().coerceIn(0, imageHeight - 1)
        val width = (block.width * imageWidth).toInt().coerceIn(1, imageWidth - x)
        val height = (block.height * imageHeight).toInt().coerceIn(1, imageHeight - y)
        graphics.color = Color.WHITE
        graphics.fillRect(x, y, width, height)

        val fontSize = (height * 0.28f).toInt().coerceIn(10, 48)
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
        graphics.color = Color.BLACK
        val metrics = graphics.fontMetrics
        val lines = wrapText(block.translatedText, metrics, (width - 8).coerceAtLeast(1))
        val lineHeight = metrics.height
        val visibleLines = lines.take((height / lineHeight).coerceAtLeast(1))
        val totalHeight = visibleLines.size * lineHeight
        var baseline = y + ((height - totalHeight) / 2) + metrics.ascent
        visibleLines.forEach { line ->
            val textWidth = metrics.stringWidth(line)
            graphics.drawString(line, x + ((width - textWidth) / 2).coerceAtLeast(4), baseline)
            baseline += lineHeight
        }
    }

    private fun wrapText(text: String, metrics: java.awt.FontMetrics, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            var line = ""
            paragraph.forEach { character ->
                val next = line + character
                if (line.isNotEmpty() && metrics.stringWidth(next) > maxWidth) {
                    lines += line
                    line = character.toString()
                } else {
                    line = next
                }
            }
            if (line.isNotEmpty()) lines += line
        }
        return lines.ifEmpty { listOf("") }
    }
}
