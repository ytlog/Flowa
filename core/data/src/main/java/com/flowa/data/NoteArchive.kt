package com.flowa.data

import com.flowa.domain.MarkdownCodec
import com.flowa.domain.Note
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object NoteArchive {
    fun encode(notes: List<Note>): ByteArray {
        val output = ByteArrayOutputStream()
        val names = mutableSetOf<String>()
        ZipOutputStream(output).use { zip ->
            notes.forEach { note ->
                val base = MarkdownCodec.fileName(note.createdAt).removeSuffix(".md")
                var name = "$base.md"
                var suffix = 2
                while (!names.add(name)) name = "$base-${suffix++}.md"
                zip.putNextEntry(ZipEntry(name))
                // Filename collisions must never alter the creation date inside a note.
                zip.write(MarkdownCodec.encode(note).toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray, zipped: Boolean): List<Note> {
        if (!zipped) return listOf(MarkdownCodec.decode(bytes.toString(Charsets.UTF_8)))
        val notes = mutableListOf<Note>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0L
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 10000) { "压缩包中的文件过多，请分批导入。" }
                val bytesInEntry = if (entry.isDirectory) byteArrayOf() else zip.readBytesLimited(50 * 1024 * 1024)
                total += bytesInEntry.size
                require(total <= 100 * 1024 * 1024) { "压缩包解压后超过 100 MB，请分批导入。" }
                val filename = entry.name.substringAfterLast('/')
                val isMarkdown = filename.endsWith(".md", true) || filename.endsWith(".markdown", true)
                if (!entry.isDirectory && isMarkdown && !filename.startsWith('_')) {
                    notes.add(MarkdownCodec.decode(bytesInEntry.toString(Charsets.UTF_8)))
                }
                zip.closeEntry()
            }
        }
        require(notes.isNotEmpty()) { "所选内容中没有 Markdown 记录。" }
        return notes
    }
}

fun InputStream.readBytesLimited(max: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= max) { "文件过大，请缩小后重新导入。" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
