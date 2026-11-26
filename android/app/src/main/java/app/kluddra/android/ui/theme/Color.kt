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

    background = Color(0xFFF4F1F8),
    onBackground = Color(0xFF19171E),

    surface = Color(0xFFF4F1F8),
    onSurface = Color(0xFF19171E),
    surfaceVariant = Color(0xFFE6E0F0),
    onSurfaceVariant = Color(0xFF4A4458),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEAE5F3),
    surfaceContainerHigh = Color(0xFFE0D9EC),
    surfaceContainerHighest = Color(0xFFD6CDE6),

    outline = Color(0xFF7B738A),
    outlineVariant = Color(0xFFCCC4DB),
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

    background = Color(0xFF1C1C1C),
    onBackground = Color(0xFFF2F2F4),

    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFF2F2F4),
    surfaceVariant = Color(0xFF2D2D30),
    onSurfaceVariant = Color(0xFFC2C2C7),

    surfaceContainerLowest = Color(0xFF141414),
    surfaceContainerLow = Color(0xFF252527),
    surfaceContainer = Color(0xFF2C2C2F),
    surfaceContainerHigh = Color(0xFF343438),
    surfaceContainerHighest = Color(0xFF3E3E42),

    outline = Color(0xFF8E8E95),
    outlineVariant = Color(0xFF46464C),
    inverseSurface = Color(0xFFF2F2F4),
    inverseOnSurface = Color(0xFF1E1E20),
    inversePrimary = Color(0xFF7152B8)
)
