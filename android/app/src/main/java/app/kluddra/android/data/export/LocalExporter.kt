package app.kluddra.android.data.export

import app.kluddra.android.BuildConfig
import app.kluddra.android.data.local.NoteDao
import app.kluddra.android.data.local.ShoppingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
private data class ExportNoteJson(
    val id: String,
    val title: String,
    val body: String,
    @SerialName("is_pinned") val isPinned: Boolean,
    @SerialName("is_read_only") val isReadOnly: Boolean,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("trashed_at") val trashedAt: Long?
)

@Serializable
private data class ExportShoppingJson(
    val id: String,
    val text: String,
    @SerialName("is_checked") val isChecked: Boolean,
    @SerialName("sort_order") val sortOrder: String
)

@Serializable
private data class ExportBundleJson(
    @SerialName("format_version") val formatVersion: Int = 1,
    @SerialName("exported_at") val exportedAt: Long,
    @SerialName("app_version") val appVersion: String,
    val notes: List<ExportNoteJson>,
    @SerialName("shopping_items") val shoppingItems: List<ExportShoppingJson>
)

/**
 * Exports all local notes and shopping items completely offline in open formats (JSON or Markdown ZIP).
 */
class LocalExporter(
    private val noteDao: NoteDao,
    private val shoppingDao: ShoppingDao
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun writeJson(output: OutputStream) = withContext(Dispatchers.IO) {
        val notes = noteDao.getAllNonPurgedNotes().map {
            ExportNoteJson(
                id = it.id,
                title = it.title,
                body = it.body,
                isPinned = it.isPinned,
                isReadOnly = it.isReadOnly,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                trashedAt = it.trashedAt
            )
        }
        val items = shoppingDao.getActiveItemsOnce().map {
            ExportShoppingJson(
                id = it.id,
                text = it.text,
                isChecked = it.isChecked,
                sortOrder = it.sortOrder
            )
        }
        val payload = json.encodeToString(
            ExportBundleJson(
                exportedAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                notes = notes,
                shoppingItems = items
            )
        )
        output.write(payload.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    suspend fun writeMarkdownZip(output: OutputStream) = withContext(Dispatchers.IO) {
        val notes = noteDao.getAllNonPurgedNotes()
        val items = shoppingDao.getActiveItemsOnce()

        ZipOutputStream(output).use { zip ->
            val used = mutableMapOf<String, Int>()
            for (note in notes) {
                val folder = if (note.trashedAt != null) "trash" else "notes"
                val base = slugify(note.title).ifEmpty { "note-${note.id.take(8)}" }
                val key = "$folder/$base"
                val count = (used[key] ?: 0) + 1
                used[key] = count
                val name = if (count == 1) "$key.md" else "$key-$count.md"

                val md = buildString {
                    appendLine("---")
                    appendLine("id: ${note.id}")
                    appendLine("created_at: ${Instant.ofEpochMilli(note.createdAt)}")
                    appendLine("updated_at: ${Instant.ofEpochMilli(note.updatedAt)}")
                    appendLine("pinned: ${note.isPinned}")
                    appendLine("read_only: ${note.isReadOnly}")
                    appendLine("---")
                    appendLine()
                    if (note.title.isNotBlank()) {
                        appendLine("# ${note.title}")
                        appendLine()
                    }
                    append(note.body)
                    if (!note.body.endsWith("\n")) appendLine()
                }
                zip.putNextEntry(ZipEntry(name))
                zip.write(md.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val shoppingMd = buildString {
                appendLine("# Shopping List / Inköpslista")
                appendLine()
                for (item in items) {
                    val mark = if (item.isChecked) "x" else " "
                    appendLine("- [$mark] ${item.text}")
                }
            }
            zip.putNextEntry(ZipEntry("shopping-list.md"))
            zip.write(shoppingMd.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun slugify(input: String): String {
        val lower = input.trim().lowercase()
        if (lower.isEmpty()) return ""
        val sb = StringBuilder()
        var lastDash = false
        for (c in lower) {
            if (c.isLetterOrDigit()) {
                sb.append(c)
                lastDash = false
            } else if (!lastDash && sb.isNotEmpty()) {
                sb.append('-')
                lastDash = true
            }
            if (sb.length >= 48) break
        }
        return sb.toString().trim('-')
    }
}
