package app.kluddra.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Curated expressive card colors for Kluddra notes.
 * Each color has individually tuned Light and Dark mode container + high-contrast ink colors
 * inspired by tactile Scandinavian stationery and Material 3 Expressive contrast.
 */
enum class NoteColor(
    val key: String,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val lightSecondaryText: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val darkSecondaryText: Color,
    val swatchLight: Color,
    val swatchDark: Color
) {
    LAVENDER(
        key = "lavender",
        lightContainer = Color(0xFFE5DCFA),
        lightOnContainer = Color(0xFF1E1533),
        lightSecondaryText = Color(0xFF423561),
        darkContainer = Color(0xFFC7B4F7),
        darkOnContainer = Color(0xFF171126),
        darkSecondaryText = Color(0xFF33274D),
        swatchLight = Color(0xFFCCB9F6),
        swatchDark = Color(0xFFC7B4F7)
    ),
    PISTACHIO(
        key = "pistachio",
        lightContainer = Color(0xFFD5F0B8),
        lightOnContainer = Color(0xFF162B0B),
        lightSecondaryText = Color(0xFF315022),
        darkContainer = Color(0xFFB2E07E),
        darkOnContainer = Color(0xFF112108),
        darkSecondaryText = Color(0xFF274219),
        swatchLight = Color(0xFFB7E08E),
        swatchDark = Color(0xFFB2E07E)
    ),
    MINT(
        key = "mint",
        lightContainer = Color(0xFFCBF2E5),
        lightOnContainer = Color(0xFF0A2B22),
        lightSecondaryText = Color(0xFF214E42),
        darkContainer = Color(0xFF8CE0C5),
        darkOnContainer = Color(0xFF06211A),
        darkSecondaryText = Color(0xFF164035),
        swatchLight = Color(0xFF9FE4CE),
        swatchDark = Color(0xFF8CE0C5)
    ),
    SKY(
        key = "sky",
        lightContainer = Color(0xFFCBE8FF),
        lightOnContainer = Color(0xFF0C2233),
        lightSecondaryText = Color(0xFF24455E),
        darkContainer = Color(0xFF94CCF8),
        darkOnContainer = Color(0xFF081A29),
        darkSecondaryText = Color(0xFF1A3852),
        swatchLight = Color(0xFF9FD3FC),
        swatchDark = Color(0xFF94CCF8)
    ),
    ROSE(
        key = "rose",
        lightContainer = Color(0xFFFFD4E5),
        lightOnContainer = Color(0xFF33101F),
        lightSecondaryText = Color(0xFF5C283E),
        darkContainer = Color(0xFFF5A6C6),
        darkOnContainer = Color(0xFF260A16),
        darkSecondaryText = Color(0xFF4D1E32),
        swatchLight = Color(0xFFFAB0CE),
        swatchDark = Color(0xFFF5A6C6)
    ),
    SALMON(
        key = "salmon",
        lightContainer = Color(0xFFFFD4C4),
        lightOnContainer = Color(0xFF331309),
        lightSecondaryText = Color(0xFF5E2C1D),
        darkContainer = Color(0xFFF28864),
        darkOnContainer = Color(0xFF240C05),
        darkSecondaryText = Color(0xFF471B0E),
        swatchLight = Color(0xFFF9B199),
        swatchDark = Color(0xFFF28864)
    ),
    PEACH(
        key = "peach",
        lightContainer = Color(0xFFFFE0B8),
        lightOnContainer = Color(0xFF331C06),
        lightSecondaryText = Color(0xFF5D3A18),
        darkContainer = Color(0xFFF7B46E),
        darkOnContainer = Color(0xFF261403),
        darkSecondaryText = Color(0xFF4B2B0D),
        swatchLight = Color(0xFFFAC687),
        swatchDark = Color(0xFFF7B46E)
    ),
    BUTTER(
        key = "butter",
        lightContainer = Color(0xFFFCECA8),
        lightOnContainer = Color(0xFF2B2205),
        lightSecondaryText = Color(0xFF544515),
        darkContainer = Color(0xFFF4D565),
        darkOnContainer = Color(0xFF211A03),
        darkSecondaryText = Color(0xFF42360D),
        swatchLight = Color(0xFFF5DB75),
        swatchDark = Color(0xFFF4D565)
    ),
    SAND(
        key = "sand",
        lightContainer = Color(0xFFEFE4D0),
        lightOnContainer = Color(0xFF2A2218),
        lightSecondaryText = Color(0xFF544838),
        darkContainer = Color(0xFFD9C5A3),
        darkOnContainer = Color(0xFF211A10),
        darkSecondaryText = Color(0xFF443828),
        swatchLight = Color(0xFFDFCEB0),
        swatchDark = Color(0xFFD9C5A3)
    ),
    NEUTRAL(
        key = "neutral",
        lightContainer = Color(0xFFFFFFFF),
        lightOnContainer = Color(0xFF1C1A21),
        lightSecondaryText = Color(0xFF4E4A57),
        darkContainer = Color(0xFF2E303A),
        darkOnContainer = Color(0xFFF2F2F6),
        darkSecondaryText = Color(0xFFC4C5CF),
        swatchLight = Color(0xFFE9E6EF),
        swatchDark = Color(0xFF464958)
    );

    fun container(darkTheme: Boolean): Color = if (darkTheme) darkContainer else lightContainer
    fun onContainer(darkTheme: Boolean): Color = if (darkTheme) darkOnContainer else lightOnContainer
    fun secondaryText(darkTheme: Boolean): Color = if (darkTheme) darkSecondaryText else lightSecondaryText
    fun swatch(darkTheme: Boolean): Color = if (darkTheme) swatchDark else swatchLight

    /**
     * Softly tinted canvas color for the Note Editor screen: airy pastel stationery wash in
     * Light Mode and dimmed pigmented slate in Dark Mode, preserving effortless readability.
     */
    fun editorBackground(darkTheme: Boolean, neutralBackground: Color): Color = when (this) {
        LAVENDER -> if (darkTheme) Color(0xFF262330) else Color(0xFFF3EEFD)
        PISTACHIO -> if (darkTheme) Color(0xFF212922) else Color(0xFFEDF8E2)
        MINT -> if (darkTheme) Color(0xFF1D2926) else Color(0xFFE9FAF4)
        SKY -> if (darkTheme) Color(0xFF1F2730) else Color(0xFFEAF5FF)
        ROSE -> if (darkTheme) Color(0xFF2B2128) else Color(0xFFFFEEF5)
        SALMON -> if (darkTheme) Color(0xFF2C2221) else Color(0xFFFFEFE9)
        PEACH -> if (darkTheme) Color(0xFF2B241C) else Color(0xFFFFF4E6)
        BUTTER -> if (darkTheme) Color(0xFF29261E) else Color(0xFFFEF8DF)
        SAND -> if (darkTheme) Color(0xFF28251F) else Color(0xFFF8F3EA)
        NEUTRAL -> neutralBackground
    }

    companion object {
        /** Vibrant curated colors used for smart automatic non-adjacent assignment. */
        val autoPalette: List<NoteColor> = listOf(
            LAVENDER, PISTACHIO, MINT, SKY, ROSE, SALMON, PEACH, BUTTER, SAND
        )

        fun fromKey(key: String?): NoteColor {
            if (key.isNullOrBlank()) return LAVENDER
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: LAVENDER
        }

        /**
         * Resolves a stable color for legacy notes created before the color field existed.
         */
        fun resolveForNote(colorKey: String?, noteId: String): NoteColor {
            if (!colorKey.isNullOrBlank()) return fromKey(colorKey)
            val idx = (noteId.hashCode() and Int.MAX_VALUE) % autoPalette.size
            return autoPalette[idx]
        }

        /**
         * Picks a color for a newly created note that avoids matching the immediate neighboring
         * notes in the overview (top 3 notes cover left, right, and directly above in a 2-column grid).
         * Once assigned, the color is stored permanently on the note so later deletions never shift colors.
         */
        fun pickAvoidingNeighbors(neighborColorKeys: List<String>): NoteColor {
            val recent = neighborColorKeys
                .take(3)
                .mapNotNull { k -> autoPalette.firstOrNull { it.key.equals(k, ignoreCase = true) } }
                .toSet()

            val candidates = autoPalette.filterNot { it in recent }
            if (candidates.isNotEmpty()) {
                // Rotate deterministically based on total neighbor count so consecutive notes stay varied
                return candidates[neighborColorKeys.size % candidates.size]
            }
            val immediatePrev = neighborColorKeys.firstOrNull()?.let { fromKey(it) }
            return autoPalette.firstOrNull { it != immediatePrev } ?: LAVENDER
        }
    }
}
