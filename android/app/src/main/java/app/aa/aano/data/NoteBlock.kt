// Clean AST Block Model for AANO Notes
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

sealed class NoteBlock {
    data class Paragraph(val text: String) : NoteBlock()
    data class Heading(val level: Int, val text: String) : NoteBlock()
    data class Checkbox(val checked: Boolean, val text: String) : NoteBlock()
    data class Bullet(val text: String) : NoteBlock()
    data class Quote(val text: String) : NoteBlock()
    data class Code(val lang: String = "", val text: String) : NoteBlock()
    data class Image(val alt: String, val url: String) : NoteBlock()
}

object NoteBlockParser {
    fun parse(content: String): List<NoteBlock> {
        if (content.isBlank()) return emptyList()
        val lines = content.lines()
        val blocks = mutableListOf<NoteBlock>()
        var inCodeBlock = false
        var codeLang = ""
        val codeBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    blocks.add(NoteBlock.Code(codeLang, codeBuffer.toString().trimEnd()))
                    codeBuffer.clear()
                    codeLang = ""
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeLang = trimmed.removePrefix("```").trim()
                }
                continue
            }
            if (inCodeBlock) {
                if (codeBuffer.isNotEmpty()) codeBuffer.append("\n")
                codeBuffer.append(line)
                continue
            }

            when {
                trimmed.startsWith("# ") -> {
                    blocks.add(NoteBlock.Heading(1, trimmed.removePrefix("# ").trim()))
                }
                trimmed.startsWith("## ") -> {
                    blocks.add(NoteBlock.Heading(2, trimmed.removePrefix("## ").trim()))
                }
                trimmed.startsWith("### ") -> {
                    blocks.add(NoteBlock.Heading(3, trimmed.removePrefix("### ").trim()))
                }
                trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ") || trimmed.startsWith("☑ ") || trimmed.startsWith("✔ ") -> {
                    val clean = trimmed.replace(Regex("""^(\[x\]|\[X\]|☑|✔)\s*"""), "")
                    blocks.add(NoteBlock.Checkbox(true, clean))
                }
                trimmed.startsWith("[ ] ") || trimmed.startsWith("☐ ") -> {
                    val clean = trimmed.replace(Regex("""^(\[ \]|☐)\s*"""), "")
                    blocks.add(NoteBlock.Checkbox(false, clean))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val clean = trimmed.replace(Regex("""^([-*•])\s*"""), "")
                    blocks.add(NoteBlock.Bullet(clean))
                }
                trimmed.startsWith("> ") -> {
                    blocks.add(NoteBlock.Quote(trimmed.removePrefix("> ").trim()))
                }
                trimmed.startsWith("![") && trimmed.contains("](") && trimmed.endsWith(")") -> {
                    val alt = trimmed.substringAfter("![").substringBefore("](")
                    val url = trimmed.substringAfter("](").removeSuffix(")")
                    blocks.add(NoteBlock.Image(alt, url))
                }
                else -> {
                    blocks.add(NoteBlock.Paragraph(line))
                }
            }
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(NoteBlock.Code(codeLang, codeBuffer.toString()))
        }
        return blocks
    }

    fun toMarkdown(blocks: List<NoteBlock>): String {
        val sb = StringBuilder()
        for ((idx, block) in blocks.withIndex()) {
            if (idx > 0) sb.append("\n")
            when (block) {
                is NoteBlock.Heading -> sb.append("#".repeat(block.level)).append(" ").append(block.text)
                is NoteBlock.Checkbox -> sb.append(if (block.checked) "[x] " else "[ ] ").append(block.text)
                is NoteBlock.Bullet -> sb.append("- ").append(block.text)
                is NoteBlock.Quote -> sb.append("> ").append(block.text)
                is NoteBlock.Code -> {
                    sb.append("```").append(block.lang).append("\n")
                    sb.append(block.text).append("\n```")
                }
                is NoteBlock.Image -> sb.append("![").append(block.alt).append("](").append(block.url).append(")")
                is NoteBlock.Paragraph -> sb.append(block.text)
            }
        }
        return sb.toString()
    }

    fun toPlainText(blocks: List<NoteBlock>): String {
        val sb = StringBuilder()
        for ((idx, block) in blocks.withIndex()) {
            if (idx > 0) sb.append("\n")
            when (block) {
                is NoteBlock.Heading -> sb.append(block.text)
                is NoteBlock.Checkbox -> sb.append(if (block.checked) "[x] " else "[ ] ").append(block.text)
                is NoteBlock.Bullet -> sb.append("• ").append(block.text)
                is NoteBlock.Quote -> sb.append(block.text)
                is NoteBlock.Code -> sb.append(block.text)
                is NoteBlock.Image -> sb.append("[Photo: ").append(block.alt).append("]")
                is NoteBlock.Paragraph -> sb.append(block.text)
            }
        }
        return sb.toString()
    }
}
