package app.kluddra.android.ui.notes

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

enum class NoteBlockType {
    PARAGRAPH,
    CHECKBOX,
    NUMBERED
}

data class NoteBlock(
    val uid: String,
    val type: NoteBlockType,
    val text: String,
    val isChecked: Boolean = false
)

/**
 * Lightweight, lossless Markdown-backed formatter supporting:
 * - Bold inline text: **bold**
 * - Numbered lists: 1. item
 * - Interactive checkbox lists: - [ ] item / - [x] item
 * - Automatic clean title generation from the first meaningful line of content.
 */
object NoteFormatting {
    private val CHECKBOX_UNCHECKED = Regex("""^-\s*\[\s]\s?(.*)$""")
    private val CHECKBOX_CHECKED = Regex("""^-\s*\[[xX]]\s?(.*)$""")
    private val NUMBERED = Regex("""^\d+\.\s+(.*)$""")
    private val BOLD_SPAN = Regex("""\*\*(.+?)\*\*""")

    fun parseBlocks(body: String): List<NoteBlock> {
        if (body.isEmpty()) {
            return listOf(NoteBlock(uid = "b0", type = NoteBlockType.PARAGRAPH, text = ""))
        }
        return body.split('\n').mapIndexed { index, rawLine ->
            val checkedMatch = CHECKBOX_CHECKED.matchEntire(rawLine)
            val uncheckedMatch = CHECKBOX_UNCHECKED.matchEntire(rawLine)
            val numberedMatch = NUMBERED.matchEntire(rawLine)
            when {
                checkedMatch != null -> NoteBlock(
                    uid = "b$index",
                    type = NoteBlockType.CHECKBOX,
                    text = checkedMatch.groupValues[1],
                    isChecked = true
                )
                uncheckedMatch != null -> NoteBlock(
                    uid = "b$index",
                    type = NoteBlockType.CHECKBOX,
                    text = uncheckedMatch.groupValues[1],
                    isChecked = false
                )
                numberedMatch != null -> NoteBlock(
                    uid = "b$index",
                    type = NoteBlockType.NUMBERED,
                    text = numberedMatch.groupValues[1]
                )
                else -> NoteBlock(
                    uid = "b$index",
                    type = NoteBlockType.PARAGRAPH,
                    text = rawLine
                )
            }
        }
    }

    fun serializeBlocks(blocks: List<NoteBlock>): String {
        var numberCounter = 0
        return blocks.joinToString("\n") { block ->
            val cleanText = block.text.replace("****", "")
            when (block.type) {
                NoteBlockType.PARAGRAPH -> {
                    numberCounter = 0
                    cleanText
                }
                NoteBlockType.CHECKBOX -> {
                    numberCounter = 0
                    val mark = if (block.isChecked) "x" else " "
                    "- [$mark] $cleanText"
                }
                NoteBlockType.NUMBERED -> {
                    numberCounter++
                    "$numberCounter. $cleanText"
                }
            }
        }
    }

    /**
     * Computes the display ordinal (1, 2, 3...) for a numbered block at [index].
     */
    fun numberForIndex(blocks: List<NoteBlock>, index: Int): Int {
        var count = 0
        for (i in index downTo 0) {
            if (blocks[i].type == NoteBlockType.NUMBERED) count++ else break
        }
        return count.coerceAtLeast(1)
    }

    /**
     * Returns true if [cursor] lies inside a **bold** Markdown span.
     */
    fun isCursorInBold(text: String, cursor: Int): Boolean {
        val pos = cursor.coerceIn(0, text.length)
        for (match in BOLD_SPAN.findAll(text)) {
            val innerStart = match.range.first + 2
            val innerEnd = match.range.last - 1 // exclusive of closing **
            if (pos in innerStart..innerEnd) return true
        }
        return false
    }

    /**
     * Returns true when every visible character in [selection] is bold (or when collapsed,
     * if the cursor lies inside a bold span). Used to drive the Bold toolbar toggle state.
     */
    fun isSelectionFullyBold(text: String, selection: TextRange): Boolean {
        val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
        val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
        if (start == end) {
            return isCursorInBold(text, start)
        }
        val parsed = parseBoldLine(text)
        val pStart = parsed.rawToPlain[start]
        val pEnd = parsed.rawToPlain[end]
        if (pStart >= pEnd) return false
        for (i in pStart until pEnd) {
            if (!parsed.bold[i]) return false
        }
        return true
    }

    /**
     * Moves a collapsed cursor outside the closing ** of a bold span so subsequent typing is normal weight.
     */
    fun exitBoldAtCursor(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val pos = value.selection.start.coerceIn(0, text.length)
        if (!value.selection.collapsed) return value
        for (match in BOLD_SPAN.findAll(text)) {
            val innerStart = match.range.first + 2
            val innerEnd = match.range.last - 1
            if (pos in innerStart..innerEnd) {
                return value.copy(selection = TextRange(match.range.last + 1))
            }
        }
        return value
    }

