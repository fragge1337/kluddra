package app.kluddra.android.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionalOrderAndUuidTest {

    @Test
    fun `uuidv7 generates canonical 36 char version 7 uuids`() {
        val a = UuidV7.generate()
        val b = UuidV7.generate()
        assertEquals(36, a.length)
        assertEquals('7', a[14])
        assertTrue(a != b)
    }

    @Test
    fun `fractional order maintains strict lexicographical order on repeated insertions`() {
        val first = FractionalOrder.initial(0)
        val second = FractionalOrder.after(first)
        assertTrue(first < second)

        var left = first
        val right = second
        repeat(10) {
            val mid = FractionalOrder.between(left, right)
            assertTrue("Expected $left < $mid < $right", left < mid && mid < right)
            left = mid
        }
    }
}
