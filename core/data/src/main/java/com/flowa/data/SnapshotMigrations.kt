package com.flowa.data

import com.flowa.domain.DefaultWorkflows
import com.flowa.domain.StepType
import org.json.JSONObject

/** Runs only when loading a stored snapshot, never while importing an explicit user configuration. */
internal object SnapshotMigrations {
    const val DEFAULT_WORKFLOWS_VERSION = 1

    fun migrate(source: JSONObject): JSONObject {
        val version = source.optInt("defaultWorkflowsVersion", 0)
        require(version in 0..DEFAULT_WORKFLOWS_VERSION) { "记录来自更新版本，请升级应用后再打开。" }
        if (version == DEFAULT_WORKFLOWS_VERSION) return source

        val defaults = DefaultWorkflows.create()
        val retained = source.array("workflows").objects().map(JsonCodec::workflow).map { workflow ->
            val isOriginal = workflow.name == "默认工作流" && (
                workflow.steps.isEmpty() || workflow.steps.singleOrNull()?.let {
                    it.type == StepType.SAVE && it.enabled
                } == true
            )
            if (isOriginal) defaults.first().copy(id = workflow.id, isOpen = workflow.isOpen)
            else workflow
        }
        val upgraded = retained + defaults.filter { preset ->
            retained.none { it.id == preset.id || it.name == preset.name }
        }
        return JSONObject(source.toString())
            .put("workflows", array(upgraded, JsonCodec::workflow))
            .put("defaultWorkflowsVersion", DEFAULT_WORKFLOWS_VERSION)
    }
}