    /**
     * When [boldTypingActive] is enabled and the user types new characters outside an existing bold span,
     * wraps them seamlessly in **...** while keeping the cursor inside the span (zero visible asterisks!).
     */
    fun applyBoldTypingDelta(
        previous: TextFieldValue,
        updated: TextFieldValue,
        boldTypingActive: Boolean
    ): TextFieldValue {
        if (!boldTypingActive) return updated
        if (!updated.selection.collapsed) return updated
        val oldText = previous.text
        val newText = updated.text
        if (newText.length <= oldText.length || newText.contains('\n')) return updated

        val insertedLen = newText.length - oldText.length
        val cursorAfter = updated.selection.start.coerceIn(0, newText.length)
        val insertStart = (cursorAfter - insertedLen).coerceIn(0, oldText.length)

        // Verify this was a clean single insertion at insertStart
        val prefixMatches = oldText.startsWith(newText.substring(0, insertStart))
        val suffixAfter = newText.substring(cursorAfter)
        val suffixMatches = oldText.endsWith(suffixAfter) && (insertStart + suffixAfter.length == oldText.length)
        if (!prefixMatches || !suffixMatches) return updated

        // Already inside a **...** span? Keep as-is!
        if (isCursorInBold(oldText, insertStart)) return updated

        val inserted = newText.substring(insertStart, cursorAfter)
        if (inserted.isEmpty()) return updated

        val before = newText.substring(0, insertStart)
        val after = newText.substring(cursorAfter)

        // If typing immediately after a closing **, extend that existing bold span cleanly!
        if (before.endsWith("**") && BOLD_SPAN.findAll(before).any { it.range.last + 1 == before.length }) {
            val extended = before.dropLast(2) + inserted + "**" + after
            val newCursor = before.length - 2 + inserted.length
            return TextFieldValue(extended, TextRange(newCursor))
        }

        val wrapped = "$before**$inserted**$after"
        val newCursor = insertStart + 2 + inserted.length
        return TextFieldValue(wrapped, TextRange(newCursor))
    }

    private data class ParsedBoldLine(
        val plain: String,
        val bold: BooleanArray,
        val rawToPlain: IntArray
    )

    private fun parseBoldLine(raw: String): ParsedBoldLine {
        val rawToPlain = IntArray(raw.length + 1)
        val plainChars = StringBuilder(raw.length)
        val boldList = ArrayList<Boolean>(raw.length)

        var rawPos = 0
        for (match in BOLD_SPAN.findAll(raw)) {
            val openStart = match.range.first
            val innerStart = openStart + 2
            val innerEnd = match.range.last - 1 // exclusive of closing **
            val closeEnd = match.range.last + 1

            while (rawPos < openStart) {
                rawToPlain[rawPos] = plainChars.length
                plainChars.append(raw[rawPos])
                boldList.add(false)
                rawPos++
            }
            rawToPlain[openStart] = plainChars.length
            rawToPlain[openStart + 1] = plainChars.length
            rawPos = innerStart

            while (rawPos < innerEnd) {
                rawToPlain[rawPos] = plainChars.length
                plainChars.append(raw[rawPos])
                boldList.add(true)
                rawPos++
            }
            rawToPlain[innerEnd] = plainChars.length
            rawToPlain[innerEnd + 1] = plainChars.length
            rawPos = closeEnd
        }

        while (rawPos < raw.length) {
            rawToPlain[rawPos] = plainChars.length
            plainChars.append(raw[rawPos])
            boldList.add(false)
            rawPos++
        }
        rawToPlain[raw.length] = plainChars.length

        return ParsedBoldLine(
            plain = plainChars.toString(),
            bold = BooleanArray(boldList.size) { boldList[it] },
            rawToPlain = rawToPlain
        )
    }

    /**
     * WYSIWYG character-level Bold toggle:
     * - If ANY character in the selection is not bold (unformatted or mixed), makes 100% of
     *   the selected range bold and merges adjacent bold runs cleanly (never inverts segments).
     * - If EVERY character in the selection is already bold, removes bold from the entire
     *   selected range cleanly (even for a partial slice inside a longer bold phrase).
     */
    fun toggleBold(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)

        if (start == end) {
            return value
        }

        val parsed = parseBoldLine(text)
        val pStart = parsed.rawToPlain[start]
        val pEnd = parsed.rawToPlain[end]
        if (pStart >= pEnd) {
            return value
        }

        var allBold = true
        for (i in pStart until pEnd) {
            if (!parsed.bold[i]) {
                allBold = false
                break
            }
        }

        val targetBold = !allBold
        for (i in pStart until pEnd) {
            parsed.bold[i] = targetBold
        }

        val out = StringBuilder(parsed.plain.length + 16)
        var inBold = false
        var newRawStart = 0
        var newRawEnd = 0

