package com.flowa.data

import android.content.Context
import android.util.AtomicFile
import com.flowa.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LocalRepository(private val context: Context) : AppRepository {
    private val root = File(context.filesDir, "library").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("environment", Context.MODE_PRIVATE)
    private var demo = preferences.getBoolean("demo", false)
    private val snapshot get() = AtomicFile(File(root, if (demo) "demo-state.json" else "state.json"))
    private val secret get() = SecretStore(context, if (demo) "demo-credentials" else "credentials")
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(AppState())
    override val state = mutable.asStateFlow()
    override suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        if (snapshot.baseFile.exists()) mutable.value = JsonCodec.decode(JSONObject(snapshot.openRead().bufferedReader().use { it.readText() }), secret.read()).copy(demoMode = demo)
        else mutable.value = if (demo) DemoData.create() else AppState()
    } }
    suspend fun setDemo(enabled: Boolean) = withContext(Dispatchers.IO) { mutex.withLock {
        if (enabled == demo) return@withLock
        demo = enabled
        try {
            val next = if (snapshot.baseFile.exists()) JsonCodec.decode(JSONObject(snapshot.openRead().bufferedReader().use { it.readText() }), secret.read()).copy(demoMode = demo) else if (demo) DemoData.create() else AppState()
            check(preferences.edit().putBoolean("demo", demo).commit())
            mutable.value = next
        } catch (e: Exception) { demo = !enabled; throw e }
    } }
    override suspend fun update(transform: (AppState) -> AppState) = withContext(Dispatchers.IO) { mutex.withLock {
        val next = transform(mutable.value)
        if (next.settings.apiKey != mutable.value.settings.apiKey) secret.write(next.settings.apiKey)
        val stream = snapshot.startWrite()
        try { stream.write(JsonCodec.encode(next).toString().toByteArray()); snapshot.finishWrite(stream) } catch (e: Exception) { snapshot.failWrite(stream); throw e }
        mutable.value = next
    } }
    override suspend fun exportNotes(): ByteArray = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream()
        val usedNames = mutableSetOf<String>()
        ZipOutputStream(bytes).use { zip -> state.value.notes.forEach { note ->
            var time = note.createdAt
            while (MarkdownCodec.fileName(time) in usedNames) time += 1000
            val name = MarkdownCodec.fileName(time); usedNames.add(name)
            zip.putNextEntry(ZipEntry(name)); zip.write(MarkdownCodec.encode(note.copy(createdAt = time)).toByteArray()); zip.closeEntry()
        } }
        bytes.toByteArray()
    }
    override suspend fun importNotes(bytes: ByteArray, zip: Boolean): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<Note>()
        if (zip) ZipInputStream(ByteArrayInputStream(bytes)).use { stream ->
            var total = 0L; var entries = 0
            while (true) {
                val entry = stream.nextEntry ?: break
                require(++entries <= 10000) { "压缩包中的文件过多，请分批导入。" }
                val data = if (entry.isDirectory) byteArrayOf() else stream.readBytesLimited(50 * 1024 * 1024)
                total += data.size
                require(total <= 100 * 1024 * 1024) { "压缩包解压后超过 100 MB，请分批导入。" }
                if (!entry.isDirectory && (entry.name.endsWith(".md", true) || entry.name.endsWith(".markdown", true)) && !entry.name.substringAfterLast('/').startsWith('_')) imported.add(MarkdownCodec.decode(data.toString(Charsets.UTF_8)))
                stream.closeEntry()
            }
        } else imported.add(MarkdownCodec.decode(bytes.toString(Charsets.UTF_8)))
        require(imported.isNotEmpty()) { "所选内容中没有 Markdown 记录。" }
        var count = 0; var skipped = 0
        update { state ->
            val notes = state.notes.toMutableList()
            imported.forEach { note ->
                if (notes.any { it.text == note.text && it.tags.toSet() == note.tags.toSet() }) skipped++ else { notes.add(note); count++ }
            }
            state.copy(notes = notes.sortedByDescending { it.createdAt })
        }
        count to skipped
    }
    override suspend fun exportConfiguration(): String = JSONObject().put("schemaVersion", 1).put("exportedAt", java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()).put("appVersion", "1.0.0-android")
        .put("ai", JsonCodec.settings(state.value.settings, true)).put("workflows", JSONObject().put("selectedWorkflowId", state.value.workflows.first().id).put("items", array(state.value.workflows, JsonCodec::workflow))).toString(2)
    override suspend fun importConfiguration(json: String) {
        require(json.toByteArray().size <= 5 * 1024 * 1024) { "配置文件超过 5 MB。" }
        val j = JSONObject(json)
        require(j.getInt("schemaVersion") == 1) { "不支持此配置版本。" }
        val workflows = j.getJSONObject("workflows").getJSONArray("items").objects().filter { it.optString("kind", "manual") == "manual" }.map(JsonCodec::workflow).toMutableList()
        require(workflows.isNotEmpty()) { "配置文件中没有工作流。" }
        if (workflows.none { it.isOpen }) workflows[0] = workflows[0].copy(isOpen = true)
        val settings = JsonCodec.settings(j.getJSONObject("ai"))
        update { it.copy(workflows = workflows, settings = settings, focusedId = null) }
    }
}
fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(output.size() + n <= max) { "文件过大，请缩小后重新导入。" }; output.write(buffer, 0, n) }
    return output.toByteArray()
}
