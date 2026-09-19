@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.flowa.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowa.app.presentation.AppViewModel
import com.flowa.domain.*

@Composable fun WorkflowSheet(state: AppState, vm: AppViewModel, close: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<Workflow?>(null) }
    val workflow = state.workflows.firstOrNull { it.id == selected }
    if (workflow != null) WorkflowDetail(workflow, vm, { selected = null }, close)
    else SheetPage("工作流", close, actions = { GlyphButton(Icons.Outlined.Add, "新建工作流") { name = ""; add = true } }) {
        LazyColumn {
            item { Text("添加步骤，把常用操作连在一起。长按首页工作流按钮可进入专注模式。", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Secondary, style = MaterialTheme.typography.bodySmall) }
            listOf(true, false).forEach { open ->
                val workflows = state.workflows.filter { it.isOpen == open }
                if (workflows.isNotEmpty()) item {
                    FormSection(if (open) "显示在首页" else "已隐藏") {
                        workflows.forEachIndexed { index, item ->
                            var menu by remember { mutableStateOf(false) }
                            FormRow(item.name, "${item.steps.count { it.enabled }} 个启用步骤", symbol(item.icon), onClick = { selected = item.id }, trailing = {
                                Box {
                                    GlyphButton(Icons.Outlined.MoreHoriz, "${item.name}的操作") { menu = true }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem({ Text(if (item.isOpen) "隐藏入口" else "显示入口") }, { menu = false; vm.saveWorkflow(item.copy(isOpen = !item.isOpen)) }, enabled = !item.isOpen || state.workflows.count { it.isOpen } > 1)
                                        DropdownMenuItem({ Text("复制工作流") }, { menu = false; vm.saveWorkflow(item.copy(id = newId(), name = item.name + " 副本", steps = item.steps.map { it.copy(id = newId()) })) })
                                        if (index > 0) DropdownMenuItem({ Text("上移") }, { menu = false; vm.edit { s -> val list = s.workflows.toMutableList(); val a = list.indexOf(item); val b = list.indexOf(workflows[index - 1]); java.util.Collections.swap(list, a, b); s.copy(workflows = list) } })
                                        DropdownMenuItem({ Text("删除工作流", color = Red) }, { menu = false; delete = item }, enabled = state.workflows.size > 1)
                                    }
                                }
                            })
                            if (index != workflows.lastIndex) Separator()
                        }
                    }
                }
            }
        }
    }
    if (add) AlertDialog(onDismissRequest = { add = false }, title = { Text("新建工作流") }, text = { OutlinedTextField(name, { name = it }, label = { Text("工作流名称") }, singleLine = true) }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { val flow = Workflow(name = name.trim()); vm.saveWorkflow(flow); selected = flow.id; add = false }) { Text("创建") } }, dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } })
    delete?.let { flow -> ConfirmDelete("删除“${flow.name}”？", "工作流及其步骤将被删除，记录不会受到影响。", { delete = null }) { vm.deleteWorkflow(flow.id) } }
}

