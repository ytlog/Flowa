package com.flowa.app.presentation

import com.flowa.domain.WorkflowResult

internal fun WorkflowResult.message(): String = when (this) {
    WorkflowResult.EmptyInput -> "请先写下此刻的想法。"
    WorkflowResult.ReturnSent -> "已发送回车指令"
    is WorkflowResult.Completed -> when {
        saved && copied -> "已保存记录并复制文本"
        saved -> "已保存记录"
        copied -> "已复制文本"
        else -> "工作流已完成"
    }
}
