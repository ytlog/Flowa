@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.flowa.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowa.app.presentation.AppViewModel
import com.flowa.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable fun LibraryScreen(state: AppState, initialSearch: String, vm: AppViewModel, back: () -> Unit, editTags: (Set<String>) -> Unit, stats: () -> Unit, importNotes: () -> Unit, export: () -> Unit) {
    var search by rememberSaveable { mutableStateOf(initialSearch) }
    var included by remember { mutableStateOf(setOf<String>()) }
    var excluded by remember { mutableStateOf(setOf<String>()) }
    var untagged by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var randomOrder by remember { mutableStateOf<List<String>?>(null) }
    var deleting by remember { mutableStateOf<Set<String>?>(null) }
    var calendar by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val filter = NoteFilter(search, included, excluded, untagged, date)
    val notes = remember(state.notes, filter, randomOrder) { state.notes.filter(filter::matches).let { list -> randomOrder?.let { order -> list.sortedBy { order.indexOf(it.id) } } ?: list } }
    LaunchedEffect(state.notes) { selected = selected.intersect(state.notes.map { it.id }.toSet()) }
    Column(Modifier.fillMaxSize().background(Grouped)) {
        PageHeader(if (selecting) "已选择 ${selected.size} 条" else "记录", back) {
            if (selecting) TextButton(onClick = { selecting = false; selected = emptySet() }) { Text("完成") }
            else Box {
                GlyphButton(Icons.Outlined.MoreHoriz, "记录操作") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("选择记录") }, { menu = false; selecting = true }, leadingIcon = { Icon(Icons.Outlined.CheckCircleOutline, null) })
                    DropdownMenuItem({ Text("随机回顾") }, { menu = false; randomOrder = notes.shuffled().map { it.id } }, leadingIcon = { Icon(Icons.Outlined.Shuffle, null) })
                    DropdownMenuItem({ Text("统计") }, { menu = false; calendar = true }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) })
                    DropdownMenuItem({ Text("从文件导入") }, { menu = false; importNotes() }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) })
                    DropdownMenuItem({ Text("导出全部记录") }, { menu = false; export() }, leadingIcon = { Icon(Icons.Outlined.IosShare, null) })
                }
            }
        }
        SearchField(search, { search = it }, "搜索记录，空格分隔多个关键词")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(included.isEmpty() && excluded.isEmpty() && !untagged && date == null, { included = emptySet(); excluded = emptySet(); untagged = false; date = null; randomOrder = null }, { Text("全部") })
            FilterChip(untagged, { untagged = !untagged }, { Text("无标签") })
            state.tags.forEach { tag -> FilterChip(tag in included, { included = if (tag in included) included - tag else included + tag; excluded = excluded - tag }, { Text(if (tag in excluded) "排除 $tag" else tag) }) }
            Box {
                IconButton(onClick = { filterMenu = true }) { Icon(Icons.Outlined.FilterAlt, "排除标签") }
                DropdownMenu(filterMenu, { filterMenu = false }) { state.tags.forEach { tag -> DropdownMenuItem({ Text("${if (tag in excluded) "✓ " else ""}排除 $tag") }, { excluded = if (tag in excluded) excluded - tag else excluded + tag; included = included - tag; filterMenu = false }) } }
            }
            date?.let { InputChip(true, { date = null }, { Text(it.toString()) }, trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) }) }
        }
        if (notes.isEmpty()) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { EmptyState(Icons.Outlined.Layers, if (state.notes.isEmpty()) "还没有记录" else "没有找到记录", if (state.notes.isEmpty()) "写下想法，使用包含“保存记录”步骤的工作流保存。" else "试试更短的关键词，或减少标签筛选。") { TextButton(onClick = if (state.notes.isEmpty()) back else ({ search = ""; included = emptySet(); excluded = emptySet(); untagged = false; date = null })) { Text(if (state.notes.isEmpty()) "开始记录" else "清除筛选") } } }
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(notes, key = { it.id }) { note ->
                NoteCard(note, selecting, note.id in selected, { selected = if (note.id in selected) selected - note.id else selected + note.id }, { selecting = true; selected = selected + note.id }, { vm.copy(note.text) }, { editTags(setOf(note.id)) }, { deleting = setOf(note.id) })
            }
            item { Text("${notes.size} 条记录 · 已保存在本机", Modifier.fillMaxWidth().padding(8.dp), color = Secondary, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        if (selecting) Surface(shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(if (selected.isEmpty()) ToggleableState.Off else if (notes.all { it.id in selected }) ToggleableState.On else ToggleableState.Indeterminate, { selected = if (notes.all { it.id in selected }) emptySet() else notes.map { it.id }.toSet() })
                Text("全选", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                GlyphButton(Icons.Outlined.ContentCopy, "复制所选记录", selected.isNotEmpty()) { vm.copy(state.notes.filter { it.id in selected }.joinToString("\n\n") { it.text }) }
                GlyphButton(Icons.Outlined.LocalOffer, "批量编辑标签", selected.isNotEmpty()) { editTags(selected) }
                GlyphButton(Icons.Outlined.DeleteOutline, "删除所选记录", selected.isNotEmpty()) { deleting = selected }
            }
        }
    }
    deleting?.let { ids -> ConfirmDelete("删除 ${ids.size} 条记录？", "所选记录将被永久删除，无法撤销。", { deleting = null }) { vm.deleteNotes(ids); selected = selected - ids } }
    if (calendar) StatisticsSheet(state.notes, { date = it; calendar = false }) { calendar = false }
}

