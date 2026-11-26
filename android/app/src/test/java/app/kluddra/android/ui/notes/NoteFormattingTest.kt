package app.kluddra.android.ui.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.kluddra.android.ui.theme.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormattingTest {

    @Test
    fun `parse and serialize roundtrip preserves paragraphs, checklists, and numbered lists`() {
        val markdown = """
            **Viktig** introduktion
            - [ ] Köp kaffe
            - [x] Boka tåg
            1. Packa skaljacka
            2. Ladda powerbank
        """.trimIndent()

        val blocks = NoteFormatting.parseBlocks(markdown)
        assertEquals(5, blocks.size)
        assertEquals(NoteBlockType.PARAGRAPH, blocks[0].type)
        assertEquals(NoteBlockType.CHECKBOX, blocks[1].type)
        assertFalse(blocks[1].isChecked)
        assertEquals(NoteBlockType.CHECKBOX, blocks[2].type)
        assertTrue(blocks[2].isChecked)
        assertEquals(NoteBlockType.NUMBERED, blocks[3].type)
        assertEquals(NoteBlockType.NUMBERED, blocks[4].type)

        assertEquals(markdown, NoteFormatting.serializeBlocks(blocks))
    }

    @Test
    fun `toggleBold wraps and unwraps selection cleanly`() {
        val initial = TextFieldValue("Hello world", TextRange(6, 11))
        val bolded = NoteFormatting.toggleBold(initial)
        assertEquals("Hello **world**", bolded.text)
        assertEquals(TextRange(8, 13), bolded.selection)

        val unbolded = NoteFormatting.toggleBold(bolded)
        assertEquals("Hello world", unbolded.text)
        assertEquals(TextRange(6, 11), unbolded.selection)
    }

    @Test
    fun `toggleBold on mixed bold selection always makes entire selection bold first then unbolds all`() {
        // Mixed text: "Hej **världen** idag", entire line selected
        val mixed = TextFieldValue("Hej **världen** idag", TextRange(0, 20))
        assertFalse(NoteFormatting.isSelectionFullyBold(mixed.text, mixed.selection))

        // 1st click: entire selection becomes uniformly bold (never inverts inner bold!)
        val allBold = NoteFormatting.toggleBold(mixed)
        assertEquals("**Hej världen idag**", allBold.text)
        assertEquals(TextRange(2, 18), allBold.selection)
        assertTrue(NoteFormatting.isSelectionFullyBold(allBold.text, allBold.selection))

        // 2nd click: removes all bold from the selection
        val allPlain = NoteFormatting.toggleBold(allBold)
        assertEquals("Hej världen idag", allPlain.text)
        assertEquals(TextRange(0, 16), allPlain.selection)
        assertFalse(NoteFormatting.isSelectionFullyBold(allPlain.text, allPlain.selection))

        // Partial slice inside a bold span unbolds just that slice, then re-merges cleanly
        val sliceInBold = TextFieldValue("**Hej stor värld**", TextRange(6, 10)) // "stor"
        assertTrue(NoteFormatting.isSelectionFullyBold(sliceInBold.text, sliceInBold.selection))
        val unboldedSlice = NoteFormatting.toggleBold(sliceInBold)
        assertEquals("**Hej **stor** värld**", unboldedSlice.text)
        assertFalse(NoteFormatting.isSelectionFullyBold(unboldedSlice.text, unboldedSlice.selection))
        val remerged = NoteFormatting.toggleBold(unboldedSlice)
        assertEquals("**Hej stor värld**", remerged.text)
    }

    @Test
    fun `WYSIWYG bold typing wraps newly typed characters without leaving visible stars`() {
        // Clicking Bold with empty cursor does not insert visible ****
        val empty = TextFieldValue("Hej ", TextRange(4))
        assertEquals(empty, NoteFormatting.toggleBold(empty))

        // Typing 'd' while bold typing mode is active wraps it cleanly and keeps cursor inside
        val afterFirstChar = NoteFormatting.applyBoldTypingDelta(
            previous = empty,
            updated = TextFieldValue("Hej d", TextRange(5)),
            boldTypingActive = true
        )
        assertEquals("Hej **d**", afterFirstChar.text)
        assertEquals(TextRange(7), afterFirstChar.selection)
        assertTrue(NoteFormatting.isCursorInBold(afterFirstChar.text, afterFirstChar.selection.start))

        // Exiting bold moves cursor past closing **
        val exited = NoteFormatting.exitBoldAtCursor(afterFirstChar)
        assertEquals(TextRange(9), exited.selection)
        assertFalse(NoteFormatting.isCursorInBold(exited.text, exited.selection.start))
    }

    @Test
    fun `autoGenerateTitle strips markdown and truncates at word boundary`() {
        assertEquals(
            "Köp kaffe och bröd",
            NoteFormatting.autoGenerateTitle("- [ ] **Köp kaffe** och bröd\nAndra raden")
        )
        val longLine = "1. Detta är en mycket lång första rad som behöver kortas snyggt vid ett mellanslag"
        val title = NoteFormatting.autoGenerateTitle(longLine, maxLength = 32)
        assertTrue(title.endsWith("…"))
        assertTrue(title.length <= 33)
    }

    @Test
    fun `smart neighbor color picker avoids immediate neighboring colors`() {
        val picked = NoteColor.pickAvoidingNeighbors(listOf("lavender", "pistachio", "salmon"))
        assertNotEquals(NoteColor.LAVENDER, picked)
        assertNotEquals(NoteColor.PISTACHIO, picked)
        assertNotEquals(NoteColor.SALMON, picked)
        assertTrue(picked in NoteColor.autoPalette)
    }
}
