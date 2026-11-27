package app.kluddra.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Kluddra Material 3 Expressive Palette
// UI chrome uses high-contrast Lavender/Indigo accents; semantic green (success),
// red (destructive/error), and amber (warning) are strictly reserved for actions & status.

data class KluddraSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

/**
 * Deliberate 5-layer depth tokens (Z-1 recessed wells -> Z0 canvas -> Z+1 cards ->
 * Z+2 anchored chrome -> Z+3 floating tools/FAB) tuned separately for Light Mode and
 * the dimmed slate Dark Mode (#23242B) so physical layering reads clearly without harshness.
 */
data class KluddraElevationColors(
    val recessedWell: Color,
    val recessedInnerShadow: Color,
    val recessedBottomCatch: Color,
    val cardAmbientShadow: Color,
    val cardSpotShadow: Color,
    val cardTopHighlight: Color,
    val cardBottomEdge: Color,
    val navBarContainer: Color,
    val navBarTopShadow: Color,
    val fabAmbientShadow: Color,
    val fabKeyShadow: Color
)

val KluddraLightElevationColors = KluddraElevationColors(
    recessedWell = Color(0xFFE2DCEB),
    recessedInnerShadow = Color(0xFF281D40).copy(alpha = 0.09f),
    recessedBottomCatch = Color.White.copy(alpha = 0.72f),
    cardAmbientShadow = Color(0xFF2B2044).copy(alpha = 0.22f),
    cardSpotShadow = Color(0xFF22183A).copy(alpha = 0.34f),
    cardTopHighlight = Color.White.copy(alpha = 0.88f),
    cardBottomEdge = Color(0xFF564A6E).copy(alpha = 0.16f),
    navBarContainer = Color(0xFFFAF8FD),
    navBarTopShadow = Color(0xFF23183A).copy(alpha = 0.055f),
    fabAmbientShadow = Color(0xFF5A3DB0).copy(alpha = 0.34f),
    fabKeyShadow = Color(0xFF1F123D).copy(alpha = 0.28f)
)

val KluddraDarkElevationColors = KluddraElevationColors(
    recessedWell = Color(0xFF1B1C22),
    recessedInnerShadow = Color.Black.copy(alpha = 0.34f),
    recessedBottomCatch = Color.White.copy(alpha = 0.07f),
    cardAmbientShadow = Color.Black.copy(alpha = 0.62f),
    cardSpotShadow = Color.Black.copy(alpha = 0.85f),
    cardTopHighlight = Color.White.copy(alpha = 0.13f),
    cardBottomEdge = Color.Black.copy(alpha = 0.30f),
    navBarContainer = Color(0xFF30323D),
    navBarTopShadow = Color.Black.copy(alpha = 0.15f),
    fabAmbientShadow = Color.Black.copy(alpha = 0.48f),
    fabKeyShadow = Color.Black.copy(alpha = 0.68f)
)

val KluddraLightSemanticColors = KluddraSemanticColors(
    success = Color(0xFF1E7E3E),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFD4F3DC),
    onSuccessContainer = Color(0xFF0A3619),
    warning = Color(0xFFAD5700),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFE7C2),
    onWarningContainer = Color(0xFF3B1C00)
)

val KluddraDarkSemanticColors = KluddraSemanticColors(
    success = Color(0xFF74DA92),
    onSuccess = Color(0xFF073919),
    successContainer = Color(0xFF194728),
    onSuccessContainer = Color(0xFFC8F7D4),
    warning = Color(0xFFFFB85C),
    onWarning = Color(0xFF452100),
    warningContainer = Color(0xFF573000),
    onWarningContainer = Color(0xFFFFE2BA)
)

val KluddraLightColorScheme = lightColorScheme(
    primary = Color(0xFF674AB8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3D7FA),
    onPrimaryContainer = Color(0xFF1E1045),

    secondary = Color(0xFF59448F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6C6F8),
    onSecondaryContainer = Color(0xFF1C0F42),

    tertiary = Color(0xFFB85438),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8CB),
    onTertiaryContainer = Color(0xFF361106),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFECE8F3),
    onBackground = Color(0xFF19171E),

    surface = Color(0xFFECE8F3),
    onSurface = Color(0xFF19171E),
    surfaceVariant = Color(0xFFE0D9EC),
    onSurfaceVariant = Color(0xFF4A4458),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFE4DDF0),
    surfaceContainerHigh = Color(0xFFDBD2EA),
    surfaceContainerHighest = Color(0xFFD1C6E3),

    outline = Color(0xFF787087),
    outlineVariant = Color(0xFFC8BFDA),
    inverseSurface = Color(0xFF2E2B36),
    inverseOnSurface = Color(0xFFF5EFFB),
    inversePrimary = Color(0xFFCBB6FA)
)

val KluddraDarkColorScheme = darkColorScheme(
    primary = Color(0xFFCBB6FA),
    onPrimary = Color(0xFF25144D),
    primaryContainer = Color(0xFF4C3582),
    onPrimaryContainer = Color(0xFFEBE2FF),

    secondary = Color(0xFFB8A5EB),
    onSecondary = Color(0xFF211245),
    secondaryContainer = Color(0xFF453575),
    onSecondaryContainer = Color(0xFFEFE6FF),

    tertiary = Color(0xFFF28864),
    onTertiary = Color(0xFF331106),
    tertiaryContainer = Color(0xFF662A16),
    onTertiaryContainer = Color(0xFFFFD8CA),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF23242B),
    onBackground = Color(0xFFF2F2F6),

    surface = Color(0xFF23242B),
    onSurface = Color(0xFFF2F2F6),
    surfaceVariant = Color(0xFF333540),
    onSurfaceVariant = Color(0xFFC4C5CF),

    surfaceContainerLowest = Color(0xFF1B1C22),
    surfaceContainerLow = Color(0xFF2E303A),
    surfaceContainer = Color(0xFF343642),
    surfaceContainerHigh = Color(0xFF3C3F4D),
    surfaceContainerHighest = Color(0xFF454858),

    outline = Color(0xFF9092A0),
    outlineVariant = Color(0xFF4B4E5E),
    inverseSurface = Color(0xFFF2F2F6),
    inverseOnSurface = Color(0xFF1F2026),
    inversePrimary = Color(0xFF7152B8)
)