@Composable private fun NoteCard(note: Note, selecting: Boolean, selected: Boolean, toggle: () -> Unit, select: () -> Unit, copy: () -> Unit, tags: () -> Unit, delete: () -> Unit) {
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value -> if (value == SwipeToDismissBoxValue.EndToStart) delete() else if (value == SwipeToDismissBoxValue.StartToEnd) tags(); false })
    SwipeToDismissBox(swipe, backgroundContent = { Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Red else Indigo).padding(20.dp), contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart) { Icon(if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Icons.Outlined.DeleteOutline else Icons.Outlined.LocalOffer, null, tint = Color.White) } }, enableDismissFromStartToEnd = !selecting, enableDismissFromEndToStart = !selecting) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selecting) toggle() else expanded = !expanded }, onLongClick = { menu = true }).padding(16.dp)) {
                if (selecting) { Checkbox(selected, { toggle() }, Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(java.time.Instant.ofEpochMilli(note.createdAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm")), Modifier.weight(1f), color = Secondary, style = MaterialTheme.typography.bodySmall)
                        Box {
                            Icon(Icons.Outlined.MoreHoriz, "记录菜单", Modifier.size(24.dp).clickable { menu = true }, tint = Secondary)
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("复制记录") }, { menu = false; copy() })
                                DropdownMenuItem({ Text("编辑标签") }, { menu = false; tags() })
                                DropdownMenuItem({ Text("选择记录") }, { menu = false; select() })
                                DropdownMenuItem({ Text("删除记录", color = Red) }, { menu = false; delete() })
                            }
                        }
                    }
                    if (expanded) SelectionContainer { Text(note.text) } else Text(if (note.text.length > 200) note.text.take(200) + "…" else note.text)
                    if (note.text.length > 200) Text(if (expanded) "收起" else "展开全文", style = MaterialTheme.typography.bodySmall, color = Secondary)
                    if (note.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { note.tags.forEach { Text(it, Modifier.background(Indigo.copy(.08f), RoundedCornerShape(5.dp)).clickable(onClick = tags).padding(horizontal = 6.dp, vertical = 2.dp), color = Indigo, style = MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}

@Composable fun TagSheet(state: AppState, vm: AppViewModel, noteIds: Set<String>, close: () -> Unit) {
    val draft = noteIds.isEmpty()
    val initial = remember { if (draft) listOf(state.draft.tags) else state.notes.filter { it.id in noteIds }.map { it.tags } }
    var added by remember { mutableStateOf(setOf<String>()) }
    var removed by remember { mutableStateOf(setOf<String>()) }
    var created by remember { mutableStateOf(setOf<String>()) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var rename by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<String?>(null) }
    val tags = (state.tags + created + initial.flatten()).distinct().filter { it !in hidden && it.contains(search, true) }.sorted()
    fun applyTags() { if (draft) vm.draftTags(((initial.firstOrNull().orEmpty() + added).filterNot { it in removed }).distinct()) else vm.noteTags(noteIds, added, removed); close() }
    SheetPage(if (draft) "草稿标签" else "编辑标签", close, actions = { TextButton(onClick = ::applyTags) { Text("保存", color = Indigo) } }) {
        SearchField(search, { search = it }, "搜索或新建标签")
        if (draft && state.draft.text.isNotBlank()) TextButton(onClick = { vm.recommendTags(state.draft.text) { added = added + it; removed = removed - it.toSet() } }, Modifier.padding(horizontal = 20.dp)) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("推荐标签") }
        if (search.trim().isNotEmpty() && search.trim() !in tags) FormSection { FormRow("新建“${search.trim()}”", icon = Icons.Outlined.Add, onClick = { val tag = search.trim(); created = created + tag; added = added + tag; removed = removed - tag; search = "" }) }
        if (tags.isEmpty()) EmptyState(Icons.Outlined.LocalOffer, "还没有标签", "在上方输入标签名称，即可创建。")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            items(tags, key = { it }) { tag ->
                val selection = when { tag in added -> ToggleableState.On; tag in removed -> ToggleableState.Off; initial.isNotEmpty() && initial.all { tag in it } -> ToggleableState.On; initial.any { tag in it } -> ToggleableState.Indeterminate; else -> ToggleableState.Off }
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).combinedClickable(onClick = { if (selection == ToggleableState.On) { added = added - tag; removed = removed + tag } else { added = added + tag; removed = removed - tag } }, onLongClick = { menu = true }).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TriStateCheckbox(selection, null, colors = CheckboxDefaults.colors(checkedColor = Indigo)); Spacer(Modifier.width(12.dp)); Text(tag, Modifier.weight(1f)); Text(state.notes.count { tag in it.tags }.toString(), style = MaterialTheme.typography.bodySmall, color = Secondary)
                    Box { GlyphButton(Icons.Outlined.MoreHoriz, "管理标签 $tag") { menu = true }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("重命名") }, { menu = false; rename = tag; newName = tag }); DropdownMenuItem({ Text("删除标签", color = Red) }, { menu = false; delete = tag }) } }
                }; Separator()
            }
        }
        Text("点选标签，长按可重命名或删除。重命名为已有标签时会自动合并。", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = Secondary)
    }
    rename?.let { old -> AlertDialog(onDismissRequest = { rename = null }, title = { Text("重命名标签") }, text = { OutlinedTextField(newName, { newName = it }, label = { Text("标签名称") }, singleLine = true) }, confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { if (old != newName.trim()) { hidden = hidden + old; vm.renameTag(old, newName); added = if (old in added) added - old + newName.trim() else added; removed = removed + old; if (initial.any { old in it }) added = added + newName.trim(); }; rename = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { rename = null }) { Text("取消") } }) }
    delete?.let { tag -> ConfirmDelete("删除标签“$tag”？", "将从所有记录和草稿中移除此标签，记录内容会保留。", { delete = null }) { vm.removeTag(tag); hidden = hidden + tag; removed = removed + tag; added = added - tag; created = created - tag } }
}

