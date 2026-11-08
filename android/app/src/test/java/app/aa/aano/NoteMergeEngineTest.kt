package app.aa.aano

import app.aa.aano.data.NoteMergeEngine
import org.junit.Assert.*
import org.junit.Test

class NoteMergeEngineTest {

    @Test
    fun testIdenticalContentNoConflict() {
        val text = "Line 1\nLine 2\nLine 3"
        val (merged, hasConflict) = NoteMergeEngine.mergeNoteContent(text, text)
        assertEquals(text, merged)
        assertFalse(hasConflict)
    }

    @Test
    fun testNonOverlappingInsertions() {
        val local = "Line 1\nLine 2\nLine 3"
        val remote = "Line 0\nLine 1\nLine 2"
        val (merged, hasConflict) = NoteMergeEngine.mergeNoteContent(local, remote)
        assertTrue(merged.contains("Line 0"))
        assertTrue(merged.contains("Line 3"))
        assertFalse(hasConflict)
    }

    @Test
    fun testCollidingEditsMarkConflictNonDestructively() {
        val local = "Header\nLocal version of paragraph"
        val remote = "Header\nRemote version of paragraph"
        val (merged, hasConflict) = NoteMergeEngine.mergeNoteContent(local, remote)
        assertTrue(hasConflict)
        assertTrue(merged.contains("<<<<<<< Local Edit"))
        assertTrue(merged.contains("Local version of paragraph"))
        assertTrue(merged.contains("======="))
        assertTrue(merged.contains("Remote version of paragraph"))
        assertTrue(merged.contains(">>>>>>> Incoming Edit"))
    }
}
