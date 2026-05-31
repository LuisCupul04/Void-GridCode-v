package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PureBlack
import com.example.ui.theme.DarkGrey
import com.example.ui.theme.Zinc100
import com.example.ui.theme.Purple600
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Zinc900
import com.example.ui.theme.Zinc800
import com.example.ui.theme.Zinc700
import com.example.ui.theme.Zinc500
import com.example.ui.theme.Zinc400

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()
        
        setContent {
            MyApplicationTheme {
                MainScreen()
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

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var showDocDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle background grid canvas styled as space-charcoal elements
        Canvas(modifier = Modifier.fillMaxSize()) {
            val columns = 36
            val rows = 18
            val colWidth = size.width / columns
            val rowHeight = size.height / rows
            for (i in 0..columns) {
                for (j in 0..rows) {
                    drawCircle(
                        color = Color(0x1A00F0FF),
                        radius = 1.dp.toPx(),
                        center = Offset(i * colWidth, j * rowHeight)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Neon Brand Artwork Logo
            WebCreatorLogo()

            // App Subtitle / Info
            Text(
                text = "v3.0 - Base Limpia",
                color = Cyan400,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("app_version_tag")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Button actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Iniciar Editor" Button
                Button(
                    onClick = {
                        val intent = Intent(context, ProjectManagerActivity::class.java)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Purple600
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0x33C299FA)),
                    modifier = Modifier
                        .height(52.dp)
                        .widthIn(min = 190.dp)
                        .testTag("start_editor_button")
                ) {
                    Text(
                        text = "Iniciar Editor",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // "Documentación" Button
                Button(
                    onClick = { showDocDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Zinc800
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Zinc700),
                    modifier = Modifier
                        .height(52.dp)
                        .widthIn(min = 190.dp)
                        .testTag("documentation_button")
                ) {
                    Text(
                        text = "Documentación",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // Documentation Custom Dialog
    if (showDocDialog) {
        Dialog(onDismissRequest = { showDocDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.85f)
                    .border(1.dp, Cyan400, RoundedCornerShape(24.dp))
                    .testTag("doc_dialog"),
                shape = RoundedCornerShape(24.dp),
                color = Zinc900
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Title
                    Text(
                        text = "DOCUMENTACIÓN NO-CODE WEB CREATOR",
                        color = Cyan400,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Guía rápida de la plataforma:",
                            color = Purple600,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = "1. GESTIÓN DE PROYECTOS: Crea proyectos web almacenados de forma local y 100% aislados en sandbox. Cada proyecto genera automáticamente 'index.html' y 'css/estilos.css' listos para producción.",
                            color = Zinc100,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Text(
                            text = "2. LIENZO CENTRAL (CANVAS): Simula una pantalla de teléfono celular. Soporta modo Vertical (9:16) y Horizontal (16:9). Se dibuja una cuadrícula de diseño con guías en cruz.",
                            color = Zinc100,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Text(
                            text = "3. PANEL TÁCTIL BENTO: Controla tus componentes de forma milimétrica. Desliza hacia abajo (Swipe Down) para ocultar el panel para trabajar a pantalla completa, y hacia arriba (Swipe Up) para reaparecer.",
                            color = Zinc100,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Text(
                            text = "4. CONTROL GEOMÉTRICO: Los deslizadores 'Y' (Horizontal) y 'X' (Vertical) posicionan de forma proporcional con centrado geométrico integrado. ¡Marca 50% para ver tus componentes perfectamente centrados!",
                            color = Zinc100,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Text(
                            text = "5. FUNCIÓN CANDADO: El interruptor '🔒 Fijar Elemento' bloquea completamente las coordenadas del elemento actual para evitar movimientos accidentales al alternar selecciones.",
                            color = Zinc100,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Text(
                            text = "6. COMPILACIÓN DIRECTA: Haz clic en 'COMPILAR Y DESCARGAR' para empaquetar de forma automática todo tu proyecto en un archivo comprimido .ZIP de alta compatibilidad listo para subir a un servidor web.",
                            color = Zinc100,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Close Button
                    Button(
                        onClick = { showDocDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("close_doc_button")
                    ) {
                        Text(
                            text = "Entendido",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WebCreatorLogo() {
    Canvas(
        modifier = Modifier
            .size(120.dp)
            .padding(8.dp)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val innerWidth = size.width * 0.82f
        val innerHeight = size.height * 0.82f
        val left = centerX - innerWidth / 2
        val top = centerY - innerHeight / 2

        // Draw a simulated glowing browser screen
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Purple600, Cyan400),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            topLeft = Offset(left, top),
            size = Size(innerWidth, innerHeight),
            style = Stroke(width = 3.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
        )

        // Draw modern horizontal layout lines for no-code design visual
        drawLine(
            color = Purple600,
            start = Offset(left + 15.dp.toPx(), centerY - 8.dp.toPx()),
            end = Offset(left + innerWidth - 15.dp.toPx(), centerY - 8.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )

        drawLine(
            color = Cyan400,
            start = Offset(left + 25.dp.toPx(), centerY + 8.dp.toPx()),
            end = Offset(left + innerWidth - 25.dp.toPx(), centerY + 8.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )

        // Draw code tag indicator `< >` glow in the corner
        drawCircle(
            color = Cyan400,
            radius = 4.dp.toPx(),
            center = Offset(left + 15.dp.toPx(), top + 15.dp.toPx())
        )
        drawCircle(
            color = Purple600,
            radius = 4.dp.toPx(),
            center = Offset(left + 27.dp.toPx(), top + 15.dp.toPx())
        )
    }
}
