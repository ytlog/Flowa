package com.flowa.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private data class MarkdownBlock(val type: String, val text: String, val marker: String = "", val level: Int = 0)
private fun blocks(source: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>(); val paragraph = mutableListOf<String>(); var code: MutableList<String>? = null
    fun flush() { if (paragraph.isNotEmpty()) result.add(MarkdownBlock("paragraph", paragraph.joinToString("\n"))); paragraph.clear() }
    source.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> { flush(); if (code != null) { result.add(MarkdownBlock("code", code!!.joinToString("\n"))); code = null } else code = mutableListOf() }
            code != null -> code!!.add(line)
            trimmed.isBlank() -> flush()
            Regex("^#{1,6} .*?").containsMatchIn(trimmed) -> { flush(); val level = trimmed.takeWhile { it == '#' }.length; result.add(MarkdownBlock("heading", trimmed.drop(level + 1), level = level)) }
            listOf("- ", "* ", "+ ").any(trimmed::startsWith) -> { flush(); result.add(MarkdownBlock("item", trimmed.drop(2), "•")) }
            Regex("^\\d+[.)]\\s+").containsMatchIn(trimmed) -> { flush(); val marker = Regex("^\\d+[.)]\\s+").find(trimmed)!!.value; result.add(MarkdownBlock("item", trimmed.drop(marker.length), marker.trim())) }
            trimmed.startsWith('>') -> { flush(); result.add(MarkdownBlock("quote", trimmed.drop(1).trim())) }
            else -> paragraph.add(line)
        }
    }
    flush(); code?.let { result.add(MarkdownBlock("code", it.joinToString("\n"))) }; return result
}
private fun inline(source: String, secondary: Color): AnnotatedString = buildAnnotatedString {
    val expression = Regex("(\\*\\*([^*]+)\\*\\*|__([^_]+)__|`([^`]+)`|\\*([^*]+)\\*|_([^_]+)_|\\[([^]]+)]\\(([^)]+)\\))")
    var offset = 0
    expression.findAll(source).forEach { match ->
        append(source.substring(offset, match.range.first))
        val groups = match.groupValues
        when {
            groups[2].isNotEmpty() || groups[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(groups[2].ifEmpty { groups[3] }) }
            groups[4].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = secondary.copy(.12f))) { append(groups[4]) }
            groups[5].isNotEmpty() || groups[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(groups[5].ifEmpty { groups[6] }) }
            else -> { pushStringAnnotation("URL", groups[8]); withStyle(SpanStyle(color = Indigo, textDecoration = TextDecoration.Underline)) { append(groups[7]) }; pop() }
        }
        offset = match.range.last + 1
    }
    append(source.substring(offset))
}
@Composable fun MarkdownText(text: String) {
    val parsed = remember(text) { blocks(text) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parsed.forEach { block ->
            when(block.type) {
                "heading" -> Text(inline(block.text, Secondary), Modifier.semantics { heading() }, style = if (block.level == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                "code" -> Box(Modifier.fillMaxWidth().background(Secondary.copy(.08f), RoundedCornerShape(12.dp)).horizontalScroll(rememberScrollState()).padding(12.dp)) { Text(block.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, softWrap = false) }
                "quote" -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Box(Modifier.width(3.dp).fillMaxHeight().background(Secondary.copy(.35f), RoundedCornerShape(2.dp))); Text(inline(block.text, Secondary), color = Secondary) }
                "item" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(block.marker); Text(inline(block.text, Secondary), Modifier.weight(1f)) }
                else -> Text(inline(block.text, Secondary))
            }
        }
    }
}
