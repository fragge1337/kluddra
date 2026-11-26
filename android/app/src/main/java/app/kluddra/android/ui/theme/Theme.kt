package app.kluddra.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.kluddra.android.data.prefs.ThemeMode

val LocalKluddraDarkTheme = staticCompositionLocalOf { false }
val LocalKluddraSemantics = staticCompositionLocalOf { KluddraLightSemanticColors }

// Generous, soft, tactile Material 3 Expressive shapes
val KluddraShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KluddraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val baseScheme = if (darkTheme) KluddraDarkColorScheme else KluddraLightColorScheme
    // Never allow dynamic color / color theme to tint the app background or neutral surfaces;
    // ONLY update accent color roles (primary, secondary, tertiary and their containers).
    val colorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        baseScheme.copy(
            primary = dyn.primary,
            onPrimary = dyn.onPrimary,
            primaryContainer = dyn.primaryContainer,
            onPrimaryContainer = dyn.onPrimaryContainer,
            inversePrimary = dyn.inversePrimary,
            secondary = dyn.secondary,
            onSecondary = dyn.onSecondary,
            secondaryContainer = dyn.secondaryContainer,
            onSecondaryContainer = dyn.onSecondaryContainer,
            tertiary = dyn.tertiary,
            onTertiary = dyn.onTertiary,
            tertiaryContainer = dyn.tertiaryContainer,
            onTertiaryContainer = dyn.onTertiaryContainer
        )
    } else {
        baseScheme
    }

    val semantics = if (darkTheme) KluddraDarkSemanticColors else KluddraLightSemanticColors

    CompositionLocalProvider(
        LocalKluddraDarkTheme provides darkTheme,
        LocalKluddraSemantics provides semantics
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = KluddraShapes,
            typography = KluddraTypography,
            content = content
        )
    }
}
