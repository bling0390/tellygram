@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package tv.telegram.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.telegram.R
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

// JetStream design tokens (Figma: 📺 JetStream - Streaming App).
//   Dark palette = node 76:671 ("Dark"); type scale = node 76:599; shape = 76:807.
// Sizes/weights AND the family match JetStream's Inter scale: Inter is bundled
// in res/font (SIL OFL 1.1, see res/raw/inter_ofl.txt). Inter carries no CJK
// glyphs, so Chinese copy falls back to the system CJK font automatically —
// the latin/汉字 mix is expected and accepted (decision 2026-09-11). JetStream
// ships no light palette, so the light scheme below is kept from the original
// tellygram theme.

private val JetStreamDarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDCBCE1),
    onTertiary = Color(0xFF3E2845),
    tertiaryContainer = Color(0xFF563E5C),
    onTertiaryContainer = Color(0xFFF9D8FE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFC7C6CA),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    border = Color(0xFF8E9099),
    borderVariant = Color(0xFF43474E),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF1A1C1E),
    inversePrimary = Color(0xFF00468A),
    surfaceTint = Color(0xFFA8C8FF),
    scrim = Color(0xFF000000),
)

// Kept from the original tellygram scheme (JetStream has no light palette yet).
private val TellygramLightColors = lightColorScheme(
    primary = Color(0xFF5288C1),
    onPrimary = Color.White,
    secondary = Color(0xFF1E5FA8),
    onSecondary = Color.White,
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE8E8EC),
    onSurfaceVariant = Color(0xFF555555),
)

// Inter (SIL OFL 1.1) — static instances bundled in res/font so every API level
// gets real weights (a variable font collapses to Regular below API 26, and this
// app's minSdk is 21). Only the weights the type scale and call sites use ship.
private val InterFamily = FontFamily(
    Font(R.font.inter_regular, weight = FontWeight.Normal),
    Font(R.font.inter_medium, weight = FontWeight.Medium),
    Font(R.font.inter_semibold, weight = FontWeight.SemiBold),
    Font(R.font.inter_bold, weight = FontWeight.Bold),
)

// JetStream type scale — Inter sizes/weights, rendered in Inter.
private val JetStreamTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

// JetStream shape scale: buttons + cards are 4dp. Circular icon buttons are
// handled by call sites (CircleShape), not this scale.
private val JetStreamShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

@Composable
fun TellygramTheme(
    themeMode: tv.telegram.ui.ThemeMode = tv.telegram.ui.ThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    // Compose loads res/font faces asynchronously: the first use of a weight
    // paints with the system fallback and then swaps to Inter, which reads as a
    // brief text flash. Warm the family up front so navigating never hits a cold
    // font face. (This Compose version's preload() takes the family only.)
    val fontResolver = LocalFontFamilyResolver.current
    LaunchedEffect(fontResolver) {
        fontResolver.preload(InterFamily)
    }
    val scheme = when (themeMode) {
        tv.telegram.ui.ThemeMode.Dark -> JetStreamDarkColors
        tv.telegram.ui.ThemeMode.Light -> TellygramLightColors
        tv.telegram.ui.ThemeMode.System -> JetStreamDarkColors
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = JetStreamShapes,
        typography = JetStreamTypography,
        content = content,
    )
}
