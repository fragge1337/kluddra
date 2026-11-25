package app.kluddra.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Kluddra Signature Palette: "Nordic Paper & Terracotta Ink"
// Individually tuned light & dark Material 3 tonal schemes (never inverted).

val KluddraLightColorScheme = lightColorScheme(
    primary = Color(0xFFB84A32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD2),
    onPrimaryContainer = Color(0xFF3D0B02),

    secondary = Color(0xFF466155),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8E7D8),
    onSecondaryContainer = Color(0xFF052016),

    tertiary = Color(0xFF7A590C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA3),
    onTertiaryContainer = Color(0xFF261900),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFBF8F5),
    onBackground = Color(0xFF1F1B18),

    surface = Color(0xFFFBF8F5),
    onSurface = Color(0xFF1F1B18),
    surfaceVariant = Color(0xFFEDE4DC),
    onSurfaceVariant = Color(0xFF524943),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F0EB),
    surfaceContainer = Color(0xFFEFEAE3),
    surfaceContainerHigh = Color(0xFFE8E2DA),
    surfaceContainerHighest = Color(0xFFE1DAD1),

    outline = Color(0xFF847A72),
    outlineVariant = Color(0xFFD7CCC4),
    inverseSurface = Color(0xFF352F2C),
    inverseOnSurface = Color(0xFFF9EFE9),
    inversePrimary = Color(0xFFFFB4A2)
)

val KluddraDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4A2),
    onPrimary = Color(0xFF5F1507),
    primaryContainer = Color(0xFF8F2F1B),
    onPrimaryContainer = Color(0xFFFFDBD2),

    secondary = Color(0xFFADCBBF),
    onSecondary = Color(0xFF183328),
    secondaryContainer = Color(0xFF2F4A3E),
    onSecondaryContainer = Color(0xFFC8E7D8),

    tertiary = Color(0xFFECC06C),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5E4200),
    onTertiaryContainer = Color(0xFFFFDEA3),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF141311),
    onBackground = Color(0xFFEAE1DC),

    surface = Color(0xFF141311),
    onSurface = Color(0xFFEAE1DC),
    surfaceVariant = Color(0xFF37322E),
    onSurfaceVariant = Color(0xFFC5BCB5),

    surfaceContainerLowest = Color(0xFF0F0E0C),
    surfaceContainerLow = Color(0xFF1C1B18),
    surfaceContainer = Color(0xFF22201D),
    surfaceContainerHigh = Color(0xFF2C2926),
    surfaceContainerHighest = Color(0xFF36332F),

    outline = Color(0xFF968C85),
    outlineVariant = Color(0xFF453F3A),
    inverseSurface = Color(0xFFEAE1DC),
    inverseOnSurface = Color(0xFF352F2C),
    inversePrimary = Color(0xFFB84A32)
)
