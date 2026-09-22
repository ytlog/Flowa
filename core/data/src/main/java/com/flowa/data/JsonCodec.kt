package com.flowa.data

import com.flowa.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
internal fun JSONArray.strings() = (0 until length()).map { getString(it) }
internal fun JSONObject.array(key: String) = optJSONArray(key) ?: JSONArray()
internal fun JSONObject.obj(key: String) = optJSONObject(key) ?: JSONObject()
internal fun strings(values: List<String>) = JSONArray(values)
internal fun <T> array(values: List<T>, convert: (T) -> JSONObject) =
    JSONArray().apply { values.forEach { put(convert(it)) } }

/** JSON mapping only. Version upgrades belong to SnapshotMigrations. */
internal object JsonCodec {
    fun draft(value: Draft): JSONObject = JSONObject()
        .put("text", value.text)
        .put("tags", strings(value.tags))
        .put("revision", value.revision)

    fun draft(json: JSONObject) = Draft(
        text = json.optString("text"),
        tags = json.array("tags").strings(),
        revision = json.optLong("revision", 0),
    )

    fun step(value: Step): JSONObject = JSONObject()
        .put("id", value.id)
        .put("type", value.type.wire)
        .put("isEnabled", value.enabled)
        .put("config", JSONObject()
            .put("aiPrompt", value.prompt)
            .put("agentPrompt", value.prompt)
            .put("httpHost", value.host)
            .put("httpPort", value.port)
            .put("httpServiceName", value.serviceName))

    fun step(json: JSONObject): Step {
        val type = StepType.values().firstOrNull { it.wire == json.getString("type") }
            ?: error("不支持这个步骤类型")
        val config = json.obj("config")
        return Step(
            id = json.optString("id", newId()),
            type = type,
            enabled = json.optBoolean("isEnabled", true),
            prompt = config.optString(if (type == StepType.AGENT) "agentPrompt" else "aiPrompt", ""),
            host = config.optString("httpHost", "localhost"),
            port = config.optInt("httpPort", 9999),
            serviceName = config.optString("httpServiceName", ""),
        )
    }

    fun workflow(value: Workflow): JSONObject = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("icon", value.icon)
        .put("kind", "manual")
        .put("isOpen", value.isOpen)
        .put("isActive", false)
        .put("syncConfig", JSONObject().put("host", "").put("port", 7788))
        .put("nodes", array(value.steps, ::step))

    fun workflow(json: JSONObject) = Workflow(
        id = json.getString("id"),
        name = json.getString("name"),
        icon = json.optString("icon", "branch"),
        isOpen = json.optBoolean("isOpen", true),
        steps = json.array("nodes").objects().map(::step),
    )

    fun settings(value: AiSettings, key: Boolean): JSONObject = JSONObject()
        .put("apiToken", if (key) value.apiKey else "")
        .put("baseURLString", value.baseUrl)
        .put("modelID", value.model)
        .put("memoryEnabled", value.memoryEnabled)

    fun settings(json: JSONObject) = AiSettings(
        apiKey = json.optString("apiToken", ""),
        baseUrl = json.optString("baseURLString", AiSettings().baseUrl),
        model = json.optString("modelID", AiSettings().model),
        memoryEnabled = json.optBoolean("memoryEnabled", true),
    )

    private fun note(value: Note): JSONObject = JSONObject()
        .put("id", value.id)
        .put("text", value.text)
        .put("tags", strings(value.tags))
        .put("createdAt", value.createdAt)

    private fun note(json: JSONObject) = Note(
        id = json.getString("id"),
        text = json.getString("text"),
        tags = json.array("tags").strings(),
        createdAt = json.getLong("createdAt"),
    )

    private fun message(value: Message): JSONObject = JSONObject()
        .put("id", value.id)
        .put("role", value.role)
        .put("text", value.text)
        .put("tools", strings(value.tools))

    private fun message(json: JSONObject) = Message(
        id = json.getString("id"),
        role = json.getString("role"),
        text = json.getString("text"),
        tools = json.array("tools").strings(),
    )

    private fun session(value: ChatSession): JSONObject = JSONObject()
        .put("id", value.id)
        .put("title", value.title)
        .put("updatedAt", value.updatedAt)
        .put("messages", array(value.messages, ::message))

    private fun session(json: JSONObject) = ChatSession(
        id = json.getString("id"),
        title = json.getString("title"),
        messages = json.array("messages").objects().map(::message),
        updatedAt = json.getLong("updatedAt"),
    )

    private fun skill(value: Skill): JSONObject = JSONObject()
        .put("name", value.name)
        .put("content", value.content)
        .put("enabled", value.enabled)

    private fun skill(json: JSONObject) = Skill(
        name = json.getString("name"),
        content = json.getString("content"),
        enabled = json.optBoolean("enabled", true),
    )

    fun encode(state: AppState): JSONObject = JSONObject()
        .put("defaultWorkflowsVersion", SnapshotMigrations.DEFAULT_WORKFLOWS_VERSION)
        .put("draft", draft(state.draft))
        .put("cleared", state.cleared?.let(::draft))
        .put("notes", array(state.notes, ::note))
        .put("workflows", array(state.workflows, ::workflow))
        .put("focusedId", state.focusedId)
        .put("settings", settings(state.settings, false))
        .put("sessions", array(state.sessions, ::session))
        .put("memory", state.memory)
        .put("knownTags", strings(state.knownTags))
        .put("skills", array(state.skills, ::skill))

    fun decode(json: JSONObject, key: String) = AppState(
        notes = json.array("notes").objects().map(::note),
        draft = draft(json.obj("draft")),
        cleared = json.optJSONObject("cleared")?.let(::draft),
        workflows = json.array("workflows").objects().map(::workflow),
        focusedId = json.optString("focusedId").takeUnless { it.isBlank() || it == "null" },
        settings = settings(json.obj("settings")).copy(apiKey = key),
        sessions = json.array("sessions").objects().map(::session),
        memory = json.optString("memory"),
        skills = json.array("skills").objects().map(::skill),
        knownTags = json.array("knownTags").strings(),
    )
}
