package com.flowa.data

import android.content.Context
import com.flowa.domain.AppRepository
import com.flowa.domain.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LocalRepository(context: Context) : AppRepository {
    private val root = File(context.filesDir, "library").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("environment", Context.MODE_PRIVATE)
    private val realStore = SnapshotStore(File(root, "state.json"), SecretStore(context, "credentials"))
    private val demoStore = SnapshotStore(File(root, "demo-state.json"), SecretStore(context, "demo-credentials"))
    private var demo = preferences.getBoolean("demo", false)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(AppState())
    override val state = mutable.asStateFlow()

    override suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { mutable.value = readEnvironment(demo) }
    }

    private fun store(isDemo: Boolean) = if (isDemo) demoStore else realStore

    private fun readEnvironment(isDemo: Boolean): AppState {
        val snapshot = store(isDemo)
        val loaded = snapshot.read() ?: (if (isDemo) DemoData.create() else AppState()).also(snapshot::write)
        return loaded.copy(demoMode = isDemo)
    }

    suspend fun setDemo(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (enabled == demo) return@withLock
            val next = readEnvironment(enabled)
            check(preferences.edit().putBoolean("demo", enabled).commit()) { "环境切换失败，请重试。" }
            demo = enabled
            mutable.value = next
        }
    }

    override suspend fun update(transform: (AppState) -> AppState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(mutable.value)
            require(next.demoMode == demo) { "请通过环境切换入口切换演示数据。" }
            if (next != mutable.value) {
                store(demo).write(next)
                mutable.value = next
            }
        }
    }

    override suspend fun exportNotes(): ByteArray = withContext(Dispatchers.IO) {
        NoteArchive.encode(state.value.notes)
    }

    override suspend fun importNotes(bytes: ByteArray, zip: Boolean): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val imported = NoteArchive.decode(bytes, zip)
        var added = 0
        var skipped = 0
        update { state ->
            val notes = state.notes.toMutableList()
            val existing = notes.map { it.text to it.tags.toSet() }.toMutableSet()
            imported.forEach { note ->
                if (existing.add(note.text to note.tags.toSet())) {
                    notes.add(note)
                    added++
                } else skipped++
            }
            state.copy(notes = notes.sortedByDescending { it.createdAt })
        }
        added to skipped
    }

    override suspend fun exportConfiguration(): String = ConfigurationCodec.encode(state.value)

    override suspend fun importConfiguration(json: String) {
        val configuration = ConfigurationCodec.decode(json)
        update { it.copy(workflows = configuration.workflows, settings = configuration.settings, focusedId = null) }
    }
}
