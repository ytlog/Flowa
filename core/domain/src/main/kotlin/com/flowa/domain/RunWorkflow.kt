package com.flowa.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface WorkflowResult {
    data object EmptyInput : WorkflowResult
    data object ReturnSent : WorkflowResult
    data class Completed(val saved: Boolean, val copied: Boolean) : WorkflowResult
}

class RunWorkflow(
    private val repository: AppRepository,
    private val ai: AiGateway,
    private val device: DeviceGateway,
) {
    suspend operator fun invoke(
        workflow: Workflow,
        draft: Draft,
        progress: (Int) -> Unit,
    ): WorkflowResult {
        workflow.issue()?.let { throw IllegalArgumentException(it) }
        val steps = workflow.steps.filter { it.enabled }
        if (draft.text.isBlank()) return sendReturnIfNeeded(steps)

        // One execution uses one configuration, even when settings change while awaiting AI.
        val settings = repository.state.value.settings
        var text = draft.text
        var shouldSave = false
        var copied = false
        steps.forEachIndexed { index, step ->
            currentCoroutineContext().ensureActive()
            progress(index + 1)
            when (step.type) {
                StepType.AI -> text = requireOutput(ai.complete(settings, listOf(
                    Message(role = "system", text = step.prompt),
                    Message(role = "user", text = text),
                )))
                StepType.AGENT -> text = requireOutput(ai.agent(settings, listOf(
                    Message(role = "user", text = "任务指令：${step.prompt}\n输入文本：$text\n只输出最终文本。"),
                )))
                StepType.COPY -> {
                    device.copy(text)
                    copied = true
                }
                StepType.SAVE -> shouldSave = true
                StepType.HTTP -> device.post(step, text)
            }
        }

        currentCoroutineContext().ensureActive()
        val note = if (shouldSave) Note(text = text, tags = draft.tags) else null
        repository.update { state ->
            state.copy(
                notes = note?.let { listOf(it) + state.notes } ?: state.notes,
                draft = if (state.draft == draft) draft.clear(keepTags = true) else state.draft,
            )
        }
        return WorkflowResult.Completed(saved = shouldSave, copied = copied)
    }

    private suspend fun sendReturnIfNeeded(steps: List<Step>): WorkflowResult {
        val sendSteps = steps.filter { it.type == StepType.HTTP }
        sendSteps.forEach { device.post(it, "", returnKey = true) }
        return if (sendSteps.isEmpty()) WorkflowResult.EmptyInput else WorkflowResult.ReturnSent
    }

    private fun requireOutput(text: String): String {
        require(text.isNotBlank()) { "AI 未返回有效文本，草稿已保留，请重试。" }
        return text
    }
}
