package com.rokutoru.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import java.io.File

enum class ToolMode {
    VIEW, TEXT_EDIT, INPAINT
}

enum class AiProvider(val label: String, val defaultModel: String) {
    OPENAI("OpenAI", "gpt-4o"),
    GEMINI("Gemini", "gemini-3.8-flash")
}

data class TextBlock(
    val id: String,
    val originalText: String = "",
    val translatedText: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0.1f,
    val height: Float = 0.05f,
    val fontSize: Float = 18f,
    val isVertical: Boolean = false
)

data class MangaPage(
    val file: File,
    val textBlocks: androidx.compose.runtime.snapshots.SnapshotStateList<TextBlock> = mutableStateListOf(),
    var isProcessed: Boolean = false
)

class AppState {
    var projectFolder by mutableStateOf<File?>(null)
    var pages = mutableStateListOf<MangaPage>()
    var currentPageIndex by mutableStateOf(0)
    var toolMode by mutableStateOf(ToolMode.VIEW)
    var isRunning by mutableStateOf(false)
    var statusMessage by mutableStateOf("Ready")
    var sourceLang by mutableStateOf("ja")
    var targetLang by mutableStateOf("id")
    var useVision by mutableStateOf(true)
    var aiProvider by mutableStateOf(AiProvider.OPENAI)
    var apiKey by mutableStateOf("")
    var geminiApiKey by mutableStateOf("")
    var selectedModel by mutableStateOf(AiProvider.OPENAI.defaultModel)
    var selectedBlockId by mutableStateOf<String?>(null)
    var isSelectingRegion by mutableStateOf(false)

    val currentPage: MangaPage?
        get() = pages.getOrNull(currentPageIndex)

    fun openFolder(folder: File) {
        require(folder.isDirectory) { "Selected path is not a folder" }
        projectFolder = folder
        pages.clear()
        val imageFiles = folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "bmp")
        }?.sortedBy { it.name } ?: emptyList()

        pages.addAll(imageFiles.map { MangaPage(it) })
        currentPageIndex = 0
        selectedBlockId = null
        statusMessage = "Loaded ${pages.size} pages"
    }

    fun nextPage() {
        if (currentPageIndex < pages.lastIndex) {
            currentPageIndex++
            selectedBlockId = null
        }
    }

    fun prevPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            selectedBlockId = null
        }
    }

    fun addTextBlock(x: Float, y: Float, width: Float, height: Float) {
        val page = currentPage ?: return
        val block = TextBlock(
            id = java.util.UUID.randomUUID().toString(),
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f),
            width = width.coerceIn(0.01f, 1f),
            height = height.coerceIn(0.01f, 1f)
        )
        page.textBlocks.add(block)
        selectedBlockId = block.id
        isSelectingRegion = false
        statusMessage = "Area teks ditambahkan; isi teks asli dan terjemahannya"
    }

    fun updateTextBlock(blockId: String, update: (TextBlock) -> TextBlock) {
        val blocks = currentPage?.textBlocks ?: return
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index >= 0) blocks[index] = update(blocks[index])
    }

    fun removeTextBlock(blockId: String) {
        currentPage?.textBlocks?.removeAll { it.id == blockId }
        if (selectedBlockId == blockId) selectedBlockId = null
    }
}