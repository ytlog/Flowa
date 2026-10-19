package com.flowa.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowa.app.BuildConfig
import com.flowa.app.presentation.AppViewModel
import com.flowa.domain.*
import com.flowa.data.DeviceCapability
import com.flowa.data.PermissionMode

@Composable fun SettingsSheet(state: AppState, vm: AppViewModel, export: () -> Unit, close: () -> Unit) {
    var key by remember { mutableStateOf(state.settings.apiKey) }
    var base by remember { mutableStateOf(state.settings.baseUrl) }
    var model by remember { mutableStateOf(state.settings.model) }
    var reveal by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var subpage by remember { mutableStateOf<String?>(null) }
    var exportWarning by remember { mutableStateOf(false) }
    var skill by remember { mutableStateOf<Skill?>(null) }
    var skillName by remember { mutableStateOf("") }
    var skillContent by remember { mutableStateOf("") }
    var memory by remember { mutableStateOf(state.memory) }
    val models by vm.models.collectAsStateWithLifecycle()
    val loading by vm.loadingModels.collectAsStateWithLifecycle()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pending = it }
    fun settings() = state.settings.copy(apiKey = key.trim(), baseUrl = base.trim().ifBlank { AiSettings().baseUrl }, model = model.trim().ifBlank { AiSettings().model })
    fun save() { vm.settings(settings()) }
    SheetPage(when(subpage) { "skills" -> "助手技能"; "memory" -> "助手记忆"; "debug" -> "调试"; else -> "设置" }, { save(); close() }, actions = { if (subpage != null) GlyphButton(Icons.Outlined.ChevronLeft, "返回设置") { subpage = null } }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (subpage) {
                "skills" -> {
                    FormSection(footer = "技能是助手可复用的任务指引。可以新建技能，也可以让助手保存到 _skills/名称/SKILL.md。") {
                        state.skills.forEach { item -> FormRow(item.name, onClick = { skill = item; skillName = item.name; skillContent = item.content }, trailing = { Switch(item.enabled, { enabled -> vm.edit { s -> s.copy(skills = s.skills.map { if (it.name == item.name) it.copy(enabled = enabled) else it }) } }) }); Separator() }
                        FormRow("新建技能", icon = Icons.Outlined.Add, onClick = { skill = Skill("", ""); skillName = ""; skillContent = "" })
                    }
                    if (state.skills.isEmpty()) EmptyState(Icons.Outlined.AutoAwesome, "还没有技能", "保存可复用的任务指引，让助手在后续对话中使用。")
                }
                "memory" -> FormSection("记忆内容", "开启记忆后，助手会在后续对话中使用这些偏好。") {
                    OutlinedTextField(memory, { memory = it }, Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 180.dp), label = { Text("偏好与背景") })
                    TextButton(onClick = { vm.edit { it.copy(memory = memory) }; subpage = null }) { Text("保存记忆") }
                }
                "debug" -> FormSection("本机状态") {
                    FormRow("记录", state.notes.size.toString()); Separator(); FormRow("工作流", state.workflows.size.toString()); Separator(); FormRow("对话", state.sessions.size.toString()); Separator(); FormRow("数据状态", "本机原子写入 · API 密钥由 Android Keystore 加密"); Separator(); FormRow("版本", "Flowa Android ${BuildConfig.VERSION_NAME}")
                }
                else -> {
                    FormSection("连接 AI 服务", "密钥加密保存在本机，使用 AI 时相关文本会发送到你设置的服务。普通记录无需配置 AI。") {
                        OutlinedTextField(key, { key = it; vm.settings(settings().copy(apiKey = it.trim())) }, Modifier.fillMaxWidth().padding(12.dp), label = { Text("AI 密钥（API Key）") }, singleLine = true, visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false), trailingIcon = { GlyphButton(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "显示或隐藏密钥") { reveal = !reveal } })
                        OutlinedTextField(base, { base = it; vm.settings(settings().copy(baseUrl = it.trim())) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), label = { Text("AI 服务地址") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false), singleLine = true)
                        if (base != AiSettings().baseUrl) TextButton(onClick = { base = AiSettings().baseUrl; save() }) { Text("恢复默认地址") }
                    }
                    FormSection("模型", "可以手动填写模型 ID。获取列表不会覆盖当前自定义模型。") {
                        OutlinedTextField(model, { model = it; vm.settings(settings().copy(model = it.trim())) }, Modifier.fillMaxWidth().padding(12.dp), label = { Text("模型 ID") }, singleLine = true, keyboardOptions = KeyboardOptions(autoCorrect = false))
                        TextButton(enabled = key.isNotBlank() && !loading, onClick = { save(); vm.fetchModels(settings()) }) { Text(if (loading) "正在获取可用模型…" else "获取可用模型") }
                        models.forEach { item -> FormRow(item, onClick = { model = item; save() }, trailing = { if (model == item) Icon(Icons.Outlined.Check, null, tint = Indigo) }) }
                    }
                    FormSection("AI 助手", "记忆和技能保存在本机，在当前数据集的后续对话中使用。") {
                        FormRow("记住对话中的偏好", trailing = { Switch(state.settings.memoryEnabled, { vm.settings(settings().copy(memoryEnabled = it)) }) }); Separator()
                        FormRow("助手记忆", onClick = { memory = state.memory; subpage = "memory" }); Separator()
                        FormRow("助手技能", state.skills.size.toString(), onClick = { subpage = "skills" })
                    }
                    FormSection("设备访问权限", "设备操作需要 Android 系统权限。“始终允许”仅跳过应用内询问。") {
                        DeviceCapability.values().forEach { capability ->
                            var mode by remember { mutableStateOf(vm.permissions.mode(capability)) }
                            var menu by remember { mutableStateOf(false) }
                            FormRow(capability.label, mode.label, onClick = { menu = true }, trailing = {
                                Box { TextButton(onClick = { menu = true }) { Text(mode.label) }; DropdownMenu(menu, { menu = false }) { PermissionMode.values().forEach { choice -> DropdownMenuItem({ Text(choice.label) }, { mode = choice; vm.permissions.setMode(capability, choice); menu = false }) } } }
                            })
                        }
                    }
                    FormSection("配置迁移", "导出包含全部工作流和 AI 设置，其中 AI 密钥为明文，请只传给你信任的设备。导入会替换当前设置，记录不受影响。") {
                        FormRow("导出配置", icon = Icons.Outlined.IosShare, onClick = { save(); exportWarning = true }); Separator()
                        FormRow("导入配置", icon = Icons.Outlined.FileDownload, onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                    }
                    FormSection("演示模式", "使用独立的示例数据，不影响你的真实记录。") { FormRow("演示模式", trailing = { Switch(state.demoMode, { vm.setDemo(it); close() }) }) }
                    FormSection("存储", "Android 无法直接使用 Apple 的 iCloud 容器。通过 Markdown / ZIP 导入导出与 iOS 交换记录。") { FormRow("本机记录", "已自动保存 ${state.notes.size} 条记录") }
                    FormSection { FormRow("调试", icon = Icons.Outlined.BugReport, onClick = { subpage = "debug" }) }
                    Text("随心记 · Android\n基于 vibetaking 移植 · GPL-3.0", Modifier.fillMaxWidth().padding(24.dp), style = MaterialTheme.typography.bodySmall, color = Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    pending?.let { uri -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("替换当前配置？") }, text = { Text("当前 AI 密钥、服务地址、模型和全部工作流将被替换。此操作无法撤销，记录不受影响。") }, confirmButton = { TextButton(onClick = { vm.importConfig(uri); pending = null; close() }) { Text("导入并替换", color = Red) } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("保留当前配置") } }) }
    if (exportWarning) AlertDialog(onDismissRequest = { exportWarning = false }, title = { Text("导出配置") }, text = { Text("导出文件包含明文 AI 密钥，请只传给你信任的设备。") }, confirmButton = { TextButton(onClick = { exportWarning = false; export() }) { Text("导出") } }, dismissButton = { TextButton(onClick = { exportWarning = false }) { Text("取消") } })
    skill?.let { original -> AlertDialog(onDismissRequest = { skill = null }, title = { Text("编辑技能") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(skillName, { skillName = it }, label = { Text("技能名称") }); Spacer(Modifier.height(12.dp)); OutlinedTextField(skillContent, { skillContent = it }, Modifier.heightIn(min = 180.dp), label = { Text("任务指引") }) } }, confirmButton = { TextButton(enabled = skillName.isNotBlank() && skillContent.isNotBlank(), onClick = { vm.edit { it.copy(skills = it.skills.filterNot { s -> s.name == original.name || s.name == skillName.trim() } + Skill(skillName.trim(), skillContent, original.enabled)) }; skill = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { skill = null }) { Text("取消") } }) }
}
