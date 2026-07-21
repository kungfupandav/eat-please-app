package com.eatplease.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.eatplease.app.generated.resources.Res
import com.eatplease.app.generated.resources.fredoka_bold
import com.eatplease.app.generated.resources.fredoka_medium
import com.eatplease.app.generated.resources.fredoka_regular
import com.eatplease.app.generated.resources.fredoka_semibold
import org.jetbrains.compose.resources.Font

/**
 * Flat, high-contrast "neo-brutalist" palette: a warm cream ground, hard black
 * ink for every border and shadow, and a set of saturated blocks whose hue
 * carries meaning (green = eating/steady, coral = paused/attention, yellow =
 * the highlighted/selected accent). Card hues (orange/magenta/cyan/lime) are
 * decorative section colors used in pairs of header + lighter body.
 */
object NeoColors {
    val Cream = Color(0xFFFBE7D6)
    val CreamDeep = Color(0xFFF5D9C4)
    val Ink = Color(0xFF0B0B0B)

    val Green = Color(0xFF88E88C)   // eating / steady / positive
    val Coral = Color(0xFFFF7059)   // paused / poke fired / attention
    val Yellow = Color(0xFFFFD60A)  // selected / highlight

    val OrangeHead = Color(0xFFFF9E1B); val OrangeBody = Color(0xFFFFC163)
    val MagentaHead = Color(0xFFFF17C4); val MagentaBody = Color(0xFFF7ADE1)
    val CyanHead = Color(0xFF34D6F0); val CyanBody = Color(0xFFAEE6F5)
    val LimeHead = Color(0xFF59D96A); val LimeBody = Color(0xFFA7EBAE)
    val BluePill = Color(0xFF79C7F2)
}

private val NeoColorScheme = lightColorScheme(
    primary = NeoColors.Ink,
    onPrimary = NeoColors.Cream,
    secondary = NeoColors.Green,
    onSecondary = NeoColors.Ink,
    tertiary = NeoColors.Yellow,
    onTertiary = NeoColors.Ink,
    background = NeoColors.Cream,
    onBackground = NeoColors.Ink,
    surface = NeoColors.Cream,
    onSurface = NeoColors.Ink,
    surfaceVariant = NeoColors.CreamDeep,
    onSurfaceVariant = NeoColors.Ink,
    outline = NeoColors.Ink,
    outlineVariant = NeoColors.Ink,
    error = NeoColors.Coral,
    onError = NeoColors.Ink,
)

@Composable
private fun fredokaFamily(): FontFamily = FontFamily(
    Font(Res.font.fredoka_regular, FontWeight.Normal),
    Font(Res.font.fredoka_medium, FontWeight.Medium),
    Font(Res.font.fredoka_semibold, FontWeight.SemiBold),
    Font(Res.font.fredoka_bold, FontWeight.Bold),
)

/** All type runs on Fredoka; display/headline sit at Bold for the chunky look. */
@Composable
private fun neoTypography(f: FontFamily): Typography {
    val base = Typography()
    fun heavy(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = f, fontWeight = FontWeight.Bold)
    fun semi(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = f, fontWeight = FontWeight.SemiBold)
    fun med(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = f, fontWeight = FontWeight.Medium)
    return Typography(
        displayLarge = heavy(base.displayLarge),
        displayMedium = heavy(base.displayMedium),
        displaySmall = heavy(base.displaySmall),
        headlineLarge = heavy(base.headlineLarge),
        headlineMedium = heavy(base.headlineMedium),
        headlineSmall = heavy(base.headlineSmall),
        titleLarge = semi(base.titleLarge),
        titleMedium = semi(base.titleMedium),
        titleSmall = semi(base.titleSmall),
        bodyLarge = med(base.bodyLarge),
        bodyMedium = med(base.bodyMedium),
        bodySmall = med(base.bodySmall),
        labelLarge = semi(base.labelLarge),
        labelMedium = semi(base.labelMedium),
        labelSmall = semi(base.labelSmall),
    )
}

/** Single light-committed theme — the neo-brutalist look is a deliberate one-world design. */
@Composable
fun EatPleaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeoColorScheme,
        typography = neoTypography(fredokaFamily()),
        content = content,
    )
}
