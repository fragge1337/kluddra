package app.kluddra.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationAuditTest {

    private val expectedLocales = listOf(
        "values",
        "values-sv",
        "values-de",
        "values-fr",
        "values-es",
        "values-it",
        "values-pt",
        "values-nl",
        "values-pl",
        "values-nb",
        "values-da"
    )

    // Compact button & tab keys that must stay concise across all European languages so text never clips.
    private val maxButtonLength = mapOf(
        "nav_notes" to 16,
        "nav_shopping" to 16,
        "theme_light" to 12,
        "theme_dark" to 12,
        "theme_system" to 12,
        "action_sync_now" to 12,
        "action_undo" to 14,
        "action_cancel" to 14,
        "action_confirm" to 14,
        "shopping_clear_checked" to 22
    )

    @Test
    fun `all 11 European locale string files are valid, complete, and fit compact buttons`() {
        val resDir = listOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).firstOrNull { it.exists() } ?: error("Could not locate src/main/res")

        val baseStrings = parseStringsXml(File(resDir, "values/strings.xml"))
        assertTrue("Base strings.xml must not be empty", baseStrings.size > 50)

        val placeholderRegex = Regex("""%\d+\$[ds]""")

        for (dirName in expectedLocales) {
            val file = File(resDir, "$dirName/strings.xml")
            assertTrue("Missing localization file: ${file.path}", file.exists())
            val localized = parseStringsXml(file)

            val missing = baseStrings.keys - localized.keys
            assertTrue("Locale $dirName is missing keys: $missing", missing.isEmpty())

            for ((key, baseVal) in baseStrings) {
                val locVal = localized.getValue(key)
                assertTrue("Empty string '$key' in $dirName", locVal.isNotBlank())

                val expectedPlaceholders = placeholderRegex.findAll(baseVal).map { it.value }.toList()
                val actualPlaceholders = placeholderRegex.findAll(locVal).map { it.value }.toList()
                assertEquals(
                    "Format placeholder mismatch for '$key' in $dirName",
                    expectedPlaceholders,
                    actualPlaceholders
                )
            }

            for ((btnKey, limit) in maxButtonLength) {
                val text = localized.getValue(btnKey)
                assertTrue(
                    "Button label '$btnKey' in $dirName is too long (${text.length} > $limit): '$text'",
                    text.length <= limit
                )
            }
        }
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name").nodeValue
            map[name] = node.textContent
        }
        return map
    }
}
