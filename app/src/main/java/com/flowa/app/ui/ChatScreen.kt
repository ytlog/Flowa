@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.flowa.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowa.app.presentation.AppViewModel
import com.flowa.domain.*

@Composable fun ChatScreen(state: AppState, vm: AppViewModel, back: () -> Unit, history: () -> Unit) {
    val id by vm.chatId.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val tools by vm.toolActivity.collectAsStateWithLifecycle()
    val busy by vm.chatting.collectAsStateWithLifecycle()
    val messages = state.sessions.firstOrNull { it.id == id }?.messages.orEmpty()
    var input by rememberSaveable { mutableStateOf("") }
    var inputVersion by rememberSaveable { mutableLongStateOf(0L) }
    fun updateInput(value: String) {
        if (input != value) {
            input = value
            inputVersion++
        }
    }
    val list = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, canForward) -> if (scrolling) follow = !canForward } }
    LaunchedEffect(streaming, messages.size, tools.size) { if (follow && list.layoutInfo.totalItemsCount > 0) list.scrollToItem(list.layoutInfo.totalItemsCount - 1) }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("AI 助手", back) {
            GlyphButton(Icons.Outlined.History, "历史对话", !busy, history)
            GlyphButton(Icons.Outlined.Edit, "新对话", !busy) { vm.newChat() }
        }
        if (messages.isEmpty() && !busy) Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(Icons.Outlined.AutoAwesome, "和记录聊一聊", "查找、整理和回顾你的想法，也可以把重复的任务交给助手。")
            listOf("帮我回顾最近的记录", "整理还没有标签的记录", "总结本周的想法").forEach { suggestion -> OutlinedButton(onClick = { updateInput(suggestion) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) { Text(suggestion) } }
        } else LazyColumn(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
            if (busy || streaming.isNotEmpty()) item(key = "streaming") { Column {
                if (tools.isNotEmpty()) ToolActivity(tools)
                if (streaming.isNotEmpty()) SelectionContainer { MarkdownText(streaming) }
                if (busy) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp); Spacer(Modifier.width(8.dp)); Text("正在处理…", color = Secondary, style = MaterialTheme.typography.bodySmall) }
            } }
            item(key = "bottom") { Spacer(Modifier.height(4.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(input, ::updateInput, Modifier.weight(1f), placeholder = { Text("给 AI 助手发送消息…", style = MaterialTheme.typography.bodyMedium) }, shape = RoundedCornerShape(24.dp), maxLines = 6, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = {
                if (busy) vm.stopChat() else {
                    val submittedVersion = inputVersion
                    vm.send(input) {
                        if (inputVersion == submittedVersion) updateInput("")
                        follow = true
                    }
                }
            }, enabled = busy || input.isNotBlank(), modifier = Modifier.padding(bottom = 4.dp).size(48.dp)) { Icon(if (busy) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward, if (busy) "停止生成" else "发送消息") }
        }
    }
}
@Composable private fun MessageBubble(message: Message) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.role == "user") Alignment.End else Alignment.Start) {
        if (message.tools.isNotEmpty()) ToolActivity(message.tools)
        if (message.role == "user") Surface(shape = RoundedCornerShape(18.dp), color = Secondary.copy(.10f)) { SelectionContainer { Text(message.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) } }
        else SelectionContainer { MarkdownText(message.text) }
    }
}
@Composable private fun ToolActivity(tools: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Secondary.copy(.06f), RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = Secondary); Spacer(Modifier.width(6.dp)); Text("已执行 ${tools.size} 个操作", Modifier.weight(1f), color = Secondary, style = MaterialTheme.typography.bodySmall); Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "展开工具活动", tint = Secondary) }
        if (expanded) tools.forEach { Text("✓ $it", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Secondary) }
    }
}
@Composable fun HistorySheet(state: AppState, vm: AppViewModel, close: () -> Unit) {
    var deleting by remember { mutableStateOf<ChatSession?>(null) }
    SheetPage("历史对话", close, actions = { GlyphButton(Icons.Outlined.Add, "新对话") { vm.newChat(); close() } }) {
        if (state.sessions.isEmpty()) EmptyState(Icons.Outlined.ChatBubbleOutline, "还没有对话", "向 AI 助手发送第一条消息，对话会自动保存在这里。")
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { items(state.sessions, key = { it.id }) { session ->
            FormSection { FormRow(session.title, "${session.messages.size} 条消息", onClick = { vm.selectChat(session.id); close() }, trailing = { GlyphButton(Icons.Outlined.DeleteOutline, "删除对话") { deleting = session } }) }
        } }
    }
    deleting?.let { session -> ConfirmDelete("删除这段对话？", "对话历史将被永久删除，已有记录和助手记忆不受影响。", { deleting = null }) { vm.deleteChat(session.id) } }
}