@Composable private fun WorkflowDetail(workflow: Workflow, vm: AppViewModel, back: () -> Unit, close: () -> Unit) {
    var editName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(workflow.name) }
    var picker by remember { mutableStateOf(false) }
    var addStep by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Step?>(null) }
    var deleting by remember { mutableStateOf<Step?>(null) }
    if (editing != null) {
        StepEditor(editing!!, vm, { editing = null }) { step ->
            vm.saveWorkflow(workflow.copy(steps = if (workflow.steps.any { it.id == step.id }) workflow.steps.map { if (it.id == step.id) step else it } else workflow.steps + step)); editing = null
        }
        return
    }
    SheetPage(workflow.name, close, actions = { GlyphButton(Icons.Outlined.ChevronLeft, "返回工作流", onClick = back) }) {
        LazyColumn {
            item {
                FormSection {
                    FormRow("名称", workflow.name, onClick = { name = workflow.name; editName = true }); Separator()
                    FormRow("图标", onClick = { picker = true }, trailing = { Icon(symbol(workflow.icon), "选择图标") }); Separator()
                    FormRow("显示在首页", trailing = { Switch(workflow.isOpen, { vm.saveWorkflow(workflow.copy(isOpen = it)) }) })
                }
            }
            item { Text("步骤", Modifier.padding(start = 36.dp, top = 10.dp, bottom = 8.dp), color = Secondary, style = MaterialTheme.typography.bodySmall) }
            if (workflow.steps.isEmpty()) item { EmptyState(Icons.Outlined.AccountTree, "还没有步骤", "按顺序添加操作，前一步的输出会传给下一步。") }
            itemsIndexed(workflow.steps, key = { _, it -> it.id }) { index, step ->
                val rowHeight = with(LocalDensity.current) { 78.dp.toPx() }
                var offset by remember { mutableFloatStateOf(0f) }
                Surface(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { editing = step }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(symbol(when(step.type) { StepType.AI -> "ai"; StepType.AGENT -> "chat"; else -> step.type.wire }), null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(step.type.title); Text(if (step.type == StepType.HTTP) "${step.host}:${step.port}" else step.prompt.ifBlank { "第 ${index + 1} 步" }, maxLines = 2, color = Secondary, style = MaterialTheme.typography.bodySmall) }
                        Switch(step.enabled, { enabled -> vm.saveWorkflow(workflow.copy(steps = workflow.steps.map { if (it.id == step.id) it.copy(enabled = enabled) else it })) })
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            Icon(Icons.Outlined.DragHandle, "长按拖动步骤", Modifier.size(40.dp).pointerInput(workflow.steps) {
                                detectDragGesturesAfterLongPress(onDragStart = { offset = 0f }, onDrag = { change, amount -> change.consume(); offset += amount.y }, onDragEnd = {
                                    val target = (index + (offset / rowHeight).toInt()).coerceIn(0, workflow.steps.lastIndex)
                                    if (target != index) vm.saveWorkflow(workflow.copy(steps = workflow.steps.toMutableList().apply { add(target, removeAt(index)) }))
                                })
                            }.clickable { menu = true }.padding(8.dp), tint = Secondary)
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("上移") }, { menu = false; vm.saveWorkflow(workflow.copy(steps = workflow.steps.toMutableList().apply { java.util.Collections.swap(this, index, index - 1) })) }, enabled = index > 0)
                                DropdownMenuItem({ Text("下移") }, { menu = false; vm.saveWorkflow(workflow.copy(steps = workflow.steps.toMutableList().apply { java.util.Collections.swap(this, index, index + 1) })) }, enabled = index < workflow.steps.lastIndex)
                                DropdownMenuItem({ Text("删除步骤", color = Red) }, { menu = false; deleting = step })
                            }
                        }
                    }
                }
            }
            item { FormSection(footer = if (workflow.steps.isEmpty()) "" else "步骤从上到下执行，长按右侧手柄拖动排序。") { FormRow("添加步骤", icon = Icons.Outlined.Add, onClick = { addStep = true }) } }
        }
    }
    if (editName) AlertDialog(onDismissRequest = { editName = false }, title = { Text("工作流名称") }, text = { OutlinedTextField(name, { name = it }) }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveWorkflow(workflow.copy(name = name.trim())); editName = false }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editName = false }) { Text("取消") } })
    if (addStep) AlertDialog(onDismissRequest = { addStep = false }, title = { Text("添加步骤") }, text = { Column { StepType.values().forEach { type -> TextButton(onClick = { addStep = false; editing = Step(type = type) }, modifier = Modifier.fillMaxWidth()) { Text(type.title, Modifier.fillMaxWidth()) } } } }, confirmButton = { TextButton(onClick = { addStep = false }) { Text("取消") } })
    if (picker) IconPicker({ picker = false }) { vm.saveWorkflow(workflow.copy(icon = it)); picker = false }
    deleting?.let { step -> ConfirmDelete("删除这个步骤？", "其他步骤和已有记录不会受到影响。", { deleting = null }) { vm.saveWorkflow(workflow.copy(steps = workflow.steps.filterNot { it.id == step.id })) } }
}

