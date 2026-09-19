package com.flowa.domain

import kotlinx.coroutines.flow.StateFlow

interface AppRepository {
    val state: StateFlow<AppState>
    suspend fun load()
    suspend fun update(transform: (AppState) -> AppState)
    suspend fun exportNotes(): ByteArray
    suspend fun importNotes(bytes: ByteArray, zip: Boolean): Pair<Int, Int>
    suspend fun exportConfiguration(): String
    suspend fun importConfiguration(json: String)
}
interface AiGateway {
    suspend fun complete(settings: AiSettings, messages: List<Message>, onDelta: (String) -> Unit = {}): String
    suspend fun agent(settings: AiSettings, messages: List<Message>, onDelta: (String) -> Unit = {}, onTool: (String) -> Unit = {}, sessionId: String = newId()): String
    suspend fun models(settings: AiSettings): List<String>
}
interface DeviceGateway {
    suspend fun copy(text: String)
    suspend fun post(step: Step, text: String, returnKey: Boolean = false)
}
class RunWorkflow(private val repository: AppRepository, private val ai: AiGateway, private val device: DeviceGateway) {
    suspend operator fun invoke(workflow: Workflow, draft: Draft, progress: (Int) -> Unit): String {
        workflow.issue()?.let { throw IllegalArgumentException(it) }
        if (draft.text.isBlank()) {
            val sends = workflow.steps.filter { it.enabled && it.type == StepType.HTTP }
            sends.forEach { device.post(it, "", true) }
            return if (sends.isEmpty()) "请先写下此刻的想法。" else "已发送回车指令"
        }
        var text = draft.text
        var save = false
        var copied = false
        workflow.steps.filter { it.enabled }.forEachIndexed { index, step ->
            progress(index + 1)
            when (step.type) {
                StepType.AI -> text = ai.complete(repository.state.value.settings, listOf(Message(role = "system", text = step.prompt), Message(role = "user", text = text)))
                StepType.AGENT -> text = ai.agent(repository.state.value.settings, listOf(Message(role = "user", text = "任务指令：${step.prompt}\n输入文本：$text\n只输出最终文本。")))
                StepType.COPY -> { device.copy(text); copied = true }
                StepType.SAVE -> save = true
                StepType.HTTP -> device.post(step, text)
            }
        }
        repository.update { state -> state.copy(notes = if (save) listOf(Note(text = text, tags = draft.tags)) + state.notes else state.notes, draft = if (state.draft == draft) Draft(tags = draft.tags) else state.draft) }
        return when { save && copied -> "已保存记录并复制文本"; save -> "已保存记录"; copied -> "已复制文本"; else -> "工作流已完成" }
    }
}
