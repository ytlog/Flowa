package com.flowa.data

import com.flowa.domain.AiSettings
import com.flowa.domain.AppState
import com.flowa.domain.Workflow
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

internal object ConfigurationCodec {
    data class Configuration(val workflows: List<Workflow>, val settings: AiSettings)

    fun encode(state: AppState): String = JSONObject()
        .put("schemaVersion", 1)
        .put("exportedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
        .put("appVersion", "1.0.0-android")
        .put("ai", JsonCodec.settings(state.settings, true))
        .put("workflows", JSONObject()
            .put("selectedWorkflowId", state.workflows.firstOrNull()?.id)
            .put("items", array(state.workflows, JsonCodec::workflow)))
        .toString(2)

    fun decode(json: String): Configuration {
        require(json.toByteArray().size <= 5 * 1024 * 1024) { "配置文件超过 5 MB。" }
        val document = JSONObject(json)
        require(document.getInt("schemaVersion") == 1) { "不支持此配置版本。" }
        val workflows = document.getJSONObject("workflows").getJSONArray("items").objects()
            .filter { it.optString("kind", "manual") == "manual" }
            .map(JsonCodec::workflow).toMutableList()
        require(workflows.isNotEmpty()) { "配置文件中没有工作流。" }
        require(workflows.map { it.id }.distinct().size == workflows.size) { "配置文件中包含重复的工作流 ID。" }
        if (workflows.none { it.isOpen }) workflows[0] = workflows[0].copy(isOpen = true)
        return Configuration(workflows, JsonCodec.settings(document.getJSONObject("ai")))
    }
}
