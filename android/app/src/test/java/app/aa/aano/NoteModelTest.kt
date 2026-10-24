package app.aa.aano

import app.aa.aano.data.Note
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NoteModelTest {

    @Test
    fun testNoteSerializationRoundtrip() {
        val original = Note(
            id = "note_test_123",
            title = "Meeting Notes",
            content = "# Sprint Planning\\n- Task 1\\n- Task 2",
            createdAt = 1700000000000L,
            updatedAt = 1700005000000L,
            deleted = false,
            deletedAt = 0L,
            pinned = true,
            color = 2,
            folder = "Work",
            tags = listOf("work", "sprint"),
            reminderAt = 1700010000000L,
            archived = false,
            isLocal = true,
            images = listOf("img_1.webp", "img_2.webp"),
            audioUri = "content://audio/1",
            audioDuration = 12000L,
            purged = false
        )

        val json = original.toJson()
        val restored = Note.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.title, restored.title)
        assertEquals(original.content, restored.content)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.updatedAt, restored.updatedAt)
        assertEquals(original.deleted, restored.deleted)
        assertEquals(original.pinned, restored.pinned)
        assertEquals(original.color, restored.color)
        assertEquals(original.folder, restored.folder)
        assertEquals(original.tags, restored.tags)
        assertEquals(original.reminderAt, restored.reminderAt)
        assertEquals(original.archived, restored.archived)
        assertEquals(original.isLocal, restored.isLocal)
        assertEquals(original.images, restored.images)
        assertEquals(original.audioUri, restored.audioUri)
        assertEquals(original.audioDuration, restored.audioDuration)
        assertEquals(original.purged, restored.purged)
    }

    @Test
    fun testPurgedTombstoneFlagPreserved() {
        val purgedNote = Note(
            id = "note_purged_999",
            title = "Deleted Note",
            content = "Old content",
            createdAt = 1000L,
            updatedAt = 2000L,
            deleted = true,
            deletedAt = 2500L,
            purged = true
        )

        val json = purgedNote.toJson()
        assertTrue("JSON should have purged: true", json.getBoolean("purged"))
        assertTrue("JSON should have deleted: true", json.getBoolean("deleted"))

        val parsed = Note.fromJson(json)
        assertTrue(parsed.purged)
        assertTrue(parsed.deleted)
        assertEquals(2500L, parsed.deletedAt)
    }

    @Test
    fun testDefaultValuesOnEmptyJson() {
        val minJson = JSONObject().apply {
            put("id", "min_1")
        }
        val note = Note.fromJson(minJson)
        assertEquals("min_1", note.id)
        assertEquals("", note.title)
        assertEquals("", note.content)
        assertFalse(note.deleted)
        assertFalse(note.pinned)
        assertFalse(note.purged)
        assertFalse(note.isLocal)
        assertTrue(note.tags.isEmpty())
        assertTrue(note.images.isEmpty())
        assertNull(note.audioUri)
    }
}