        for (i in parsed.plain.indices) {
            val shouldBeBold = parsed.bold[i]
            if (shouldBeBold && !inBold) {
                out.append("**")
                inBold = true
            } else if (!shouldBeBold && inBold) {
                out.append("**")
                inBold = false
            }
            if (i == pStart) {
                newRawStart = out.length
            }
            out.append(parsed.plain[i])
            if (i + 1 == pEnd) {
                newRawEnd = out.length
            }
        }
        if (inBold) {
            out.append("**")
        }

        return TextFieldValue(
            text = out.toString(),
            selection = TextRange(newRawStart, newRawEnd)
        )
    }

    /**
     * Renders **bold** spans cleanly as FontWeight.Bold without showing raw ** asterisks.
     */
    fun renderInlineBold(raw: String): AnnotatedString = buildAnnotatedString {
        var cursor = 0
        for (match in BOLD_SPAN.findAll(raw)) {
            if (match.range.first > cursor) {
                append(raw.substring(cursor, match.range.first))
            }
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(match.groupValues[1])
            pop()
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) {
            append(raw.substring(cursor))
        }
    }

    /**
     * Derives a concise, clean automatic title from the first meaningful line of [body]
     * when the user didn't enter a manual title.
     */
    fun autoGenerateTitle(body: String, maxLength: Int = 46): String {
        val firstLine = parseBlocks(body)
            .map { stripBoldMarkers(it.text).trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return ""

        if (firstLine.length <= maxLength) return firstLine
        val cut = firstLine.take(maxLength)
        val lastSpace = cut.lastIndexOf(' ')
        val clean = if (lastSpace >= maxLength / 2) cut.substring(0, lastSpace) else cut
        return clean.trimEnd('.', ',', ';', ':', ' ') + "…"
    }

    fun stripBoldMarkers(text: String): String =
        BOLD_SPAN.replace(text, "$1").replace("**", "")

    /**
     * Formats note content cleanly for Android Share intents.
     */
    fun toSharePlainText(title: String, body: String): String {
        val blocks = parseBlocks(body)
        val formattedBody = buildString {
            var num = 0
            blocks.forEachIndexed { idx, block ->
                val clean = stripBoldMarkers(block.text)
                when (block.type) {
                    NoteBlockType.PARAGRAPH -> {
                        num = 0
                        append(clean)
                    }
                    NoteBlockType.CHECKBOX -> {
                        num = 0
                        append(if (block.isChecked) "☑ " else "☐ ")
                        append(clean)
                    }
                    NoteBlockType.NUMBERED -> {
                        num++
                        append("$num. ")
                        append(clean)
                    }
                }
                if (idx < blocks.lastIndex) append('\n')
            }
        }.trim()

        return buildString {
            if (title.isNotBlank()) {
                append(title.trim())
                if (formattedBody.isNotBlank()) append("\n\n")
            }
            append(formattedBody)
        }
    }
}

/**
 * Renders **bold** Markdown spans visually as real bold text inside an editable TextField
 * while preserving exact cursor & selection mapping!
 */
class BoldMarkdownVisualTransformation : VisualTransformation {
    private val boldRegex = Regex("""\*\*(.*?)\*\*""")

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val matches = boldRegex.findAll(raw).toList()
        if (matches.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        // Map original index -> transformed index and vice versa
        val origToTrans = IntArray(raw.length + 1)
        val transToOrig = mutableListOf<Int>()
        val builder = AnnotatedString.Builder()

        var rawPos = 0
        var transPos = 0

        for (match in matches) {
            val openStart = match.range.first
            val innerStart = openStart + 2
            val innerEnd = match.range.last - 1 // exclusive
            val closeEnd = match.range.last + 1

            // Copy plain prefix
            while (rawPos < openStart) {
                origToTrans[rawPos] = transPos
                transToOrig.add(rawPos)
                builder.append(raw[rawPos])
                rawPos++
                transPos++
            }
            // Hidden opening **
            origToTrans[openStart] = transPos
            origToTrans[openStart + 1] = transPos
            rawPos = innerStart

            // Bold inner text
            val boldStartTrans = transPos
            while (rawPos < innerEnd) {
                origToTrans[rawPos] = transPos
                transToOrig.add(rawPos)
                builder.append(raw[rawPos])
                rawPos++
                transPos++
            }
            builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), boldStartTrans, transPos)

            // Hidden closing **
            origToTrans[innerEnd] = transPos
            origToTrans[innerEnd + 1] = transPos
            rawPos = closeEnd
        }

        while (rawPos < raw.length) {
            origToTrans[rawPos] = transPos
            transToOrig.add(rawPos)
            builder.append(raw[rawPos])
            rawPos++
            transPos++
        }
        origToTrans[raw.length] = transPos
        transToOrig.add(raw.length)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                origToTrans[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transToOrig[offset.coerceIn(0, transToOrig.lastIndex)]
        }

        return TransformedText(builder.toAnnotatedString(), mapping)
    }
}
