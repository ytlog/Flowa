package com.flowa.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.flowa.data.LocalRepository
import com.flowa.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RepositoryTest {
    private fun isolatedContext(): Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        val id = newId()
        override fun getFilesDir() = File(super.getCacheDir(), "test-$id").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("test-$id-$name", mode)
    }
    @Test fun upgradePreservesUserDataAndDoesNotRecreateDeletedPresets() = runBlocking {
        val context = isolatedContext(); val old = LocalRepository(context); old.load()
        val legacy = Workflow(); val custom = Workflow(name = "我的流程", steps = listOf(Step(type = StepType.COPY)))
        old.update { it.copy(workflows = listOf(legacy, custom), focusedId = legacy.id, draft = Draft("现有草稿"), notes = listOf(Note(text = "现有记录"))) }
        val file = File(context.filesDir, "library/state.json")
        val json = org.json.JSONObject(file.readText()); json.remove("defaultWorkflowsVersion"); file.writeText(json.toString())
        val upgraded = LocalRepository(context); upgraded.load()
        val state = upgraded.state.value
        assertEquals(listOf("保存记录", "我的流程", "整理口述", "润色文案"), state.workflows.map { it.name })
        assertEquals(legacy.id, state.workflows.first().id); assertEquals(legacy.id, state.focusedId)
        assertEquals(custom, state.workflows[1]); assertEquals("现有草稿", state.draft.text); assertEquals("现有记录", state.notes.single().text)
        upgraded.update { it.copy(workflows = it.workflows.filterNot { flow -> flow.name == "润色文案" }) }
        val reloaded = LocalRepository(context); reloaded.load()
        assertEquals(upgraded.state.value, reloaded.state.value)
    }
    @Test fun processReloadPreservesDraftAndEncryptedSettings() = runBlocking {
        val context = isolatedContext(); val first = LocalRepository(context); first.load()
        first.update { it.copy(draft = Draft("没有丢失的草稿", listOf("工作")), settings = AiSettings(apiKey = "test-secret")) }
        val second = LocalRepository(context); second.load()
        assertEquals(first.state.value.draft, second.state.value.draft)
        assertEquals("test-secret", second.state.value.settings.apiKey)
        assertFalse(File(context.filesDir, "library/state.json").readText().contains("test-secret"))
    }
    @Test fun archiveRoundTripAndDuplicateImport() = runBlocking {
        val a = LocalRepository(isolatedContext()); a.load()
        a.update { it.copy(notes = listOf(Note(text = "一条记录", tags = listOf("中文")), Note(text = "另一条记录"))) }
        val bytes = a.exportNotes(); val b = LocalRepository(isolatedContext()); b.load()
        assertEquals(2 to 0, b.importNotes(bytes, true)); assertEquals(0 to 2, b.importNotes(bytes, true))
        assertEquals(a.state.value.notes.map { it.text }.toSet(), b.state.value.notes.map { it.text }.toSet())
    }
    @Test fun iosConfigurationImportsAllFiveStepTypes() = runBlocking {
        val a = LocalRepository(isolatedContext()); a.load()
        val flow = Workflow(name = "测试流程", steps = StepType.values().map { Step(type = it, prompt = "处理") })
        a.update { it.copy(workflows = listOf(flow), settings = AiSettings(apiKey = "test-key", model = "custom-model")) }
        val json = a.exportConfiguration(); assertFalse(org.json.JSONObject(json).getString("exportedAt").contains(".")); val b = LocalRepository(isolatedContext()); b.load(); b.importConfiguration(json)
        assertEquals(flow, b.state.value.workflows.single()); assertEquals("custom-model", b.state.value.settings.model)
        try { b.importConfiguration("{\"schemaVersion\":99}"); fail("must fail") } catch (_: IllegalArgumentException) { }
        assertEquals(flow, b.state.value.workflows.single())
    }
}
