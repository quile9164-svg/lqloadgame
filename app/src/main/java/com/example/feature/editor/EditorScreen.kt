package com.example.feature.editor

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.domain.model.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: PosterEditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val project by viewModel.projectState.collectAsState()
    val selectedId by viewModel.selectedLayerId.collectAsState()
    val applyState by viewModel.applyProgress.collectAsState()

    var activeTab by remember { mutableStateOf("background") }
    var showGrid by remember { mutableStateOf(false) }
    var showSafeArea by remember { mutableStateOf(true) }
    var isPreviewMode by remember { mutableStateOf(false) }

    // Media Pickers
    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.updateProjectState { current ->
                current.copy(background = current.background.copy(uri = it.toString()))
            }
        }
    }

    val stickerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.addStickerLayer(it.toString())
        }
    }

    Scaffold(
        topBar = {
            if (!isPreviewMode) {
                TopAppBar(
                    title = {
                        Text(
                            project.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Quay lại")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.undo() }, modifier = Modifier.testTag("undo_button")) {
                            Icon(Icons.AutoMirrored.Default.Undo, contentDescription = "Hoàn tác")
                        }
                        IconButton(onClick = { viewModel.redo() }, modifier = Modifier.testTag("redo_button")) {
                            Icon(Icons.AutoMirrored.Default.Redo, contentDescription = "Làm lại")
                        }
                        IconButton(onClick = { showGrid = !showGrid }) {
                            Icon(
                                if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                                contentDescription = "Bật lưới"
                            )
                        }
                        IconButton(onClick = { isPreviewMode = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Xem trước")
                        }
                        Button(
                            onClick = { viewModel.saveProjectLocal(context) { Toast.makeText(context, "Đã lưu dự án cục bộ", Toast.LENGTH_SHORT).show() } },
                            modifier = Modifier.padding(end = 4.dp).testTag("save_project_local")
                        ) {
                            Text("Lưu", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.applyPoster(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.testTag("apply_poster_button")
                        ) {
                            Text("Áp dụng", fontSize = 12.sp)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1E1E1E)) // Dark professional workspace
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Workspace / Canvas area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Checkerboard Canvas container with exact 320:504 aspect ratio
                    Box(
                        modifier = Modifier
                            .aspectRatio(320f / 504f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                // Clear selection if clicking on empty area
                                detectDragGestures { _, _ -> }
                            }
                    ) {
                        // Checkerboard pattern behind transparent elements
                        CheckerboardPattern()

                        // BACKGROUND LAYER
                        val bg = project.background
                        if (bg.uri.isNotEmpty()) {
                            AsyncImage(
                                model = bg.uri,
                                contentDescription = "Background image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = bg.x
                                        translationY = bg.y
                                        scaleX = bg.scale * (if (bg.isFlippedHorizontally) -1f else 1f)
                                        scaleY = bg.scale
                                        rotationZ = bg.rotation
                                    }
                            )
                        }

                        // SNAP GUIDELINES (drawn on canvas)
                        var drawHGuide by remember { mutableStateOf(false) }
                        var drawVGuide by remember { mutableStateOf(false) }

                        // LAYER RENDERERS
                        project.layers.forEach { layer ->
                            if (layer.isVisible) {
                                val isSelected = layer.id == selectedId
                                
                                Box(
                                    modifier = Modifier
                                        .offset(x = layer.x.dp, y = layer.y.dp)
                                        .size(layer.width.dp, layer.height.dp)
                                        .graphicsLayer {
                                            rotationZ = layer.rotation
                                            alpha = layer.opacity
                                        }
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            if (!layer.isLocked) {
                                                viewModel.selectLayer(layer.id)
                                            }
                                        }
                                        .pointerInput(layer.id) {
                                            if (!layer.isLocked && isSelected) {
                                                detectDragGestures(
                                                    onDragEnd = {
                                                        drawHGuide = false
                                                        drawVGuide = false
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        
                                                        // Check snapping to center
                                                        val targetX = layer.x + dragAmount.x
                                                        val targetY = layer.y + dragAmount.y
                                                        
                                                        // Center of layer relative to center of canvas (320x504)
                                                        val layerCenterX = targetX + layer.width / 2f
                                                        val layerCenterY = targetY + layer.height / 2f
                                                        
                                                        var finalX = targetX
                                                        var finalY = targetY
                                                        
                                                        // Snap Horizontal Center (160)
                                                        if (abs(layerCenterX - 160f) < 8f) {
                                                            finalX = 160f - layer.width / 2f
                                                            drawVGuide = true
                                                        } else {
                                                            drawVGuide = false
                                                        }
                                                        
                                                        // Snap Vertical Center (252)
                                                        if (abs(layerCenterY - 252f) < 8f) {
                                                            finalY = 252f - layer.height / 2f
                                                            drawHGuide = true
                                                        } else {
                                                            drawHGuide = false
                                                        }
                                                        
                                                        viewModel.updateLayerPosition(layer.id, finalX - layer.x, finalY - layer.y)
                                                    }
                                                )
                                            }
                                        }
                                ) {
                                    // Layer content rendering based on type
                                    when (layer.type) {
                                        LayerType.TEXT -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = when (layer.textAlignment) {
                                                    com.example.domain.model.TextAlign.LEFT -> Alignment.CenterStart
                                                    com.example.domain.model.TextAlign.RIGHT -> Alignment.CenterEnd
                                                    else -> Alignment.Center
                                                }
                                            ) {
                                                // Dynamic stylized text based on preset
                                                Text(
                                                    text = layer.textContent,
                                                    fontSize = layer.fontSize.sp,
                                                    color = Color(android.graphics.Color.parseColor(layer.textColor)),
                                                    fontWeight = if (layer.isBold) FontWeight.Bold else FontWeight.Normal,
                                                    textAlign = when (layer.textAlignment) {
                                                        com.example.domain.model.TextAlign.LEFT -> TextAlign.Left
                                                        com.example.domain.model.TextAlign.RIGHT -> TextAlign.Right
                                                        else -> TextAlign.Center
                                                    },
                                                    lineHeight = (layer.fontSize * layer.lineSpacing).sp,
                                                    fontFamily = when (layer.fontFamily) {
                                                        "Monospace" -> FontFamily.Monospace
                                                        "Serif" -> FontFamily.Serif
                                                        else -> FontFamily.Default
                                                    },
                                                    modifier = Modifier.padding(4.dp)
                                                )
                                            }
                                        }
                                        LayerType.STICKER -> {
                                            AsyncImage(
                                                model = layer.stickerUri,
                                                contentDescription = "Sticker image",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        LayerType.SHAPE -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(
                                                        if (layer.shapeType == ShapeType.ROUNDED_RECT) RoundedCornerShape(layer.shapeCornerRadius.dp)
                                                        else if (layer.shapeType == ShapeType.ELLIPSE) CircleShape
                                                        else RoundedCornerShape(0.dp)
                                                    )
                                                    .background(
                                                        if (layer.shapeColor2 != null) {
                                                            Brush.linearGradient(
                                                                colors = listOf(
                                                                    Color(android.graphics.Color.parseColor(layer.shapeColor1)),
                                                                    Color(android.graphics.Color.parseColor(layer.shapeColor2))
                                                                )
                                                            )
                                                        } else {
                                                            SolidColor(Color(android.graphics.Color.parseColor(layer.shapeColor1)))
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // GRID LINES
                        if (showGrid) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val colW = size.width / 3f
                                val rowH = size.height / 3f
                                // Rule of thirds grid lines
                                drawLine(Color.LightGray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(colW, 0f), end = androidx.compose.ui.geometry.Offset(colW, size.height), strokeWidth = 1f)
                                drawLine(Color.LightGray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(colW * 2f, 0f), end = androidx.compose.ui.geometry.Offset(colW * 2f, size.height), strokeWidth = 1f)
                                drawLine(Color.LightGray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(0f, rowH), end = androidx.compose.ui.geometry.Offset(size.width, rowH), strokeWidth = 1f)
                                drawLine(Color.LightGray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(0f, rowH * 2f), end = androidx.compose.ui.geometry.Offset(size.width, rowH * 2f), strokeWidth = 1f)
                            }
                        }

                        // SAFE AREA BORDER
                        if (showSafeArea) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Safe margin around edge
                                val margin = 16.dp.toPx()
                                drawRect(
                                    color = Color.Red.copy(alpha = 0.3f),
                                    style = Stroke(width = 1f),
                                    size = androidx.compose.ui.geometry.Size(size.width - margin * 2f, size.height - margin * 2f),
                                    topLeft = androidx.compose.ui.geometry.Offset(margin, margin)
                                )
                            }
                        }

                        // DRAW SNAPPING LINES
                        if (drawHGuide || drawVGuide) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (drawVGuide) {
                                    drawLine(
                                        Color.Yellow,
                                        start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                                        end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                                        strokeWidth = 2f
                                    )
                                }
                                if (drawHGuide) {
                                    drawLine(
                                        Color.Yellow,
                                        start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                                        strokeWidth = 2f
                                    )
                                }
                            }
                        }
                    }
                }

                // BOTTOM WORKSPACE TOOLBAR
                if (!isPreviewMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        // Property sheets panels based on activeTab
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(12.dp)
                        ) {
                            when (activeTab) {
                                "background" -> BackgroundTabPanel(project, viewModel, bgPickerLauncher = { bgPickerLauncher.launch("image/*") })
                                "text" -> TextTabPanel(project, selectedId, viewModel)
                                "sticker" -> StickerTabPanel(viewModel, stickerPickerLauncher = { stickerPickerLauncher.launch("image/*") })
                                "shapes" -> ShapesTabPanel(viewModel)
                                "layers" -> LayersTabPanel(project, selectedId, viewModel)
                                "filters" -> FiltersTabPanel(project, viewModel)
                            }
                        }

                        // Navigation tabs row
                        Divider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = listOf(
                                Triple("background", Icons.Default.Image, "Nền"),
                                Triple("text", Icons.Default.TextFields, "Chữ"),
                                Triple("sticker", Icons.Default.EmojiEmotions, "Nhãn"),
                                Triple("shapes", Icons.Default.Category, "Hình"),
                                Triple("layers", Icons.Default.Layers, "Lớp"),
                                Triple("filters", Icons.Default.Tune, "Bộ lọc")
                            )

                            tabs.forEach { (id, icon, label) ->
                                val active = activeTab == id
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { activeTab = id }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        tint = if (active) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        color = if (active) MaterialTheme.colorScheme.primary else Color.Gray,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FULLSCREEN PREVIEW OVERLAY
            if (isPreviewMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                ) {
                    IconButton(
                        onClick = { isPreviewMode = false },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.DarkGray.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
                    }

                    Text(
                        "Xem trước chất lượng cao (320x504)",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                    )

                    // Rendered Preview box
                    Box(
                        modifier = Modifier
                            .aspectRatio(320f / 504f)
                            .fillMaxHeight(0.75f)
                            .align(Alignment.Center)
                            .background(Color.White)
                    ) {
                        val bg = project.background
                        if (bg.uri.isNotEmpty()) {
                            AsyncImage(
                                model = bg.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = bg.x
                                        translationY = bg.y
                                        scaleX = bg.scale * (if (bg.isFlippedHorizontally) -1f else 1f)
                                        scaleY = bg.scale
                                        rotationZ = bg.rotation
                                    }
                            )
                        }

                        project.layers.forEach { layer ->
                            if (layer.isVisible) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = layer.x.dp, y = layer.y.dp)
                                        .size(layer.width.dp, layer.height.dp)
                                        .graphicsLayer {
                                            rotationZ = layer.rotation
                                            alpha = layer.opacity
                                        }
                                ) {
                                    when (layer.type) {
                                        LayerType.TEXT -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = layer.textContent,
                                                    fontSize = layer.fontSize.sp,
                                                    color = Color(android.graphics.Color.parseColor(layer.textColor)),
                                                    fontWeight = if (layer.isBold) FontWeight.Bold else FontWeight.Normal,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        LayerType.STICKER -> {
                                            AsyncImage(
                                                model = layer.stickerUri,
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        LayerType.SHAPE -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(android.graphics.Color.parseColor(layer.shapeColor1)))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7 STEPS APPLY POSTER VISUAL DIALOG
            if (applyState !is PosterEditorViewModel.ApplyState.Idle) {
                ApplyPosterProcessDialog(applyState, viewModel)
            }
        }
    }
}

@Composable
fun CheckerboardPattern() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sizePx = 10.dp.toPx()
        val cols = (size.width / sizePx).toInt() + 1
        val rows = (size.height / sizePx).toInt() + 1
        
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((r + c) % 2 == 0) {
                    drawRect(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        topLeft = androidx.compose.ui.geometry.Offset(c * sizePx, r * sizePx),
                        size = androidx.compose.ui.geometry.Size(sizePx, sizePx)
                    )
                }
            }
        }
    }
}

// TAB PANELS IMPLEMENTATIONS
@Composable
fun BackgroundTabPanel(
    project: Project,
    viewModel: PosterEditorViewModel,
    bgPickerLauncher: () -> Unit
) {
    val bg = project.background
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(100.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = bgPickerLauncher,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Chọn ảnh", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("Chọn ảnh nền", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        }

        VerticalDivider()

        if (bg.uri.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chọn một ảnh nền từ máy để bắt đầu tùy chỉnh.", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        viewModel.updateProjectState { current ->
                            current.copy(background = current.background.copy(scale = (current.background.scale + 0.1f).coerceAtMost(5f)))
                        }
                    }) { Icon(Icons.Default.ZoomIn, "Zoom In") }

                    IconButton(onClick = {
                        viewModel.updateProjectState { current ->
                            current.copy(background = current.background.copy(scale = (current.background.scale - 0.1f).coerceAtLeast(0.1f)))
                        }
                    }) { Icon(Icons.Default.ZoomOut, "Zoom Out") }

                    IconButton(onClick = {
                        viewModel.updateProjectState { current ->
                            current.copy(background = current.background.copy(rotation = (current.background.rotation + 90f) % 360f))
                        }
                    }) { Icon(Icons.Default.RotateRight, "Rotate 90") }

                    IconButton(onClick = {
                        viewModel.updateProjectState { current ->
                            current.copy(background = current.background.copy(isFlippedHorizontally = !current.background.isFlippedHorizontally))
                        }
                    }) { Icon(Icons.Default.Flip, "Mirror Horizontal") }

                    IconButton(onClick = {
                        viewModel.updateProjectState { current ->
                            current.copy(background = BackgroundTransform(uri = current.background.uri))
                        }
                    }) { Icon(Icons.Default.Restore, "Reset Background") }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text("Độ phân giải nguồn: ${if (bg.originalWidth > 0) "${bg.originalWidth}x${bg.originalHeight}" else "Đang tải..."}", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun TextTabPanel(
    project: Project,
    selectedId: String?,
    viewModel: PosterEditorViewModel
) {
    val selectedLayer = project.layers.find { it.id == selectedId && it.type == LayerType.TEXT }

    if (selectedLayer == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { viewModel.addTextLayer() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Thêm lớp chữ")
            }
            Text("Chọn hoặc thêm một lớp chữ để chỉnh sửa.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = selectedLayer.textContent,
                    onValueChange = { text ->
                        viewModel.updateProjectState { current ->
                            current.copy(
                                layers = current.layers.map {
                                    if (it.id == selectedLayer.id) it.copy(textContent = text) else it
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nội dung chữ") },
                    singleLine = false,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = {
                        viewModel.updateProjectState { current ->
                            current.copy(
                                layers = current.layers.map {
                                    if (it.id == selectedLayer.id) it.copy(fontSize = (it.fontSize - 2f).coerceAtLeast(8f)) else it
                                }
                            )
                        }
                    }) { Icon(Icons.Default.TextFormat, "Decrease Font Size") }
                    IconButton(onClick = {
                        viewModel.updateProjectState { current ->
                            current.copy(
                                layers = current.layers.map {
                                    if (it.id == selectedLayer.id) it.copy(fontSize = (it.fontSize + 2f).coerceAtMost(100f)) else it
                                }
                            )
                        }
                    }) { Icon(Icons.Default.TextFields, "Increase Font Size") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val colors = listOf("#FFFFFF", "#FFD700", "#FF0000", "#00FF00", "#0000FF", "#000000")
                    colors.take(3).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(1.dp, Color.Gray, CircleShape)
                                .clickable {
                                    viewModel.updateProjectState { current ->
                                        current.copy(
                                            layers = current.layers.map {
                                                if (it.id == selectedLayer.id) it.copy(textColor = hex) else it
                                            }
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StickerTabPanel(
    viewModel: PosterEditorViewModel,
    stickerPickerLauncher: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = stickerPickerLauncher) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Thêm Sticker từ điện thoại")
        }
        Text("Hỗ trợ ảnh trong suốt PNG, JPG, WEBP chất lượng cao.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ShapesTabPanel(
    viewModel: PosterEditorViewModel
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shapes = listOf(
            Pair(ShapeType.ROUNDED_RECT, "Bo góc"),
            Pair(ShapeType.ELLIPSE, "Hình tròn"),
            Pair(ShapeType.BANNER, "Banner"),
            Pair(ShapeType.GRADIENT_RECT, "Chuyển màu")
        )

        shapes.forEach { (type, label) ->
            Column(
                modifier = Modifier
                    .clickable { viewModel.addShapeLayer(type) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    when (type) {
                        ShapeType.ROUNDED_RECT -> Icons.Default.Crop32
                        ShapeType.ELLIPSE -> Icons.Default.RadioButtonUnchecked
                        ShapeType.BANNER -> Icons.Default.Bookmark
                        ShapeType.GRADIENT_RECT -> Icons.Default.Texture
                    },
                    contentDescription = label,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun LayersTabPanel(
    project: Project,
    selectedId: String?,
    viewModel: PosterEditorViewModel
) {
    if (project.layers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Không có lớp vẽ nào. Hãy thêm chữ hoặc sticker.", fontSize = 12.sp, color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(project.layers.reversed()) { index, layer ->
                val isSelected = layer.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                        .clickable { viewModel.selectLayer(layer.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (layer.type) {
                                LayerType.TEXT -> Icons.Default.TextFields
                                LayerType.STICKER -> Icons.Default.EmojiEmotions
                                LayerType.SHAPE -> Icons.Default.Category
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            layer.name,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(100.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            viewModel.updateProjectState { current ->
                                current.copy(
                                    layers = current.layers.map {
                                        if (it.id == layer.id) it.copy(isVisible = !it.isVisible) else it
                                    }
                                )
                            }
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Ẩn/Hiện",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(onClick = {
                            viewModel.updateProjectState { current ->
                                current.copy(
                                    layers = current.layers.map {
                                        if (it.id == layer.id) it.copy(isLocked = !it.isLocked) else it
                                    }
                                )
                            }
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Khóa",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (isSelected) {
                            IconButton(onClick = { viewModel.moveLayerZOrder(layer.id, "FORWARD") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Lên", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { viewModel.moveLayerZOrder(layer.id, "BACKWARD") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Xuống", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { viewModel.duplicateSelectedLayer() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Nhân đôi", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { viewModel.deleteSelectedLayer() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FiltersTabPanel(
    project: Project,
    viewModel: PosterEditorViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Độ sắc nét (Sharpen)", fontSize = 12.sp)
            Slider(
                value = project.filters.sharpen,
                onValueChange = { s ->
                    viewModel.updateProjectState { current ->
                        current.copy(filters = current.filters.copy(sharpen = s))
                    }
                },
                valueRange = 0f..0.6f,
                modifier = Modifier.width(180.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Độ sáng (Brightness)", fontSize = 12.sp)
            Slider(
                value = project.filters.brightness,
                onValueChange = { b ->
                    viewModel.updateProjectState { current ->
                        current.copy(filters = current.filters.copy(brightness = b))
                    }
                },
                valueRange = -0.5f..0.5f,
                modifier = Modifier.width(180.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chất lượng xuất hình", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Standard", "HQ Balanced", "HQ Strong").forEach { q ->
                    FilterChip(
                        selected = project.quality == q,
                        onClick = {
                            viewModel.updateProjectState { current -> current.copy(quality = q) }
                        },
                        label = { Text(q, fontSize = 10.sp) }
                    )
                }
            }
        }
    }
}

// 7-STEPS VISUAL PROCESS OVERLAY DIALOG
@Composable
fun ApplyPosterProcessDialog(
    state: PosterEditorViewModel.ApplyState,
    viewModel: PosterEditorViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ÁP DỤNG POSTER VÀO GAME",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Current Step Status indicator
                val currentStep = when (state) {
                    is PosterEditorViewModel.ApplyState.Running -> state.currentStep
                    is PosterEditorViewModel.ApplyState.Success -> 7
                    is PosterEditorViewModel.ApplyState.Failure -> state.error.step
                    else -> 1
                }

                val progress = when (state) {
                    is PosterEditorViewModel.ApplyState.Running -> state.progress
                    is PosterEditorViewModel.ApplyState.Success -> 1.0f
                    else -> 0f
                }

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // The 7 checklist items
                val steps = listOf(
                    "1. Kiểm tra phiên đăng nhập KGVN",
                    "2. Khởi tạo Poster ID chính thức",
                    "3. Kết xuất ảnh độ phân giải cao",
                    "4. Tải ảnh poster chuẩn lên Tencent COS",
                    "5. Tải ảnh poster lớn lên Tencent COS",
                    "6. Lưu và áp dụng vào tài khoản game",
                    "7. Lưu cấu trúc bố cục thiết kế"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEachIndexed { index, text ->
                        val stepNum = index + 1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = when {
                                    stepNum < currentStep -> Icons.Default.CheckCircle
                                    stepNum == currentStep && state is PosterEditorViewModel.ApplyState.Running -> Icons.Default.Loop
                                    stepNum == currentStep && state is PosterEditorViewModel.ApplyState.Failure -> Icons.Default.Cancel
                                    else -> Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = when {
                                    stepNum < currentStep -> Color(0xFF4CAF50)
                                    stepNum == currentStep && state is PosterEditorViewModel.ApplyState.Running -> MaterialTheme.colorScheme.primary
                                    stepNum == currentStep && state is PosterEditorViewModel.ApplyState.Failure -> MaterialTheme.colorScheme.error
                                    else -> Color.Gray
                                },
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(if (stepNum == currentStep && state is PosterEditorViewModel.ApplyState.Running) progress * 360f else 0f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text,
                                fontSize = 13.sp,
                                fontWeight = if (stepNum == currentStep) FontWeight.Bold else FontWeight.Normal,
                                color = if (stepNum == currentStep) MaterialTheme.colorScheme.onSurface else Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Log or status message
                when (state) {
                    is PosterEditorViewModel.ApplyState.Running -> {
                        Text(
                            state.statusMessage,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    is PosterEditorViewModel.ApplyState.Success -> {
                        Text(
                            "Chúc mừng! Poster đã được lưu, xuất và áp dụng thành công vào tài khoản game của bạn!",
                            fontSize = 13.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.resetApplyState() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.testTag("apply_success_close")
                        ) {
                            Text("Hoàn thành")
                        }
                    }
                    is PosterEditorViewModel.ApplyState.Failure -> {
                        val err = state.error
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Lỗi: ${err.title}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                err.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (err.retryable) {
                                    Button(
                                        onClick = { viewModel.applyPoster(context) },
                                        modifier = Modifier.testTag("apply_retry_button")
                                    ) {
                                        Text("Thử lại")
                                    }
                                }
                                TextButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(err.sanitizedDetails))
                                    Toast.makeText(context, "Đã sao chép báo cáo lỗi đã ẩn mật khẩu", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Sao chép báo cáo", fontSize = 12.sp)
                                }
                                TextButton(onClick = { viewModel.resetApplyState() }) {
                                    Text("Hủy", color = Color.Gray)
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