@Composable fun StatisticsSheet(notes: List<Note>, select: (LocalDate?) -> Unit, close: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val filtered = notes.filter { date == null || it.day == date }
    SheetPage("统计", close) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            FormSection("记录日历") {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlyphButton(Icons.Outlined.ChevronLeft, "上个月") { month = month.minusMonths(1) }
                    Text("${month.year}年${month.monthValue}月", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    GlyphButton(Icons.Outlined.ChevronRight, "下个月") { month = month.plusMonths(1) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Secondary, fontSize = 13.sp) } }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val weeks = (offset + month.lengthOfMonth() + 6) / 7
                repeat(weeks) { week -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { repeat(7) { day ->
                    val value = week * 7 + day - offset + 1
                    Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                        if (value in 1..month.lengthOfMonth()) {
                            val current = month.atDay(value); val count = notes.count { it.day == current }
                            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(if (date == current) Indigo else if (count > 0) Indigo.copy(.08f) else Color.Transparent).clickable { date = current }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(value.toString(), color = if (date == current) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                                if (count > 0) Box(Modifier.size(4.dp).background(if (date == current) Color.White else Indigo, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                } } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TextButton(onClick = { date = null }) { Text("查看全部日期") }; if (date != null) TextButton(onClick = { select(date) }) { Text("查看当天记录", color = Indigo) } }
            }
            FormSection(if (date == null) "总览" else "$date 统计") {
                FormRow("记录", trailing = { Text("${filtered.size}") }); Separator(); FormRow("字数", trailing = { Text(filtered.sumOf { it.text.length }.toString()) })
            }
            FormSection("标签统计") {
                filtered.flatMap { it.tags }.distinct().sorted().forEach { tag -> FormRow(tag, trailing = { Text(filtered.count { tag in it.tags }.toString(), color = Secondary) }); Separator() }
                FormRow("无标签", trailing = { Text(filtered.count { it.tags.isEmpty() }.toString(), color = Secondary) })
            }
        }
    }
}
