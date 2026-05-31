package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Purple600,
    secondary = Cyan400,
    tertiary = Purple500,
    background = PureBlack,
    surface = Zinc900,
    onPrimary = Color.White,
    onSecondary = PureBlack,
    onTertiary = Color.White,
    onBackground = Zinc100,
    onSurface = Zinc100
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Purple600,
    secondary = Cyan400,
    tertiary = Purple500,
    background = PureBlack,
    surface = Zinc900,
    onPrimary = Color.White,
    onSecondary = PureBlack,
    onBackground = Zinc100,
    onSurface = Zinc100
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme
  dynamicColor: Boolean = false, // Disable Android dynamic colors to ensure neon look
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
