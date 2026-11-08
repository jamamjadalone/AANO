package app.aa.aano

import app.aa.aano.data.NoteBlock
import app.aa.aano.data.NoteBlockParser
import org.junit.Assert.*
import org.junit.Test

class NoteBlockTest {

    @Test
    fun testParseMixedBlocks() {
        val markdown = """
            # Meeting Agenda
            ## Discussion Items
            - Item 1
            - Item 2
            [ ] Todo task 1
            [x] Completed task 2
            > Important quote from discussion
            ```kotlin
            val x = 42
            ```
            Simple concluding paragraph.
        """.trimIndent()

        val blocks = NoteBlockParser.parse(markdown)
        assertTrue(blocks.isNotEmpty())
        assertTrue(blocks[0] is NoteBlock.Heading && (blocks[0] as NoteBlock.Heading).level == 1)
        assertTrue(blocks[1] is NoteBlock.Heading && (blocks[1] as NoteBlock.Heading).level == 2)
        assertTrue(blocks[2] is NoteBlock.Bullet)
        assertTrue(blocks[4] is NoteBlock.Checkbox && !(blocks[4] as NoteBlock.Checkbox).checked)
        assertTrue(blocks[5] is NoteBlock.Checkbox && (blocks[5] as NoteBlock.Checkbox).checked)
        assertTrue(blocks[6] is NoteBlock.Quote)
        assertTrue(blocks[7] is NoteBlock.Code)
        assertTrue(blocks[8] is NoteBlock.Paragraph)
    }

    @Test
    fun testRoundTripMarkdownSerialization() {
        val originalMarkdown = "# Title\n- Bullet 1\n[ ] Todo\n> Quote\nConclusion"
        val blocks = NoteBlockParser.parse(originalMarkdown)
        val serialized = NoteBlockParser.toMarkdown(blocks)
        assertEquals(originalMarkdown, serialized)
    }

    @Test
    fun testPlainTextExport() {
        val markdown = "# Heading\n[ ] Task 1\n- Point A"
        val blocks = NoteBlockParser.parse(markdown)
        val plain = NoteBlockParser.toPlainText(blocks)
        assertTrue(plain.contains("Heading"))
        assertTrue(plain.contains("[ ] Task 1"))
        assertTrue(plain.contains("• Point A"))
        assertFalse(plain.startsWith("#"))
    }
}
