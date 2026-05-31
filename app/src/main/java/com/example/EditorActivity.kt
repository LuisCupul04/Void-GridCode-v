package com.example

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ComponentType {
    TEXT, IMAGE, VIDEO, AUDIO
}

data class WebComponent(
    val id: String = UUID.randomUUID().toString(),
    val type: ComponentType,
    var content: String,
    var xPercent: Float = 50f, // vertical (Slider X)
    var yPercent: Float = 50f, // horizontal (Slider Y)
    var isLocked: Boolean = false,
    var widthEstimate: Dp = 130.dp,
    var heightEstimate: Dp = 44.dp
)

class EditorActivity : ComponentActivity() {
    private val isLandscapeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        val projectName = intent.getStringExtra("PROJECT_NAME") ?: "Sin_Nombre"
        isLandscapeState.value = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        setContent {
            MyApplicationTheme {
                EditorScreen(
                    projectName = projectName,
                    isDeviceLandscape = isLandscapeState.value,
                    onBackPressed = {
                        finish()
                    }
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemUI()
        isLandscapeState.value = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
}

@Composable
fun EditorScreen(projectName: String, isDeviceLandscape: Boolean = false, onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val projectFolder = remember { File(File(context.filesDir, "projects"), projectName) }
    val dataFile = remember { File(projectFolder, "no_code_data.json") }

    // Component configurations state
    var components by remember { mutableStateOf(emptyList<WebComponent>()) }
    var selectedComponentId by remember { mutableStateOf<String?>(null) }
    
    // Viewport layout states
    var isVerticalLayout by remember { mutableStateOf(true) } // true: Portrait (9:16), false: Landscape (16:9)
    var isBottomPanelVisible by remember { mutableStateOf(true) }
    var showPreviewWebView by remember { mutableStateOf(false) }

    // Dynamic dialogue controls
    var showAddComponentDialog by remember { mutableStateOf<ComponentType?>(null) }
    var componentInputText by remember { mutableStateOf("") }

    // Helpers to save state to local config file (no_code_data.json)
    fun saveProjectState() {
        try {
            val rootObj = JSONObject()
            val array = JSONArray()
            for (comp in components) {
                val obj = JSONObject().apply {
                    put("id", comp.id)
                    put("type", comp.type.name)
                    put("content", comp.content)
                    put("xPercent", comp.xPercent.toDouble())
                    put("yPercent", comp.yPercent.toDouble())
                    put("isLocked", comp.isLocked)
                    put("widthEstimate", comp.widthEstimate.value.toDouble())
                    put("heightEstimate", comp.heightEstimate.value.toDouble())
                }
                array.put(obj)
            }
            rootObj.put("components", array)
            dataFile.writeText(rootObj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Load initial project config from json
    fun loadProjectState() {
        if (dataFile.exists()) {
            try {
                val jsonString = dataFile.readText()
                val rootObj = JSONObject(jsonString)
                val array = rootObj.getJSONArray("components")
                val loadedList = mutableListOf<WebComponent>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedList.add(
                        WebComponent(
                            id = obj.getString("id"),
                            type = ComponentType.valueOf(obj.getString("type")),
                            content = obj.getString("content"),
                            xPercent = obj.optDouble("xPercent", 50.0).toFloat(),
                            yPercent = obj.optDouble("yPercent", 50.0).toFloat(),
                            isLocked = obj.optBoolean("isLocked", false),
                            widthEstimate = obj.optDouble("widthEstimate", 130.0).toFloat().dp,
                            heightEstimate = obj.optDouble("heightEstimate", 44.0).toFloat().dp
                        )
                    )
                }
                components = loadedList
                if (components.isNotEmpty()) {
                    selectedComponentId = components.first().id
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fail-safe default components
                components = listOf(
                    WebComponent(type = ComponentType.TEXT, content = "Maqueta Web No-Code")
                )
            }
        } else {
            // First mock item
            components = listOf(
                WebComponent(type = ComponentType.TEXT, content = "Maqueta Web No-Code")
            )
            saveProjectState()
        }
    }

    LaunchedEffect(Unit) {
        loadProjectState()
    }

    // Mathematical calculations for aligning the index.html on ZIP compile
    fun updateIndexHtml() {
        try {
            val indexFile = File(projectFolder, "index.html")
            val htmlBuilder = StringBuilder()
            
            htmlBuilder.append("""
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>$projectName - NoCode Builder</title>
                    <link rel="stylesheet" href="css/estilos.css">
                </head>
                <body>
                    <!-- Container Simulation -->
                    <main id="app">
            """.trimIndent())

            for (comp in components) {
                // translate(-50%, -50%) centers mathematically matching our exact canvas centering formula
                val styleString = "position: absolute; left: ${comp.yPercent}%; top: ${comp.xPercent}%; transform: translate(-50%, -50%);"
                
                when (comp.type) {
                    ComponentType.TEXT -> {
                        htmlBuilder.append("\n        <div class=\"text-comp\" style=\"$styleString text-shadow: 0 0 8px var(--color-morado); font-family: sans-serif;\">${comp.content}</div>")
                    }
                    ComponentType.IMAGE -> {
                        htmlBuilder.append("\n        <img class=\"img-comp\" src=\"${comp.content}\" style=\"$styleString width: 140px; height: auto;\" alt=\"Graphic Asset\">")
                    }
                    ComponentType.VIDEO -> {
                        htmlBuilder.append("\n        <video class=\"video-comp\" src=\"${comp.content}\" controls style=\"$styleString width: 180px; height: 110px;\"></video>")
                    }
                    ComponentType.AUDIO -> {
                        htmlBuilder.append("\n        <audio class=\"audio-comp\" src=\"${comp.content}\" controls style=\"$styleString width: 160px; height: 36px;\"></audio>")
                    }
                }
            }

            htmlBuilder.append("""
                
                    </main>
                </body>
                </html>
            """.trimIndent())

            indexFile.writeText(htmlBuilder.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Zip execution helper
    fun zipProjectFolder(folderToZip: File, zipOutputFile: File) {
        ZipOutputStream(FileOutputStream(zipOutputFile)).use { zipOut ->
            fun addDirToZip(baseDir: File, currentFile: File) {
                val files = currentFile.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory) {
                        addDirToZip(baseDir, file)
                    } else {
                        // Keep relative path mapping
                        val entryName = file.absolutePath.substring(baseDir.absolutePath.length + 1)
                        val zipEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(zipEntry)
                        
                        FileInputStream(file).use { input ->
                            val buffer = ByteArray(1024)
                            var len: Int
                            while (input.read(buffer).also { len = it } > 0) {
                                zipOut.write(buffer, 0, len)
                            }
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            addDirToZip(folderToZip, folderToZip)
        }
    }

    // Trigger complete compile & ZIP package deployment share list
    fun handleZipExport() {
        try {
            // First force save state and update index.html html output file
            saveProjectState()
            updateIndexHtml()

            val zipOutDir = File(context.cacheDir, "exports")
            if (!zipOutDir.exists()) zipOutDir.mkdirs()
            val zipFile = File(zipOutDir, "$projectName.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }

            // Run compression
            zipProjectFolder(projectFolder, zipFile)

            // Trigger sharesheet share Intent in modern Android compliant format
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/zip"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Descargar y Guardar Proyecto Web (.ZIP)"))
            Toast.makeText(context, "¡Proyecto compiling completado con éxito!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Error al compilar ZIP: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // Retrieve active selected component
    val selectedComponent = components.find { it.id == selectedComponentId }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        if (isDeviceLandscape) {
            // Landscape Lateral Bento Partition
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Top Bar + Canvas
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                ) {
                    // Top Bar (more compact: 44.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Zinc900.copy(alpha = 0.5f))
                            .border(BorderStroke(1.dp, Zinc800))
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable {
                                saveProjectState()
                                onBackPressed()
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Salir", tint = Cyan400, modifier = Modifier.size(16.dp))
                            Text(
                                text = "NC: $projectName".uppercase(),
                                color = Cyan400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                saveProjectState()
                                updateIndexHtml()
                                showPreviewWebView = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                            border = BorderStroke(1.dp, Purple500.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Previsualizar", tint = Purple500, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(PureBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(0.95f)
                                .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                                .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x332563EB))
                                .testTag("design_canvas")
                        ) {
                            CanvasGridAndContent(
                                components = components,
                                selectedComponentId = selectedComponentId,
                                onSelectComponent = { id -> selectedComponentId = id }
                            )
                        }
                    }

                    // Compile Button at bottom
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { handleZipExport() },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                            border = BorderStroke(1.dp, Color(0x66C299FA)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.width(220.dp).height(34.dp).testTag("compile_zip_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Compilar", tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COMPILAR Y DESCARGAR WEB (.ZIP)", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Right Column: Bento Panel Controls
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .background(Zinc900)
                        .border(BorderStroke(1.dp, Zinc800))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "PANEL BENTO DE CONTROLES",
                        color = NeonPurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("INSERTAR CAPA", color = Zinc400, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("TT", "TEXTO", ComponentType.TEXT),
                                Triple("📷", "IMAGEN", ComponentType.IMAGE),
                                Triple("📹", "VIDEO", ComponentType.VIDEO),
                                Triple("🔊", "AUDIO", ComponentType.AUDIO)
                            ).forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .background(Zinc800, RoundedCornerShape(10.dp))
                                        .border(BorderStroke(1.dp, Zinc700), RoundedCornerShape(10.dp))
                                        .clickable {
                                            componentInputText = when(item.third) {
                                                ComponentType.TEXT -> "Encabezado Web"
                                                ComponentType.IMAGE -> "https://picsum.photos/300/200"
                                                ComponentType.VIDEO -> "https://www.w3schools.com/html/mov_bbb.mp4"
                                                ComponentType.AUDIO -> "https://www.w3schools.com/html/horse.mp3"
                                            }
                                            showAddComponentDialog = item.third
                                        }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = item.first, color = Cyan400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(text = item.second, color = Zinc500, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Zinc800)

                        if (selectedComponent != null) {
                            val isLocked = selectedComponent.isLocked

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "X: ${selectedComponent.xPercent.toInt()}% | Y: ${selectedComponent.yPercent.toInt()}%",
                                    color = if (isLocked) Zinc500 else Cyan400,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                Row(
                                    modifier = Modifier
                                        .background(Zinc800, RoundedCornerShape(6.dp))
                                        .clickable {
                                            selectedComponent.isLocked = !selectedComponent.isLocked
                                            components = components.toList()
                                            saveProjectState()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isLocked) "🔓 DESTRABAR" else "🔒 FIJAR",
                                        color = if (isLocked) Color.Red else Zinc400,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Slider(
                                    value = selectedComponent.yPercent,
                                    onValueChange = {
                                        if (!isLocked) {
                                            selectedComponent.yPercent = it
                                            components = components.toList()
                                        }
                                    },
                                    onValueChangeFinished = { saveProjectState() },
                                    valueRange = 0f..100f,
                                    enabled = !isLocked,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Cyan400,
                                        activeTrackColor = Cyan400,
                                        inactiveTrackColor = Zinc800
                                    ),
                                    modifier = Modifier.height(24.dp).testTag("slider_y")
                                )

                                Slider(
                                    value = selectedComponent.xPercent,
                                    onValueChange = {
                                        if (!isLocked) {
                                            selectedComponent.xPercent = it
                                            components = components.toList()
                                        }
                                    },
                                    onValueChangeFinished = { saveProjectState() },
                                    valueRange = 0f..100f,
                                    enabled = !isLocked,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Purple500,
                                        activeTrackColor = Purple500,
                                        inactiveTrackColor = Zinc800
                                    ),
                                    modifier = Modifier.height(24.dp).testTag("slider_x")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(0.55f)
                                        .background(Zinc800.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (!isLocked) {
                                                selectedComponent.yPercent = (selectedComponent.yPercent - 0.5f).coerceIn(0f, 100f)
                                                components = components.toList()
                                                saveProjectState()
                                            }
                                        },
                                        enabled = !isLocked,
                                        modifier = Modifier.size(24.dp).testTag("mini_left")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Izq", tint = if (isLocked) Zinc500 else Zinc100, modifier = Modifier.size(16.dp))
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconButton(
                                            onClick = {
                                                if (!isLocked) {
                                                    selectedComponent.xPercent = (selectedComponent.xPercent - 0.5f).coerceIn(0f, 100f)
                                                    components = components.toList()
                                                    saveProjectState()
                                                }
                                            },
                                            enabled = !isLocked,
                                            modifier = Modifier.size(24.dp).testTag("mini_up")
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Arr", tint = if (isLocked) Zinc500 else Zinc100, modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                if (!isLocked) {
                                                    selectedComponent.xPercent = (selectedComponent.xPercent + 0.5f).coerceIn(0f, 100f)
                                                    components = components.toList()
                                                    saveProjectState()
                                                }
                                            },
                                            enabled = !isLocked,
                                            modifier = Modifier.size(24.dp).testTag("mini_down")
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Abj", tint = if (isLocked) Zinc500 else Zinc100, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (!isLocked) {
                                                selectedComponent.yPercent = (selectedComponent.yPercent + 0.5f).coerceIn(0f, 100f)
                                                components = components.toList()
                                                saveProjectState()
                                            }
                                        },
                                        enabled = !isLocked,
                                        modifier = Modifier.size(24.dp).testTag("mini_right")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Der", tint = if (isLocked) Zinc500 else Zinc100, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Button(
                                    onClick = {
                                        components = components.filter { it.id != selectedComponentId }
                                        selectedComponentId = components.firstOrNull()?.id
                                        saveProjectState()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(0.45f).height(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Borrar", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                Text("Sin capa seleccionada", color = Zinc500, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        } else {
            // Main Screen Interface
            Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. TOP BAR TOOLBAR - Fixed at top always
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Zinc900.copy(alpha = 0.5f))
                    .border(BorderStroke(1.dp, Zinc800))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back, "NC" branding logo, and Project label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable {
                        saveProjectState()
                        onBackPressed()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Salir",
                        tint = Cyan400
                    )
                    
                    // Brand Logo Badge "NC" with Gradient Backdrop
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Purple600, Cyan400))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NC",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column {
                        Text(
                            text = "PROYECTO ACTUAL",
                            color = Zinc400,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "v3.0 - $projectName".uppercase(),
                            color = Cyan400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Interactive upper buttons styled with modern responsive containers
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "🔄 Rotación de Hoja" Button
                    Button(
                        onClick = { isVerticalLayout = !isVerticalLayout },
                        colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                        border = BorderStroke(1.dp, Cyan400.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("rotate_canvas_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rotar",
                            tint = Cyan400,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVerticalLayout) "Vista Horizontal" else "Vista Vertical",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // "▶️ Proyecto en Línea" Webview Button
                    Button(
                        onClick = {
                            saveProjectState()
                            updateIndexHtml()
                            showPreviewWebView = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                        border = BorderStroke(1.dp, Purple500.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("preview_online_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Previsualizar",
                            tint = Purple500,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Proyecto en Línea",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. CENTRAL WORKSPACE - Floats and expands based on bottom panel height simulation
            val animatedOffset by animateDpAsState(
                targetValue = if (isBottomPanelVisible) 180.dp else 0.dp,
                label = "bottomPanelHeight"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount.y > 20) {
                                // Swipe down action detected
                                isBottomPanelVisible = false
                            } else if (dragAmount.y < -20) {
                                // Swipe up action detected
                                isBottomPanelVisible = true
                            }
                        }
                    }
                    .background(PureBlack)
                    .drawBehind {
                        // Drawing majestic radial grid dots (#1a1a1a -> Color(0xFF1E1E1E) spaced 20dp)
                        val dotColor = Color(0xFF1C1C1E)
                        val spacingPx = 20.dp.toPx()
                        var currentX = 0f
                        while (currentX < size.width) {
                            var currentY = 0f
                            while (currentY < size.height) {
                                drawCircle(
                                    color = dotColor,
                                    radius = 1.dp.toPx(),
                                    center = Offset(currentX, currentY)
                                )
                                currentY += spacingPx
                            }
                            currentX += spacingPx
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    
                    // Design Blue Simulated Canvas Workspace with premium shadow outline and RoundedCornerShape(24.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.9f)
                            .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                            .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0x332563EB)) // Translucent Blueprint Blue bg-blue-700/20
                            .testTag("design_canvas")
                    ) {
                        CanvasGridAndContent(
                            components = components,
                            selectedComponentId = selectedComponentId,
                            onSelectComponent = { id -> selectedComponentId = id }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // "COMPILAR Y DESCARGAR WEB (.ZIP)" Button: Framed perfectly below the canvas with high fidelity capsule shape
                    Button(
                        onClick = { handleZipExport() },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                        border = BorderStroke(1.dp, Color(0x66C299FA)),
                        shape = RoundedCornerShape(24.dp), // pill/capsule shape-full
                        modifier = Modifier
                            .width(260.dp)
                            .height(42.dp)
                            .testTag("compile_zip_button"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compilar",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "COMPILAR Y DESCARGAR (.ZIP)",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Drag Pull Toggle Tab Handle at the bottom center to slide up/down controles
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .background(Color.Black.copy(0.7f), RoundedCornerShape(20.dp))
                        .border(1.dp, if (isBottomPanelVisible) Purple600 else Cyan400, RoundedCornerShape(20.dp))
                        .clickable { isBottomPanelVisible = !isBottomPanelVisible }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBottomPanelVisible) "▼ CONTINUAR A PANTALLA COMPLETA" else "▲ VER CONTROLES BENTO",
                        color = if (isBottomPanelVisible) Purple600 else Cyan400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 3. BENTO PANEL CONTROL TAB SHEET - Dynamic Animated visibility
            AnimatedVisibility(
                visible = isBottomPanelVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                // Outer Bento Box Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * 0.40f) // strict restriction (max 40% screen height)
                        .background(Zinc900, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .border(BorderStroke(1.dp, Zinc800), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("bento_control_panel")
                ) {
                    // Pull Handle Drag Line
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(4.dp)
                            .background(Zinc800, RoundedCornerShape(100))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal scrollable Component Tray
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. TEXT Button
                        Box(
                            modifier = Modifier
                                .size(width = 82.dp, height = 72.dp)
                                .background(Zinc800, RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, Zinc700), RoundedCornerShape(16.dp))
                                .clickable {
                                    componentInputText = "Encabezado Web"
                                    showAddComponentDialog = ComponentType.TEXT
                                }
                                .testTag("add_text_button")
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TT",
                                    color = Cyan400,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "TEXTO",
                                    color = Zinc500,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 2. IMAGE Button
                        Box(
                            modifier = Modifier
                                .size(width = 82.dp, height = 72.dp)
                                .background(Zinc800, RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, Zinc700), RoundedCornerShape(16.dp))
                                .clickable {
                                    componentInputText = "https://picsum.photos/300/200"
                                    showAddComponentDialog = ComponentType.IMAGE
                                }
                                .testTag("add_image_button")
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Imagen",
                                    tint = Cyan400,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "IMAGEN",
                                    color = Zinc500,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 3. VIDEO Button
                        Box(
                            modifier = Modifier
                                .size(width = 82.dp, height = 72.dp)
                                .background(Zinc800, RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, Zinc700), RoundedCornerShape(16.dp))
                                .clickable {
                                    componentInputText = "https://www.w3schools.com/html/mov_bbb.mp4"
                                    showAddComponentDialog = ComponentType.VIDEO
                                }
                                .testTag("add_video_button")
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Cyan400,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "VIDEO",
                                    color = Zinc500,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 4. AUDIO Button
                        Box(
                            modifier = Modifier
                                .size(width = 82.dp, height = 72.dp)
                                .background(Zinc800, RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, Zinc700), RoundedCornerShape(16.dp))
                                .clickable {
                                    componentInputText = "https://www.w3schools.com/html/horse.mp3"
                                    showAddComponentDialog = ComponentType.AUDIO
                                }
                                .testTag("add_audio_button")
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Audio",
                                    tint = Cyan400,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "AUDIO",
                                    color = Zinc500,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Separation Divider line
                    Divider(color = Zinc800, modifier = Modifier.padding(vertical = 4.dp))

                    // Main precision coordinate controllers + directional keypad
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Section: Coordinates and Locks
                        Column(
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (selectedComponent != null) {
                                val isLocked = selectedComponent.isLocked

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "X (Hz): ${selectedComponent.xPercent.toInt()}%",
                                            color = if (isLocked) Zinc500 else Zinc400,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Y (Vt): ${selectedComponent.yPercent.toInt()}%",
                                            color = if (isLocked) Zinc500 else Cyan400,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Slider(
                                        value = selectedComponent.yPercent,
                                        onValueChange = {
                                            if (!isLocked) {
                                                selectedComponent.yPercent = it
                                                components = components.toList()
                                            }
                                        },
                                        onValueChangeFinished = { saveProjectState() },
                                        valueRange = 0f..100f,
                                        enabled = !isLocked,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Cyan400,
                                            activeTrackColor = Cyan400,
                                            inactiveTrackColor = Zinc800
                                        ),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("slider_y")
                                    )

                                    Slider(
                                        value = selectedComponent.xPercent,
                                        onValueChange = {
                                            if (!isLocked) {
                                                selectedComponent.xPercent = it
                                                components = components.toList()
                                            }
                                        },
                                        onValueChangeFinished = { saveProjectState() },
                                        valueRange = 0f..100f,
                                        enabled = !isLocked,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Purple500,
                                            activeTrackColor = Purple500,
                                            inactiveTrackColor = Zinc800
                                        ),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("slider_x")
                                    )
                                }

                                // Interactive lock bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .background(Zinc800.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.dp, Zinc700), RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedComponent.isLocked = !selectedComponent.isLocked
                                            components = components.toList()
                                            saveProjectState()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔒 FIJAR ELEMENTO",
                                        color = if (isLocked) Color.Red else Zinc400,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 30.dp, height = 16.dp)
                                            .background(if (isLocked) Purple600 else Zinc700, RoundedCornerShape(100.dp))
                                            .padding(2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(Color.White, RoundedCornerShape(100))
                                                .align(if (isLocked) Alignment.CenterEnd else Alignment.CenterStart)
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Selecciona una capa", color = Zinc500, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        VerticalDivider(color = Zinc800, modifier = Modifier.padding(vertical = 4.dp))

                        // Right Section: Joypad movement precision
                        Column(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (selectedComponent != null) {
                                val isLocked = selectedComponent.isLocked

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Zinc800.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Up arrow
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Zinc700, RoundedCornerShape(6.dp))
                                                .clickable(enabled = !isLocked) {
                                                    selectedComponent.xPercent = (selectedComponent.xPercent - 0.5f).coerceIn(0f, 100f)
                                                    components = components.toList()
                                                    saveProjectState()
                                                }
                                                .testTag("mini_up"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Arriba", tint = if (isLocked) Zinc500 else Zinc100, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    // Left & Right with center blinking dot
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Zinc700, RoundedCornerShape(6.dp))
                                                .clickable(enabled = !isLocked) {
                                                    selectedComponent.yPercent = (selectedComponent.yPercent - 0.5f).coerceIn(0f, 100f)
                                                    components = components.toList()
                                                    saveProjectState()
                                                }
                                                .testTag("mini_left"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Izquierda", tint = if (isLocked) Zinc500 else Zinc100, modifier = Modifier.size(18.dp))
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(if (isLocked) Color.Red else Cyan400, RoundedCornerShape(100))
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Zinc700, RoundedCornerShape(6.dp))
                                                .clickable(enabled = !isLocked) {
                                                    selectedComponent.yPercent = (selectedComponent.yPercent + 0.5f).coerceIn(0f, 100f)
                                                    components = components.toList()
                                                    saveProjectState()
                                                }
                                                .testTag("mini_right"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Derecha", tint = if (isLocked) Zinc500 else Zinc100, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    // Down arrow
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Zinc700, RoundedCornerShape(6.dp))
                                                .clickable(enabled = !isLocked) {
                                                    selectedComponent.xPercent = (selectedComponent.xPercent + 0.5f).coerceIn(0f, 100f)
                                                    components = components.toList()
                                                    saveProjectState()
                                                }
                                                .testTag("mini_down"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Abajo", tint = if (isLocked) Zinc500 else Zinc100, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            } else {
                                Text("Sin selección", color = Zinc500, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Modal popup helper used when configuring url or text contents on new component insertion
    if (showAddComponentDialog != null) {
        val activeType = showAddComponentDialog!!
        Dialog(onDismissRequest = { showAddComponentDialog = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .border(2.dp, NeonCyan, RoundedCornerShape(16.dp))
                    .testTag("add_component_dialog"),
                shape = RoundedCornerShape(16.dp),
                color = PureBlack
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "CONFIGURAR ${activeType.name}",
                        color = NeonCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    OutlinedTextField(
                        value = componentInputText,
                        onValueChange = { componentInputText = it },
                        label = { Text(if (activeType == ComponentType.TEXT) "Texto" else "URL de origen") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = NeonPurple,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = NeonPurple
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("component_content_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddComponentDialog = null }) {
                            Text("Cancelar", color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                val textVal = componentInputText.trim()
                                if (textVal.isNotEmpty()) {
                                    val newComp = WebComponent(
                                        type = activeType,
                                        content = textVal,
                                        // Auto adjust standard layout shapes depending on media dimensions
                                        widthEstimate = when(activeType) {
                                            ComponentType.TEXT -> 130.dp
                                            ComponentType.IMAGE -> 110.dp
                                            ComponentType.VIDEO -> 140.dp
                                            ComponentType.AUDIO -> 120.dp
                                        },
                                        heightEstimate = when(activeType) {
                                            ComponentType.TEXT -> 32.dp
                                            ComponentType.IMAGE -> 64.dp
                                            ComponentType.VIDEO -> 80.dp
                                            ComponentType.AUDIO -> 28.dp
                                        }
                                    )
                                    components = components + newComp
                                    selectedComponentId = newComp.id
                                    saveProjectState()
                                    showAddComponentDialog = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Text("Insertar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Full screen Overlay containing a live HTML preview (WebView Client)
    if (showPreviewWebView) {
        val indexHtmlFile = File(projectFolder, "index.html")
        Dialog(onDismissRequest = { showPreviewWebView = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.95f)
                    .border(2.dp, NeonCyan, RoundedCornerShape(12.dp))
                    .testTag("webview_dialog"),
                shape = RoundedCornerShape(12.dp),
                color = PureBlack
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkGrey)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "▶️ COMPILACIÓN LIVE PREVIEW",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        IconButton(
                            onClick = { showPreviewWebView = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Red)
                        }
                    }

                    // Simulated WebView Client rendering raw HTML5 index.html output
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.White),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                webViewClient = WebViewClient()
                                loadUrl("file://${indexHtmlFile.absolutePath}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CanvasGridAndContent(
    components: List<WebComponent>,
    selectedComponentId: String?,
    onSelectComponent: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current

        // Math Mesh lines & crossed dotted lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val gridSpacing = 20f

            // 1. Grid lines drawing
            // Horizontals
            var y = 0f
            while (y < canvasH) {
                drawLine(
                    color = Color(0x223B82F6),
                    start = Offset(0f, y),
                    end = Offset(canvasW, y),
                    strokeWidth = 1f
                )
                y += gridSpacing
            }
            // Verticals
            var x = 0f
            while (x < canvasW) {
                drawLine(
                    color = Color(0x223B82F6),
                    start = Offset(x, 0f),
                    end = Offset(x, canvasH),
                    strokeWidth = 1f
                )
                x += gridSpacing
            }

            // 2. crossing horizontal and vertical cross lines (cruz divisoria)
            // Vertical dotted line center
            drawLine(
                color = Color(0xFF60A5FA),
                start = Offset(canvasW / 2, 0f),
                end = Offset(canvasW / 2, canvasH),
                strokeWidth = 2.5f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
            // Horizontal dotted line center
            drawLine(
                color = Color(0xFF60A5FA),
                start = Offset(0f, canvasH / 2),
                end = Offset(canvasW, canvasH / 2),
                strokeWidth = 2.5f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }

        // Web added components layout with responsive placement
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxWidthPx = constraints.maxWidth.toFloat()
            val maxHeightPx = constraints.maxHeight.toFloat()

            for (comp in components) {
                val isCompSelected = comp.id == selectedComponentId

                // Back end Centering Geometric Formula calculation
                val compW = with(density) { comp.widthEstimate.toPx() }
                val compH = with(density) { comp.heightEstimate.toPx() }

                // formula: relative percent position * total size - half of element width/height
                val leftOffsetPx = (comp.yPercent / 100f) * maxWidthPx - (compW / 2f)
                val topOffsetPx = (comp.xPercent / 100f) * maxHeightPx - (compH / 2f)

                // convert pixels back to DP to map properly in Compose positioning Modifier
                val leftOffsetDp = with(density) { leftOffsetPx.toDp() }
                val topOffsetDp = with(density) { topOffsetPx.toDp() }

                Box(
                    modifier = Modifier
                        .absoluteOffset(x = leftOffsetDp, y = topOffsetDp)
                        .size(width = comp.widthEstimate, height = comp.heightEstimate)
                        .border(
                            BorderStroke(
                                width = if (isCompSelected) 2.dp else 1.dp,
                                color = if (isCompSelected) Cyan400 else Zinc700
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(if (isCompSelected) Cyan500.copy(alpha = 0.15f) else PureBlack.copy(0.85f))
                        .clickable {
                            onSelectComponent(comp.id)
                        }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (comp.type) {
                        ComponentType.TEXT -> {
                            Text(
                                text = comp.content,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ComponentType.IMAGE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Img",
                                    tint = Cyan400,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Imagen",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        ComponentType.VIDEO -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "vid",
                                    tint = Purple500,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Video .mp4",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        ComponentType.AUDIO -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Aud",
                                    tint = Cyan400,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Audio MP3",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Selection corners (cian nodes)
                    if (isCompSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-3).dp, y = (-3).dp)
                                .size(6.dp)
                                .background(Cyan400, RoundedCornerShape(100))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-3).dp)
                                .size(6.dp)
                                .background(Cyan400, RoundedCornerShape(100))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = (-3).dp, y = 3.dp)
                                .size(6.dp)
                                .background(Cyan400, RoundedCornerShape(100))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp)
                                .size(6.dp)
                                .background(Cyan400, RoundedCornerShape(100))
                        )
                    }

                    // Locked icon indicator overlay
                    if (comp.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloqueado",
                            tint = Color.Red,
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }
    }
}
