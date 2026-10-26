package de.vanhake.wakeup.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Background = Color(0xFF0E0F13)
val Surface = Color(0xFF181A20)
val SurfaceHigh = Color(0xFF23262E)
val Accent = Color(0xFFFF8A3D)
val AccentDim = Color(0xFF5A3418)
val Secondary = Color(0xFF7AA2FF)
val TextPrimary = Color(0xFFF2F3F5)
val TextSecondary = Color(0xFF9EA3AE)
val Danger = Color(0xFFFF5A5F)
val Success = Color(0xFF4CD08A)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0E00),
    primaryContainer = AccentDim,
    onPrimaryContainer = Color(0xFFFFDCC6),
    secondary = Secondary,
    onSecondary = Color(0xFF001A44),
    secondaryContainer = Color(0xFF26324D),
    onSecondaryContainer = Color(0xFFD9E2FF),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    surfaceContainerLow = Surface,
    surfaceContainerLowest = Background,
    outline = Color(0xFF3A3E48),
    outlineVariant = Color(0xFF2C2F37),
    error = Danger,
    onError = Color.White,
)

private val base = Typography()

private val typography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
)

@Composable
fun WakeUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Screens drawn without Scaffold/Surface (ringing screen, challenges) would otherwise get
        // Compose's default black text on the dark background.
        CompositionLocalProvider(LocalContentColor provides TextPrimary, content = content)
    }
}
