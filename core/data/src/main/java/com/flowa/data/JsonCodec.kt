package com.flowa.data

import com.flowa.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
internal fun JSONArray.strings() = (0 until length()).map { getString(it) }
internal fun JSONObject.array(key: String) = optJSONArray(key) ?: JSONArray()
internal fun JSONObject.obj(key: String) = optJSONObject(key) ?: JSONObject()
internal fun strings(values: List<String>) = JSONArray(values)
internal fun <T> array(values: List<T>, convert: (T) -> JSONObject) = JSONArray().apply { values.forEach { put(convert(it)) } }
internal object JsonCodec {
    fun draft(d: Draft) = JSONObject().put("text", d.text).put("tags", strings(d.tags))
    fun draft(j: JSONObject) = Draft(j.optString("text"), j.array("tags").strings())
    fun step(s: Step) = JSONObject().put("id", s.id).put("type", s.type.wire).put("isEnabled", s.enabled).put("config", JSONObject().put("aiPrompt", s.prompt).put("agentPrompt", s.prompt).put("httpHost", s.host).put("httpPort", s.port).put("httpServiceName", s.serviceName))
    fun step(j: JSONObject): Step {
        val type = StepType.values().firstOrNull { it.wire == j.getString("type") } ?: error("不支持这个步骤类型")
        val c = j.obj("config")
        return Step(j.optString("id", newId()), type, j.optBoolean("isEnabled", true), c.optString(if (type == StepType.AGENT) "agentPrompt" else "aiPrompt", ""), c.optString("httpHost", "localhost"), c.optInt("httpPort", 9999), c.optString("httpServiceName", ""))
    }
    fun workflow(w: Workflow) = JSONObject().put("id", w.id).put("name", w.name).put("icon", w.icon).put("kind", "manual").put("isOpen", w.isOpen).put("isActive", false).put("syncConfig", JSONObject().put("host", "").put("port", 7788)).put("nodes", array(w.steps, ::step))
    fun workflow(j: JSONObject) = Workflow(j.getString("id"), j.getString("name"), j.optString("icon", "branch"), j.optBoolean("isOpen", true), j.array("nodes").objects().map(::step))
    fun settings(a: AiSettings, key: Boolean) = JSONObject().put("apiToken", if (key) a.apiKey else "").put("baseURLString", a.baseUrl).put("modelID", a.model).put("memoryEnabled", a.memoryEnabled)
    fun settings(j: JSONObject) = AiSettings(j.optString("apiToken", ""), j.optString("baseURLString", AiSettings().baseUrl), j.optString("modelID", AiSettings().model), j.optBoolean("memoryEnabled", true))
    fun encode(s: AppState): JSONObject = JSONObject().put("defaultWorkflowsVersion", 1).put("draft", draft(s.draft)).put("cleared", s.cleared?.let(::draft))
        .put("notes", array(s.notes) { JSONObject().put("id", it.id).put("text", it.text).put("tags", strings(it.tags)).put("createdAt", it.createdAt) })
        .put("workflows", array(s.workflows, ::workflow)).put("focusedId", s.focusedId).put("settings", settings(s.settings, false))
        .put("sessions", array(s.sessions) { session -> JSONObject().put("id", session.id).put("title", session.title).put("updatedAt", session.updatedAt).put("messages", array(session.messages) { JSONObject().put("id", it.id).put("role", it.role).put("text", it.text).put("tools", strings(it.tools)) }) })
        .put("memory", s.memory).put("knownTags", strings(s.knownTags)).put("skills", array(s.skills) { JSONObject().put("name", it.name).put("content", it.content).put("enabled", it.enabled) })
    fun decode(j: JSONObject, key: String): AppState = AppState(
        notes = j.array("notes").objects().map { Note(it.getString("id"), it.getString("text"), it.array("tags").strings(), it.getLong("createdAt")) },
        draft = draft(j.obj("draft")), cleared = j.optJSONObject("cleared")?.let(::draft),
        workflows = j.array("workflows").objects().map(::workflow).let { if (j.optInt("defaultWorkflowsVersion", 0) < 1) DefaultWorkflows.upgrade(it) else it },
        focusedId = j.optString("focusedId").takeUnless { it.isBlank() || it == "null" }, settings = settings(j.obj("settings")).copy(apiKey = key),
        sessions = j.array("sessions").objects().map { session -> ChatSession(session.getString("id"), session.getString("title"), session.array("messages").objects().map { Message(it.getString("id"), it.getString("role"), it.getString("text"), it.array("tools").strings()) }, session.getLong("updatedAt")) },
        memory = j.optString("memory"), skills = j.array("skills").objects().map { Skill(it.getString("name"), it.getString("content"), it.optBoolean("enabled", true)) }, knownTags = j.array("knownTags").strings()
    )
}
