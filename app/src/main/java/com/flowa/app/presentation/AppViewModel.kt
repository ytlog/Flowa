package com.flowa.app.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowa.app.FlowaApplication
import com.flowa.data.readBytesLimited
import com.flowa.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File

data class Execution(val id: String, val step: Int = 0)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application as FlowaApplication
    val repository = graph.repository
    val state = repository.state
    private val _ready = MutableStateFlow(false); val ready = _ready.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    val status = MutableStateFlow<String?>(null)
    val execution = MutableStateFlow<Execution?>(null)
    val models = MutableStateFlow<List<String>>(emptyList())
    val loadingModels = MutableStateFlow(false)
    val chatId = MutableStateFlow<String?>(null)
    val streaming = MutableStateFlow("")
    val toolActivity = MutableStateFlow<List<String>>(emptyList())
    val chatting = MutableStateFlow(false)
    val devices = graph.discovery.devices
    val permissions = graph.permissions
    val draftReset = MutableStateFlow(0)
    var draftResetExpectedText: String? = null
        private set
    fun respondPermission(id: String, allowed: Boolean, session: Boolean) { viewModelScope.launch { permissions.respond(id, allowed, session) } }
    private var chatJob: Job? = null
    private var modelJob: Job? = null
    private val edits = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    init {
        viewModelScope.launch {
            try { repository.load(); _ready.value = true } catch (e: Exception) { error.value = "记录读取失败：${e.message}。请保留应用数据后重试。" }
            for (action in edits) try { action() } catch (e: Exception) { error.value = "保存失败：${e.message}" }
        }
    }
    fun edit(transform: (AppState) -> AppState) { edits.trySend { repository.update(transform) } }
    private suspend fun flushEdits() { val done = CompletableDeferred<Unit>(); edits.send { done.complete(Unit) }; done.await() }
    private fun task(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.message ?: "操作未完成，请重试。" }
    }
    fun draftText(text: String) = edit { it.copy(draft = it.draft.copy(text = text)) }
    fun draftTags(tags: List<String>) = edit { it.copy(draft = it.draft.copy(tags = tags.distinct()), knownTags = (it.knownTags + tags).distinct()) }
    fun clearDraft() { task { flushEdits(); repository.update { s -> if (s.draft.text.isEmpty() && s.draft.tags.isEmpty() && s.cleared != null) s.copy(draft = s.cleared!!, cleared = null) else s.copy(cleared = s.draft, draft = Draft()) }; draftResetExpectedText = null; draftReset.value++ } }
    fun focus(id: String?) = edit { it.copy(focusedId = id) }
    fun run(workflow: Workflow, text: String) {
        if (execution.value != null) return
        workflow.issue()?.let { error.value = it; return }
        execution.value = Execution(workflow.id)
        task {
            try {
                flushEdits()
                val draft = state.value.draft.copy(text = text)
                status.value = graph.runWorkflow(workflow, draft) { execution.value = Execution(workflow.id, it) }; draftResetExpectedText = draft.text; draftReset.value++
            } finally { execution.value = null }
        }
    }
    fun saveWorkflow(workflow: Workflow) = edit { s ->
        val list = (if (s.workflows.any { it.id == workflow.id }) s.workflows.map { if (it.id == workflow.id) workflow else it } else s.workflows + workflow).toMutableList()
        if (list.none { it.isOpen }) list[0] = list[0].copy(isOpen = true)
        s.copy(workflows = list, focusedId = s.focusedId?.takeIf { id -> list.any { it.id == id && it.isOpen } })
    }
    fun deleteWorkflow(id: String) = edit { s ->
        if (s.workflows.size <= 1) s else {
            val list = s.workflows.filterNot { it.id == id }.toMutableList()
            if (list.none { it.isOpen }) list[0] = list[0].copy(isOpen = true)
            s.copy(workflows = list, focusedId = s.focusedId?.takeUnless { it == id })
        }
    }
    fun deleteNotes(ids: Set<String>) = edit { it.copy(notes = it.notes.filterNot { n -> n.id in ids }) }
    fun noteTags(ids: Set<String>, add: Set<String>, remove: Set<String>) = edit { s -> s.copy(notes = s.notes.map { if (it.id in ids) it.copy(tags = (it.tags.filterNot { t -> t in remove } + add).distinct()) else it }, knownTags = (s.knownTags + add).distinct()) }
    fun renameTag(old: String, new: String) { if (new.isBlank()) return; edit { s ->
        fun change(tags: List<String>) = tags.map { if (it == old) new.trim() else it }.distinct()
        s.copy(notes = s.notes.map { it.copy(tags = change(it.tags)) }, draft = s.draft.copy(tags = change(s.draft.tags)), knownTags = change(s.knownTags))
    } }
    fun removeTag(tag: String) = edit { s -> s.copy(notes = s.notes.map { it.copy(tags = it.tags - tag) }, draft = s.draft.copy(tags = s.draft.tags - tag), knownTags = s.knownTags - tag) }
    fun copy(text: String) { task { graph.device.copy(text); status.value = "已复制文本" } }
    fun setDemo(enabled: Boolean) { task { flushEdits(); stopChat(); repository.setDemo(enabled); chatId.value = null; draftResetExpectedText = null; draftReset.value++ } }
    fun settings(settings: AiSettings) {
        if (settings.baseUrl != state.value.settings.baseUrl || settings.apiKey != state.value.settings.apiKey) {
            modelJob?.cancel(); loadingModels.value = false; models.value = emptyList()
        }
        edit { it.copy(settings = settings) }
    }
    fun fetchModels(settings: AiSettings) {
        modelJob?.cancel(); loadingModels.value = true
        modelJob = task { try { models.value = graph.ai.models(settings) } finally { loadingModels.value = false } }
    }
    fun discover(start: Boolean) { if (start) graph.discovery.start() else graph.discovery.stop() }
    fun newChat() { if (!chatting.value) { chatId.value = null; streaming.value = ""; toolActivity.value = emptyList() } }
    fun selectChat(id: String) { if (!chatting.value) { chatId.value = id; streaming.value = ""; toolActivity.value = emptyList() } }
    fun deleteChat(id: String) { if (chatId.value == id) newChat(); edit { it.copy(sessions = it.sessions.filterNot { s -> s.id == id }) } }
    fun send(text: String): Boolean {
        if (text.isBlank() || chatting.value) return false
        if (state.value.settings.apiKey.isBlank()) { error.value = "请先在设置中填写 AI 密钥。输入内容已保留。"; return false }
        val session = state.value.sessions.firstOrNull { it.id == chatId.value } ?: ChatSession(title = text.take(24))
        val messages = session.messages + Message(role = "user", text = text)
        val id = session.id
        chatId.value = id; chatting.value = true; streaming.value = ""; toolActivity.value = emptyList()
        chatJob = task {
            repository.update { it.copy(sessions = listOf(session.copy(messages = messages, updatedAt = System.currentTimeMillis())) + it.sessions.filterNot { s -> s.id == id }) }
            try {
                val result = graph.ai.agent(state.value.settings, messages, { streaming.value = it }, { toolActivity.value = toolActivity.value + it }, id)
                repository.update { it.copy(sessions = it.sessions.map { s -> if (s.id == id) s.copy(messages = messages + Message(role = "assistant", text = result, tools = toolActivity.value)) else s }) }
                streaming.value = ""
            } finally { chatting.value = false }
        }
        return true
    }
    fun stopChat() { chatJob?.cancel(); status.value = "已停止生成" }
    fun recommendTags(text: String, onResult: (List<String>) -> Unit) { task {
        val available = state.value.tags
        require(available.isNotEmpty()) { "请先创建标签。" }
        val answer = graph.ai.complete(state.value.settings, listOf(Message(role = "system", text = "从这些已有标签中选择最多 5 个最相关的，仅输出用逗号分隔的标签，不要创建新标签：${available.joinToString()}"), Message(role = "user", text = text)))
        onResult(answer.split(Regex("[,，、\n]")).map { it.trim().trim('"', '[', ']') }.filter { it in available }.distinct())
    } }
    fun importNotes(uri: Uri) { task {
        val resolver = graph.contentResolver
        val mime = resolver.getType(uri)
        val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytesLimited(50 * 1024 * 1024) } }
        val result = repository.importNotes(bytes, mime?.contains("zip") == true || (bytes.size > 2 && bytes[0] == 80.toByte() && bytes[1] == 75.toByte()))
        status.value = "已导入 ${result.first} 条，跳过 ${result.second} 条重复记录"
    } }
    fun importConfig(uri: Uri) { task {
        val text = withContext(Dispatchers.IO) { graph.contentResolver.openInputStream(uri)!!.use { it.readBytesLimited(5 * 1024 * 1024).toString(Charsets.UTF_8) } }
        repository.importConfiguration(text); status.value = "配置已导入"
    } }
    fun export(config: Boolean, onFile: (File) -> Unit) { task {
        flushEdits()
        val bytes = if (config) repository.exportConfiguration().toByteArray() else repository.exportNotes()
        val file = withContext(Dispatchers.IO) { File(graph.cacheDir, "exports").apply { mkdirs() }.let { File(it, if (config) "vibetaking-config.json" else "vibetaking-notes.zip") }.apply { writeBytes(bytes) } }
        onFile(file)
    } }
    override fun onCleared() { graph.discovery.stop(); super.onCleared() }
}
