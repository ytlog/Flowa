package com.flowa.domain

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The same front matter fields as the iOS Markdown store. */
object MarkdownCodec {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm-ss")
    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    private fun unquote(value: String): String = value.trim().removeSurrounding("\"").replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
    private fun parseDate(value: String): LocalDateTime = runCatching { LocalDateTime.parse(value, stamp) }.getOrElse { LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
    fun fileName(time: Long): String = stamp.format(java.time.Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())) + ".md"
    fun encode(note: Note): String = buildString {
        append("---\ndescription: ${quote(note.text.take(50))}\ncreated: ${stamp.format(java.time.Instant.ofEpochMilli(note.createdAt).atZone(ZoneId.systemDefault()))}\n")
        if (note.tags.isNotEmpty()) { append("tags:\n"); note.tags.forEach { append("  - ${quote(it)}\n") } }
        append("---\n\n${note.text}")
    }
    fun decode(raw: String, id: String = newId(), fallbackTime: Long = System.currentTimeMillis()): Note {
        val lines = raw.removePrefix("\uFEFF").replace("\r\n", "\n").lines()
        val end = if (lines.firstOrNull() == "---") (1 until lines.size).firstOrNull { lines[it] == "---" } else null
        if (end == null) return Note(id, raw, createdAt = fallbackTime)
        var time = fallbackTime
        var inTags = false
        val tags = mutableListOf<String>()
        lines.subList(1, end).forEach { line ->
            val value = line.trim()
            when {
                value.startsWith("created:") -> { inTags = false; time = runCatching { parseDate(unquote(value.substringAfter(':'))).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(fallbackTime) }
                value.startsWith("tags:") -> inTags = true
                inTags && value.startsWith("- ") -> tags.add(unquote(value.removePrefix("- ")))
                value.contains(':') -> inTags = false
            }
        }
        return Note(id, lines.drop(end + 1).dropWhile { it.isBlank() }.joinToString("\n"), tags.filter { it.isNotBlank() }.distinct(), time)
    }
}
