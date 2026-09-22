package com.flowa.app.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowa.app.FlowaApplication
import com.flowa.data.readBytesLimited
import com.flowa.domain.*
import kotlinx.coroutines.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.*
import java.io.File

data class Execution(val id: String, val step: Int = 0)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application as FlowaApplication
    val repository = graph.repository
    val state = repository.state
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
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
    private val _draftField = MutableStateFlow(TextFieldValue())
    val draftField = _draftField.asStateFlow()
    private var editorVersion = 0L
    private var switchingEnvironment = false
    private var operationJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var operationScope = CoroutineScope(viewModelScope.coroutineContext + operationJob)
    private var chatJob: Job? = null
    private var modelJob: Job? = null
    private val writer = StateWriter(
        scope = viewModelScope,
        repository = repository,
        onLoaded = { state ->
            replaceEditorText(state.draft.text)
            _ready.value = true
        },
        onLoadFailure = { error.value = "记录读取失败：${it.message}。请保留应用数据后重试。" },
        onWriteFailure = { error.value = "保存失败：${it.message}" },
    )

    fun respondPermission(id: String, allowed: Boolean, session: Boolean) {
        viewModelScope.launch { permissions.respond(id, allowed, session) }
    }

    fun edit(transform: (AppState) -> AppState) = writer.enqueue(transform)
    private suspend fun flushEdits() = writer.flush()

    private fun replaceEditorText(text: String) {
        if (_draftField.value.text != text) {
            editorVersion++
            _draftField.value = TextFieldValue(text, TextRange(text.length))
        }
    }

    private fun task(block: suspend () -> Unit) = operationScope.launch { reportFailure(block) }

    private suspend fun reportFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error.value = failure.message ?: "操作未完成，请重试。"
        }
    }
    fun updateDraft(value: TextFieldValue) {
        if (switchingEnvironment) return
        val changed = value.text != _draftField.value.text
        _draftField.value = value
        if (changed) {
            editorVersion++
            edit { it.copy(draft = it.draft.edit(text = value.text)) }
        }
    }

    fun draftTags(tags: List<String>) = edit {
        it.copy(draft = it.draft.edit(tags = tags), knownTags = (it.knownTags + tags).distinct())
    }

    fun clearDraft() {
        val version = editorVersion
        val text = _draftField.value.text
        val saved = writer.submit { state ->
            val draft = state.draft.edit(text = text)
            val cleared = state.cleared
            if (draft.text.isEmpty() && draft.tags.isEmpty() && cleared != null) {
                state.copy(draft = draft.restore(cleared), cleared = null)
            } else {
                state.copy(cleared = draft, draft = draft.clear())
            }
        }
        task {
            val next = saved.await()
            if (editorVersion == version) replaceEditorText(next.draft.text)
        }
    }

    fun focus(id: String?) = edit { it.copy(focusedId = id) }

    fun run(workflow: Workflow) {
        if (execution.value != null || switchingEnvironment) return
        workflow.issue()?.let { error.value = it; return }
        val version = editorVersion
        val text = _draftField.value.text
        // Capture a position in the write queue now, before subsequent keystrokes are enqueued.
        // Re-persist the editor value so a retry can recover from a previous autosave failure.
        edit { it.copy(draft = it.draft.edit(text = text)) }
        val input = writer.snapshot()
        execution.value = Execution(workflow.id)
        task {
            try {
                val draft = input.await().draft
                val result = graph.runWorkflow(workflow, draft) {
                    execution.value = Execution(workflow.id, it)
                }
                status.value = result.message()
                if (editorVersion == version) replaceEditorText(state.value.draft.text)
            } finally {
                execution.value = null
            }
        }
    }

    fun saveWorkflow(workflow: Workflow) = edit { state ->
        val workflows = if (state.workflows.any { it.id == workflow.id }) {
            state.workflows.map { if (it.id == workflow.id) workflow else it }
        } else state.workflows + workflow
        val visible = ensureVisibleWorkflow(workflows)
        state.copy(
            workflows = visible,
            focusedId = state.focusedId?.takeIf { id -> visible.any { it.id == id && it.isOpen } },
        )
    }

    fun deleteWorkflow(id: String) = edit { state ->
        if (state.workflows.size <= 1) state else state.copy(
            workflows = ensureVisibleWorkflow(state.workflows.filterNot { it.id == id }),
            focusedId = state.focusedId?.takeUnless { it == id },
        )
    }

    private fun ensureVisibleWorkflow(workflows: List<Workflow>): List<Workflow> =
        if (workflows.any { it.isOpen }) workflows
        else workflows.mapIndexed { index, workflow -> if (index == 0) workflow.copy(isOpen = true) else workflow }

    fun deleteNotes(ids: Set<String>) = edit { state ->
        state.copy(notes = state.notes.filterNot { it.id in ids })
    }

    fun noteTags(ids: Set<String>, add: Set<String>, remove: Set<String>) = edit { state ->
        state.copy(
            notes = state.notes.map { note ->
                if (note.id in ids) note.copy(tags = (note.tags.filterNot { it in remove } + add).distinct())
                else note
            },
            knownTags = (state.knownTags + add).distinct(),
        )
    }

    fun renameTag(old: String, new: String) {
        if (new.isBlank()) return
        edit { state ->
            fun rename(tags: List<String>) = tags.map { if (it == old) new.trim() else it }.distinct()
            state.copy(
                notes = state.notes.map { it.copy(tags = rename(it.tags)) },
                draft = state.draft.edit(tags = rename(state.draft.tags)),
                knownTags = rename(state.knownTags),
            )
        }
    }

    fun removeTag(tag: String) = edit { state ->
        state.copy(
            notes = state.notes.map { it.copy(tags = it.tags - tag) },
            draft = state.draft.edit(tags = state.draft.tags - tag),
            knownTags = state.knownTags - tag,
        )
    }

    fun copy(text: String) {
        task {
            graph.device.copy(text)
            status.value = "已复制文本"
        }
    }

    fun setDemo(enabled: Boolean) {
        if (switchingEnvironment || state.value.demoMode == enabled) return
        switchingEnvironment = true
        _ready.value = false
        viewModelScope.launch {
            try {
                reportFailure {
                    // Every asynchronous operation belongs to the environment where it started.
                    operationJob.cancelAndJoin()
                    flushEdits()
                    repository.setDemo(enabled)
                    chatId.value = null
                    streaming.value = ""
                    toolActivity.value = emptyList()
                    status.value = null
                    replaceEditorText(state.value.draft.text)
                }
            } finally {
                operationJob = SupervisorJob(viewModelScope.coroutineContext[Job])
                operationScope = CoroutineScope(viewModelScope.coroutineContext + operationJob)
                switchingEnvironment = false
                _ready.value = true
            }
        }
    }

    fun settings(settings: AiSettings) {
        if (settings.baseUrl != state.value.settings.baseUrl || settings.apiKey != state.value.settings.apiKey) {
            modelJob?.cancel()
            loadingModels.value = false
            models.value = emptyList()
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
    fun send(text: String, onPersisted: () -> Unit): Boolean {
        if (text.isBlank() || chatting.value || switchingEnvironment) return false
        if (state.value.settings.apiKey.isBlank()) {
            error.value = "请先在设置中填写 AI 密钥。输入内容已保留。"
            return false
        }
        val session = state.value.sessions.firstOrNull { it.id == chatId.value }
            ?: ChatSession(title = text.take(24))
        val messages = session.messages + Message(role = "user", text = text)
        val id = session.id
        chatId.value = id
        chatting.value = true
        streaming.value = ""
        toolActivity.value = emptyList()
        chatJob = task {
            try {
                flushEdits()
                repository.update { state ->
                    state.copy(sessions = listOf(session.copy(messages = messages, updatedAt = System.currentTimeMillis())) +
                        state.sessions.filterNot { it.id == id })
                }
                onPersisted()
                val updates = CoroutineScope(currentCoroutineContext())
                val result = graph.ai.agent(
                    settings = state.value.settings,
                    messages = messages,
                    onDelta = { partial -> updates.launch { streaming.value = partial } },
                    onTool = { title -> updates.launch { toolActivity.value = toolActivity.value + title } },
                    sessionId = id,
                )
                repository.update { state ->
                    state.copy(sessions = state.sessions.map { current ->
                        if (current.id == id) current.copy(messages = messages +
                            Message(role = "assistant", text = result, tools = toolActivity.value))
                        else current
                    })
                }
                streaming.value = ""
            } finally {
                chatting.value = false
            }
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
