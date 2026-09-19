package com.flowa.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
data class Note(val id: String = newId(), val text: String = "", val tags: List<String> = emptyList(), val createdAt: Long = System.currentTimeMillis()) {
    val day: LocalDate get() = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
}
data class Draft(val text: String = "", val tags: List<String> = emptyList())
enum class StepType(val wire: String, val title: String) {
    AI("ai_process", "AI 改写"), AGENT("agent", "AI 助手"), COPY("copy", "复制文本"), SAVE("save", "保存记录"), HTTP("http_post", "发送到 Mac 或网址")
}
data class Step(val id: String = newId(), val type: StepType, val enabled: Boolean = true, val prompt: String = "", val host: String = "localhost", val port: Int = 9999, val serviceName: String = "")
data class Workflow(val id: String = newId(), val name: String = "默认工作流", val icon: String = "branch", val isOpen: Boolean = true, val steps: List<Step> = emptyList()) {
    fun issue(): String? {
        val active = steps.filter { it.enabled }
        if (active.isEmpty()) return "还没有启用的步骤。请添加并启用至少一个步骤，再运行。"
        active.forEach {
            if (it.type in listOf(StepType.AI, StepType.AGENT) && it.prompt.isBlank()) return "请先填写“${it.type.title}”步骤的指令。草稿已保留。"
            if (it.type == StepType.HTTP && !validHost(it.host, it.port)) return "请填写有效的裸主机地址和 1–65535 之间的端口。"
        }
        return null
    }
}
fun validHost(host: String, port: Int): Boolean = host.isNotBlank() && port in 1..65535 && !host.contains(Regex("[\\s/#?@]")) && !host.contains("://") && (!host.contains(':') || host.startsWith('['))
data class AiSettings(val apiKey: String = "", val baseUrl: String = "https://api.infingrow.asia/v1", val model: String = "gpt-5.5", val memoryEnabled: Boolean = true)
data class Message(val id: String = newId(), val role: String, val text: String, val tools: List<String> = emptyList())
data class ChatSession(val id: String = newId(), val title: String = "新对话", val messages: List<Message> = emptyList(), val updatedAt: Long = System.currentTimeMillis())
data class Skill(val name: String, val content: String, val enabled: Boolean = true)
data class AppState(val demoMode: Boolean = false, val notes: List<Note> = emptyList(), val draft: Draft = Draft(), val cleared: Draft? = null, val workflows: List<Workflow> = DefaultWorkflows.create(), val focusedId: String? = null, val settings: AiSettings = AiSettings(), val sessions: List<ChatSession> = emptyList(), val memory: String = "", val skills: List<Skill> = emptyList(), val knownTags: List<String> = emptyList()) {
    val tags: List<String> get() = (knownTags + notes.flatMap { it.tags } + draft.tags).distinct().sorted()
}
data class NoteFilter(val query: String = "", val included: Set<String> = emptySet(), val excluded: Set<String> = emptySet(), val untagged: Boolean = false, val date: LocalDate? = null) {
    fun matches(note: Note): Boolean {
        val haystack = (note.text + " " + note.tags.joinToString(" ")).lowercase()
        return query.trim().split(Regex("\\s+")).all { haystack.contains(it.lowercase()) } && included.all { it in note.tags } && excluded.none { it in note.tags } && (!untagged || note.tags.isEmpty()) && (date == null || note.day == date)
    }
}
