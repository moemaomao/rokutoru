package com.rokutoru.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rokutoru.model.AppState
import com.rokutoru.model.AiProvider
import com.rokutoru.model.ToolMode
import com.rokutoru.pipeline.PageExporter
import com.rokutoru.pipeline.TranslatorPipeline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.jetbrains.skia.Image
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.pow
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RokutoruApp(state: AppState) {
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showRunConfirmation by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7C4DFF),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Rokutoru", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        // Open Folder
                        IconButton(onClick = {
                            val chooser = JFileChooser()
                            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                state.openFolder(chooser.selectedFile)
                            }
                        }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open Folder")
                        }

                        IconButton(
                            onClick = { showRunConfirmation = true },
                            enabled = !state.isRunning && state.pages.isNotEmpty() && state.useVision
                        ) {
                            if (state.isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                            }
                        }

                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(
                            onClick = {
                                val chooser = JFileChooser()
                                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                chooser.dialogTitle = "Choose export folder"
                                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    try {
                                        val output = File(chooser.selectedFile, "Rokutoru-Export")
                                        val exported = PageExporter.exportAll(state.pages.toList(), output)
                                        state.statusMessage = "Exported ${exported.size} pages to ${output.absolutePath}"
                                    } catch (error: Exception) {
                                        state.statusMessage = "Export failed: ${error.message ?: "unknown error"}"
                                    }
                                }
                            },
                            enabled = state.pages.isNotEmpty() && state.pages.any { page ->
                                page.textBlocks.any { it.translatedText.isNotBlank() }
                            }
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Export translated pages")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E1E)
                    )
                )
            },
            bottomBar = {
                BottomAppBar(
                    containerColor = Color(0xFF1E1E1E),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (state.pages.isNotEmpty())
                            "Page ${state.currentPageIndex + 1} / ${state.pages.size}"
                        else "No project",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Sidebar - Page list
                PageListSidebar(state)

                // Main canvas area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF0D0D0D)),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.currentPage != null) {
                        PageCanvas(state)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Open a folder containing manga pages",
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Right panel - Text blocks / tools
                RightPanel(state)
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("API settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.apiKey,
                            onValueChange = { state.apiKey = it },
                            label = { Text("OpenAI API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.geminiApiKey,
                            onValueChange = { state.geminiApiKey = it },
                            label = { Text("Gemini API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Pilih provider di panel kanan. Key hanya disimpan selama aplikasi berjalan. " +
                                "Environment variable juga didukung: OPENAI_API_KEY dan GEMINI_API_KEY.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) { Text("Done") }
                }
            )
        }

        if (showRunConfirmation) {
            AlertDialog(
                onDismissRequest = { showRunConfirmation = false },
                title = { Text("Run Vision pipeline?") },
                text = {
                    Text(
                        "Semua ${state.pages.size} halaman akan dikirim ke ${state.aiProvider.label} " +
                            "untuk deteksi teks dan terjemahan. " +
                            "Penggunaan API dapat dikenakan biaya."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRunConfirmation = false
                            scope.launch { runPipeline(state) }
                        }
                    ) { Text("Run all pages") }
                },
                dismissButton = {
                    TextButton(onClick = { showRunConfirmation = false }) { Text("Cancel") }
                }
            )
        }
    }
}

private suspend fun runPipeline(state: AppState) {
    val apiKey = when (state.aiProvider) {
        AiProvider.OPENAI -> state.apiKey.ifBlank { System.getenv("OPENAI_API_KEY").orEmpty() }
        AiProvider.GEMINI -> state.geminiApiKey.ifBlank {
            System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY").orEmpty()
        }
    }
    state.isRunning = true
    try {
        state.statusMessage = "Processing ${state.pages.size} pages..."
        val pipeline = TranslatorPipeline(
            sourceLang = state.sourceLang,
            targetLang = state.targetLang,
            provider = state.aiProvider,
            apiKey = apiKey,
            model = state.selectedModel
        )
        pipeline.processAll(state.pages.toList()) { completed, total ->
            state.statusMessage = "${state.aiProvider.label}: processed $completed / $total pages"
        }
        state.statusMessage = "${state.aiProvider.label} finished: ${state.pages.size} pages processed"
    } catch (error: Exception) {
        state.statusMessage = error.message ?: "Pipeline failed"
    } finally {
        state.isRunning = false
    }
}

@Composable
fun PageListSidebar(state: AppState) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color(0xFF1A1A1A))
            .padding(8.dp)
    ) {
        Text(
            "Pages",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(8.dp)
        )
        HorizontalDivider(color = Color.DarkGray)

        if (state.pages.isEmpty()) {
            Text(
                "No pages loaded",
                color = Color.Gray,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                itemsIndexed(state.pages) { index, page ->
                    val isSelected = index == state.currentPageIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color(0xFF7C4DFF).copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { state.currentPageIndex = index }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = page.file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (page.isProcessed) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF03DAC6),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PageCanvas(state: AppState) {
    val currentPage = state.currentPage
    var zoom by remember(currentPage?.file?.absolutePath) { mutableFloatStateOf(1f) }
    var pan by remember(currentPage?.file?.absolutePath) { mutableStateOf(Offset.Zero) }
    var selectionStart by remember(currentPage?.file?.absolutePath) { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember(currentPage?.file?.absolutePath) { mutableStateOf<Offset?>(null) }
    val pageImage = remember(currentPage?.file?.absolutePath) {
        currentPage?.file?.takeIf { it.exists() }?.let { file ->
            try {
                val bytes = file.readBytes()
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun setZoom(value: Float) {
        zoom = value.coerceIn(0.25f, 6f)
        if (zoom <= 1f) pan = Offset.Zero
    }

    LaunchedEffect(state.isSelectingRegion) {
        if (state.isSelectingRegion) {
            zoom = 1f
            pan = Offset.Zero
            selectionStart = null
            selectionEnd = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        val imageAspect = pageImage?.let { it.width.toFloat() / it.height } ?: 0.7f
        val containerAspect = maxWidth.value / maxHeight.value
        val imageWidth = if (containerAspect > imageAspect) maxHeight * imageAspect else maxWidth
        val imageHeight = if (containerAspect > imageAspect) maxHeight else maxWidth / imageAspect

        Box(
            modifier = Modifier
                .clipToBounds()
                .width(imageWidth)
                .height(imageHeight)
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
                .pointerInput(currentPage?.file?.absolutePath) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scroll = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (scroll != 0f && !state.isSelectingRegion) {
                                    setZoom(zoom * 1.1f.pow(-scroll))
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
                .pointerInput(currentPage?.file?.absolutePath, state.isSelectingRegion) {
                    detectDragGestures(
                        onDragStart = { position ->
                            if (state.isSelectingRegion) {
                                selectionStart = Offset(position.x / size.width, position.y / size.height)
                                selectionEnd = selectionStart
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (state.isSelectingRegion) {
                                selectionEnd = Offset(
                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f)
                                )
                                change.consume()
                            } else if (zoom > 1f) {
                                pan += Offset(dragAmount.x, dragAmount.y)
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            val start = selectionStart
                            val end = selectionEnd
                            if (state.isSelectingRegion && start != null && end != null) {
                                val left = minOf(start.x, end.x)
                                val top = minOf(start.y, end.y)
                                val width = kotlin.math.abs(end.x - start.x)
                                val height = kotlin.math.abs(end.y - start.y)
                                if (width > 0.01f && height > 0.01f) {
                                    state.addTextBlock(left, top, width, height)
                                } else {
                                    state.statusMessage = "Area terlalu kecil; coba pilih area yang lebih besar"
                                }
                            }
                            selectionStart = null
                            selectionEnd = null
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (pageImage != null) {
                Image(
                    bitmap = pageImage,
                    contentDescription = currentPage?.file?.name ?: "Manga page",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(Modifier.fillMaxSize()) {
                    currentPage?.textBlocks?.forEach { block ->
                        val selected = block.id == state.selectedBlockId
                        drawRect(
                            color = if (selected) Color(0xFF03DAC6) else Color(0xFF7C4DFF),
                            topLeft = Offset(block.x * size.width, block.y * size.height),
                            size = Size(block.width * size.width, block.height * size.height),
                            style = Stroke(width = if (selected) 3.dp.toPx() else 2.dp.toPx())
                        )
                    }
                    val start = selectionStart
                    val end = selectionEnd
                    if (start != null && end != null) {
                        drawRect(
                            color = Color(0xFF03DAC6),
                            topLeft = Offset(
                                minOf(start.x, end.x) * size.width,
                                minOf(start.y, end.y) * size.height
                            ),
                            size = Size(
                                kotlin.math.abs(end.x - start.x) * size.width,
                                kotlin.math.abs(end.y - start.y) * size.height
                            ),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            } else {
                Text("Image could not be loaded", color = Color.Gray)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            color = Color(0xCC1E1E1E),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { setZoom(zoom / 1.2f) }) { Text("−") }
                Text("${(zoom * 100).toInt()}%", color = Color.White)
                TextButton(onClick = { setZoom(zoom * 1.2f) }) { Text("+") }
                TextButton(onClick = { setZoom(1f) }) { Text("Fit") }
            }
        }

        if (state.isSelectingRegion) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Color(0xCC1E1E1E),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "Drag untuk memilih area teks",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { state.prevPage() }, enabled = state.currentPageIndex > 0) {
                Text("Prev")
            }
            Button(onClick = { state.nextPage() }, enabled = state.currentPageIndex < state.pages.lastIndex) {
                Text("Next")
            }
        }
    }
}

@Composable
fun RightPanel(state: AppState) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(Color(0xFF1A1A1A))
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Tools", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                ToolMode.VIEW to "View",
                ToolMode.TEXT_EDIT to "Text",
                ToolMode.INPAINT to "Inpaint"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = state.toolMode == mode,
                    onClick = {
                        state.toolMode = mode
                        state.isSelectingRegion = mode == ToolMode.TEXT_EDIT
                    },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        OutlinedButton(
            onClick = {
                state.toolMode = ToolMode.TEXT_EDIT
                state.isSelectingRegion = !state.isSelectingRegion
            },
            enabled = state.currentPage != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isSelectingRegion) "Cancel area selection" else "Select text area")
        }
        Text(
            "Pilih area secara manual, lalu isi teks asli dan terjemahannya. Run memakai Vision untuk deteksi otomatis.",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider(color = Color.DarkGray)

        Text("AI provider", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { provider ->
                FilterChip(
                    selected = state.aiProvider == provider,
                    onClick = {
                        if (state.aiProvider != provider) {
                            state.aiProvider = provider
                            state.selectedModel = provider.defaultModel
                        }
                    },
                    label = { Text(provider.label) }
                )
            }
        }

        Text("Language", style = MaterialTheme.typography.titleSmall, color = Color.White)

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.sourceLang,
                onValueChange = { state.sourceLang = it },
                label = { Text("From") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Text(" → ", color = Color.Gray)
            OutlinedTextField(
                value = state.targetLang,
                onValueChange = { state.targetLang = it },
                label = { Text("To") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.useVision,
                onCheckedChange = { state.useVision = it }
            )
            Text("Use Vision Model", color = Color.LightGray, fontSize = 13.sp)
        }

        OutlinedTextField(
            value = state.selectedModel,
            onValueChange = { state.selectedModel = it },
            label = { Text("${state.aiProvider.label} model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        HorizontalDivider(color = Color.DarkGray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text blocks", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text("${state.currentPage?.textBlocks?.size ?: 0}", color = Color.LightGray)
        }

        val blocks = state.currentPage?.textBlocks.orEmpty()
        if (blocks.isEmpty()) {
            Text("Belum ada area teks. Pilih area atau jalankan Vision pipeline.", color = Color.Gray)
        } else {
            blocks.forEachIndexed { index, block ->
                Surface(
                    color = if (state.selectedBlockId == block.id) Color(0xFF34245C) else Color(0xFF242424),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().clickable { state.selectedBlockId = block.id }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Block ${index + 1}", color = Color.White)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { state.removeTextBlock(block.id) }) {
                                Text("Delete", color = Color(0xFFFF8A80))
                            }
                        }
                        OutlinedTextField(
                            value = block.originalText,
                            onValueChange = { value ->
                                state.updateTextBlock(block.id) { it.copy(originalText = value) }
                            },
                            label = { Text("Original") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 3
                        )
                        OutlinedTextField(
                            value = block.translatedText,
                            onValueChange = { value ->
                                state.updateTextBlock(block.id) { it.copy(translatedText = value) }
                            },
                            label = { Text("Translation") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 4
                        )
                    }
                }
            }
        }

        Text(
            "Export menimpa area teks dengan kotak putih dan teks terjemahan.",
            color = Color.DarkGray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}