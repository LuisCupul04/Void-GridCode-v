package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.DarkGrey
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PureBlack
import com.example.ui.theme.Purple600
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Zinc900
import com.example.ui.theme.Zinc800
import com.example.ui.theme.Zinc700
import com.example.ui.theme.Zinc500
import com.example.ui.theme.Zinc400
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            MyApplicationTheme {
                ProjectManagerScreen(
                    onBackPressed = {
                        finish()
                    }
                )
            }
        }
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

// Data class representing listed project info
data class ProjectItem(
    val name: String,
    val dateModified: String,
    val folder: File
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagerScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    var projectList by remember { mutableStateOf(emptyList<ProjectItem>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    // Read and refresh projects list from storage sandboxed projects folder
    fun loadProjects() {
        val projectsDir = File(context.filesDir, "projects")
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }
        val folders = projectsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        
        // Sort chronologically (oldest last or newest first; let's show newest first based on modified date)
        val sorted = folders.sortedByDescending { it.lastModified() }
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        projectList = sorted.map { folderFile ->
            ProjectItem(
                name = folderFile.name,
                dateModified = formatter.format(Date(folderFile.lastModified())),
                folder = folderFile
            )
        }
    }

    // Load folders on render
    LaunchedEffect(Unit) {
        loadProjects()
    }

    // Function to create folder and write template files
    fun handleCreateProject(name: String) {
        val cleanName = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        if (cleanName.isEmpty()) {
            Toast.makeText(context, "Nombre inválido", Toast.LENGTH_SHORT).show()
            return
        }

        val projectsDir = File(context.filesDir, "projects")
        val newProjDir = File(projectsDir, cleanName)

        if (newProjDir.exists()) {
            Toast.makeText(context, "El proyecto ya existe", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 1. Create main folder
            newProjDir.mkdirs()

            // 2. Create subfolders
            val cssDir = File(newProjDir, "css")
            cssDir.mkdirs()

            // 3. Write index.html ROOT template
            val indexHtmlFile = File(newProjDir, "index.html")
            val htmlContent = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Proyecto No-Code: $cleanName</title>
                    <link rel="stylesheet" href="css/estilos.css">
                </head>
                <body>
                    <!-- No-Code Generated Layout Container -->
                    <main id="app">
                    </main>
                </body>
                </html>
            """.trimIndent()
            indexHtmlFile.writeText(htmlContent)

            // 4. Write CSS variables standard template
            val estilosCssFile = File(cssDir, "estilos.css")
            val cssContent = """
                :root {
                    --fondo-oscuro: #000000;
                    --color-morado: #bd00ff;
                    --color-cian: #00f0ff;
                    --color-texto: #ffffff;
                }
                body {
                    background-color: var(--fondo-oscuro);
                    color: var(--color-texto);
                    font-family: 'Segoe UI', Arial, sans-serif;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    width: 100vw;
                    height: 100vh;
                }
                #app {
                    position: relative;
                    width: 100%;
                    height: 100%;
                }
                .text-comp {
                    color: var(--color-texto);
                    font-weight: bold;
                    font-size: 1.2rem;
                }
                .img-comp {
                    border: 2px solid var(--color-cian);
                    border-radius: 8px;
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                }
                .video-comp, .audio-comp {
                    border: 2px solid var(--color-morado);
                    border-radius: 8px;
                    background: #111;
                }
            """.trimIndent()
            estilosCssFile.writeText(cssContent)

            // Refresh project view
            loadProjects()
            showCreateDialog = false
            newProjectName = ""

            // Navigate to third editor activity directly
            val intent = Intent(context, EditorActivity::class.java).apply {
                putExtra("PROJECT_NAME", cleanName)
            }
            context.startActivity(intent)

        } catch (e: Exception) {
            Toast.makeText(context, "Error al crear proyecto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to delete a project folder
    fun handleDeleteProject(project: ProjectItem) {
        try {
            project.folder.deleteRecursively()
            loadProjects()
            Toast.makeText(context, "Proyecto eliminado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        // Left Column: Control panel (Title, stats & ➕ Button)
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { onBackPressed() }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Retroceder",
                    tint = Cyan400
                )
                Text(
                    text = "Gestián de Proyectos",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "Crea y administra tus maquetas webs de forma local con aislamiento de archivos.",
                color = Zinc400,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                shape = RoundedCornerShape(24.dp), // modern pill shape
                border = BorderStroke(1.dp, Color(0x33C299FA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("create_project_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Crear",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Nuevo Proyecto",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right Column: Scaled Vertical Project List (RecyclerView analog)
        Box(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .border(BorderStroke(1.dp, Zinc800), RoundedCornerShape(16.dp))
                .background(Zinc900, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (projectList.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Sin proyectos",
                        tint = Cyan400,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Aún no has creado ningún proyecto",
                        color = Zinc500,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Presiona 'Nuevo Proyecto' para iniciar",
                        color = Zinc500,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .testTag("projects_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(projectList) { project ->
                        Card(
                            onClick = {
                                // Double target check before opening
                                val intent = Intent(context, EditorActivity::class.java).apply {
                                    putExtra("PROJECT_NAME", project.name)
                                }
                                context.startActivity(intent)
                            },
                            colors = CardDefaults.cardColors(containerColor = PureBlack),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Zinc800),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("project_item_${project.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.75f)) {
                                    Text(
                                        text = project.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Creación: ${project.dateModified}",
                                        color = Cyan400,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                IconButton(
                                    onClick = { handleDeleteProject(project) },
                                    modifier = Modifier.testTag("delete_project_${project.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar Proyecto",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Create Project
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .border(BorderStroke(1.dp, Purple600), RoundedCornerShape(24.dp))
                    .testTag("create_project_dialog"),
                shape = RoundedCornerShape(24.dp),
                color = Zinc900
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "NUEVO PROYECTO WEB",
                        color = Purple600,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )

                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Nombre del Proyecto") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400,
                            unfocusedBorderColor = Zinc700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Cyan400,
                            unfocusedLabelColor = Zinc500
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_project_edittext")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showCreateDialog = false
                                newProjectName = ""
                            },
                            modifier = Modifier.testTag("cancel_project_creation")
                        ) {
                            Text("Cancelar", color = Zinc500, fontFamily = FontFamily.Monospace)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { handleCreateProject(newProjectName) },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("confirm_project_creation")
                        ) {
                            Text("Crear", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}


