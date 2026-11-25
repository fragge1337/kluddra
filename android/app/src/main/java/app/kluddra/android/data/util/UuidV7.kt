package app.kluddra.android.data.util

import java.security.SecureRandom
import java.util.UUID

object UuidV7 {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(16)
        val ms = System.currentTimeMillis()
        bytes[0] = (ms ushr 40).toByte()
        bytes[1] = (ms ushr 32).toByte()
        bytes[2] = (ms ushr 24).toByte()
        bytes[3] = (ms ushr 16).toByte()
        bytes[4] = (ms ushr 8).toByte()
        bytes[5] = ms.toByte()

        val randTail = ByteArray(10)
        random.nextBytes(randTail)
        System.arraycopy(randTail, 0, bytes, 6, 10)

        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        var msb = 0L
        var lsb = 0L
        for (i in 0 until 8) msb = (msb shl 8) or (bytes[i].toLong() and 0xFF)
        for (i in 8 until 16) lsb = (lsb shl 8) or (bytes[i].toLong() and 0xFF)
        return UUID(msb, lsb).toString()
    }
}

/**
 * Generates deterministic lexicographical sort keys so reordering one shopping item
 * only modifies that single row and merges cleanly across offline devices.
 */
object FractionalOrder {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val MIN_CHAR = '0'
    private const val MAX_CHAR = 'z'
    private const val MID_CHAR = 'V'

    fun initial(index: Int = 0): String {
        val base = (index + 1).coerceAtMost(999_999)
        return "m%06d".format(base)
    }

    fun after(prev: String?): String {
        if (prev.isNullOrBlank()) return "m000010"
        return between(prev, null)
    }

    fun before(next: String?): String {
        if (next.isNullOrBlank()) return "m000010"
        return between(null, next)
    }

    fun between(before: String?, after: String?): String {
        val a = before ?: ""
        val b = after ?: ""

        if (a.isEmpty() && b.isEmpty()) return "m"

        val maxLen = maxOf(a.length, b.length)
        val sb = StringBuilder()

        for (i in 0 until maxLen + 1) {
            val leftIdx = if (i < a.length) ALPHABET.indexOf(a[i]).coerceAtLeast(0) else 0
            val rightIdx = if (i < b.length) ALPHABET.indexOf(b[i]).let { if (it < 0) ALPHABET.lastIndex else it } else ALPHABET.lastIndex

            if (rightIdx - leftIdx > 1) {
                val midIdx = (leftIdx + rightIdx) / 2
                sb.append(ALPHABET[midIdx])
                return sb.toString()
            } else {
                sb.append(ALPHABET[leftIdx])
            }
        }
        sb.append(MID_CHAR)
        return sb.toString()
    }

    fun validKey(key: String): Boolean = key.isNotEmpty() && key.all { it in MIN_CHAR..MAX_CHAR }
}
