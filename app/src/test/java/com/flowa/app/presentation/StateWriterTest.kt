package com.flowa.app.presentation

import com.flowa.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StateWriterTest {
    @Test fun snapshotHasExactlyTheEditsQueuedBeforeIt() = runTest {
        val repository = MemoryRepository()
        val writer = StateWriter(backgroundScope, repository, {}, {}, {})
        writer.enqueue { it.copy(draft = it.draft.edit(text = "本次任务")) }
        val input = writer.snapshot()
        writer.enqueue { it.copy(draft = it.draft.edit(text = "下一条")) }
        runCurrent()
        assertEquals("本次任务", input.await().draft.text)
        assertEquals("下一条", repository.state.value.draft.text)
    }

    @Test fun failedAutosaveBlocksDependentActionAndAllowsExplicitRetry() = runTest {
        val repository = MemoryRepository().apply { failWrite = true }
        val failures = mutableListOf<Exception>()
        val writer = StateWriter(backgroundScope, repository, {}, {}, failures::add)
        writer.enqueue { it.copy(draft = it.draft.edit(text = "不能丢的输入")) }
        val input = writer.snapshot()
        runCurrent()
        try { input.await(); fail("Write failure must reach dependent action") } catch (_: IOException) { }
        assertEquals(1, failures.size)
        assertEquals("", repository.state.value.draft.text)
        repository.failWrite = false
        writer.enqueue { it.copy(draft = it.draft.edit(text = "不能丢的输入")) }
        assertEquals("不能丢的输入", writer.snapshot().await().draft.text)
    }

    @Test fun failedLoadNeverOverwritesStorageWithAnEmptyState() = runTest {
        val repository = MemoryRepository().apply { failLoad = true }
        var loadFailed = false
        val writer = StateWriter(backgroundScope, repository, {}, { loadFailed = true }, {})
        val update = writer.submit { it.copy(draft = Draft("不应写入")) }
        runCurrent()
        try { update.await(); fail("A failed load must block writes") } catch (_: IOException) { }
        assertTrue(loadFailed)
        assertEquals(0, repository.writes)
    }

    @Test fun explicitMutationCannotSkipAnEarlierAutosaveFailure() = runTest {
        val repository = MemoryRepository().apply { failWrite = true }
        val writer = StateWriter(backgroundScope, repository, {}, {}, {})
        writer.enqueue { it.copy(draft = Draft("尚未保存")) }
        val clear = writer.submit { it.copy(draft = Draft()) }
        runCurrent()
        try { clear.await(); fail("Clear must not skip a failed autosave") } catch (_: IOException) { }
        assertEquals(1, repository.writes)
    }
}

private class MemoryRepository : AppRepository {
    override val state = MutableStateFlow(AppState())
    var failLoad = false
    var failWrite = false
    var writes = 0
    override suspend fun load() { if (failLoad) throw IOException("load failed") }
    override suspend fun update(transform: (AppState) -> AppState) {
        writes++
        if (failWrite) throw IOException("write failed")
        state.value = transform(state.value)
    }
    override suspend fun exportNotes() = byteArrayOf()
    override suspend fun importNotes(bytes: ByteArray, zip: Boolean) = 0 to 0
    override suspend fun exportConfiguration() = ""
    override suspend fun importConfiguration(json: String) = Unit
}