@Composable private fun StepEditor(initial: Step, vm: AppViewModel, cancel: () -> Unit, save: (Step) -> Unit) {
    var prompt by remember { mutableStateOf(initial.prompt) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var service by remember { mutableStateOf(initial.serviceName) }
    val devices by vm.devices.collectAsStateWithLifecycle()
    val valid = when(initial.type) { StepType.AI, StepType.AGENT -> prompt.isNotBlank(); StepType.HTTP -> validHost(host.trim(), port.toIntOrNull() ?: 0); else -> true }
    DisposableEffect(initial.type) { if (initial.type == StepType.HTTP) vm.discover(true); onDispose { vm.discover(false) } }
    SheetPage(initial.type.title, cancel, actions = { TextButton(enabled = valid, onClick = { save(initial.copy(prompt = prompt, host = host.trim(), port = port.toIntOrNull() ?: 9999, serviceName = service)) }) { Text("保存步骤", color = if (valid) Indigo else Secondary) } }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (initial.type) {
                StepType.AI, StepType.AGENT -> FormSection("任务指令", if (initial.type == StepType.AI) "AI 按指令改写文本，结果传给下一步。" else "AI 助手可搜索和整理记录，处理结果传给下一步。") { OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 180.dp), label = { Text("填写指令") }, placeholder = { Text("例如：整理成清晰简洁的 Markdown，保留原意。") }) }
                StepType.HTTP -> {
                    FormSection("发送地址", "填写裸主机地址，如 192.168.1.10；不要包含协议或路径。端口范围为 1–65535。") {
                        OutlinedTextField(host, { host = it; service = "" }, Modifier.fillMaxWidth().padding(12.dp), label = { Text("主机") }, singleLine = true)
                        OutlinedTextField(port, { port = it; service = "" }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), label = { Text("端口") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = port.toIntOrNull() !in 1..65535)
                    }
                    FormSection("附近的 Mac", "请让 Mac 与手机处于同一局域网，并打开 Mac 版随心记。") {
                        if (devices.isEmpty()) FormRow("正在查找设备…", "也可以手动填写地址和端口。")
                        devices.forEach { device -> FormRow(device.name, "${device.host}:${device.port}", onClick = { host = device.host; port = device.port.toString(); service = device.name }) }
                        if (service.isNotEmpty()) FormRow("解除绑定", service, onClick = { service = "" })
                    }
                }
                StepType.COPY -> FormSection { FormRow("复制文本", "将运行到这一步的文本复制到系统剪贴板。") }
                StepType.SAVE -> FormSection { FormRow("保存记录", "工作流成功完成后保存最终文本，并保留当前草稿标签。") }
            }
            TextButton(onClick = cancel, Modifier.padding(horizontal = 20.dp)) { Text("取消") }
        }
    }
}

@Composable private fun IconPicker(close: () -> Unit, select: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val icons = listOf("branch" to "工作流", "save" to "保存", "copy" to "复制", "send" to "发送", "ai" to "AI 改写", "chat" to "对话", "star" to "星标", "bolt" to "闪电", "heart" to "喜欢", "book" to "阅读", "pencil" to "编辑", "checkmark" to "完成", "tag" to "标签")
    AlertDialog(onDismissRequest = close, title = { Text("选择图标") }, text = { Column {
        SearchField(search, { search = it }, "搜索图标")
        val shown = icons.filter { search.isBlank() || it.first.contains(search, true) || it.second.contains(search) }
        if (shown.isEmpty()) Text("没有找到图标，试试其他关键词。", color = Secondary)
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 360.dp)) { items(shown) { (key, name) -> Column(Modifier.clickable { select(key) }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(symbol(key), name, Modifier.size(26.dp)); Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1) } } }
    } }, confirmButton = { TextButton(onClick = close) { Text("取消") } })
}
