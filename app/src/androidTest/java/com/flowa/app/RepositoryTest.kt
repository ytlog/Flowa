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
        assertEquals(1, org.json.JSONObject(file.readText()).getInt("defaultWorkflowsVersion"))
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
    @Test fun legacyEncryptedCredentialMigratesIntoAtomicSnapshot() = runBlocking {
        val context = isolatedContext()
        val first = LocalRepository(context); first.load()
        first.update { it.copy(settings = AiSettings(apiKey = "legacy-test-key"), draft = Draft("旧版本草稿")) }
        val file = File(context.filesDir, "library/state.json")
        val legacy = org.json.JSONObject(file.readText())
        context.getSharedPreferences("credentials", Context.MODE_PRIVATE).edit()
            .putString("token", legacy.getString("encryptedApiKey")).commit()
        legacy.remove("encryptedApiKey")
        file.writeText(legacy.toString())
        val migrated = LocalRepository(context); migrated.load()
        assertEquals("legacy-test-key", migrated.state.value.settings.apiKey)
        assertEquals("旧版本草稿", migrated.state.value.draft.text)
        assertTrue(org.json.JSONObject(file.readText()).has("encryptedApiKey"))
        assertFalse(file.readText().contains("legacy-test-key"))
    }

    @Test fun failedSnapshotWriteKeepsPreviousCredentialAndStateTogether() = runBlocking {
        val context = isolatedContext()
        val repository = LocalRepository(context); repository.load()
        repository.update { it.copy(settings = AiSettings(apiKey = "original-test-key", model = "original")) }
        val root = File(context.filesDir, "library")
        val backup = File(context.filesDir, "library-backup")
        assertTrue(root.renameTo(backup)); assertTrue(root.createNewFile())
        try {
            try {
                repository.update { it.copy(settings = AiSettings(apiKey = "replacement-test-key", model = "replacement")) }
                fail("Writing into a file instead of a directory must fail")
            } catch (_: java.io.IOException) { }
            assertEquals("original-test-key", repository.state.value.settings.apiKey)
        } finally {
            assertTrue(root.delete()); assertTrue(backup.renameTo(root))
        }
        val reloaded = LocalRepository(context); reloaded.load()
        assertEquals("original-test-key", reloaded.state.value.settings.apiKey)
        assertEquals("original", reloaded.state.value.settings.model)
    }

    @Test fun archiveFilenameCollisionsNeverChangeCreationDates() = runBlocking {
        val context = isolatedContext()
        val original = LocalRepository(context); original.load()
        val createdAt = 1700000000000L
        original.update { it.copy(notes = listOf(
            Note(text = "同一秒的第一条", createdAt = createdAt),
            Note(text = "同一秒的第二条", createdAt = createdAt),
        )) }
        val imported = LocalRepository(isolatedContext()); imported.load()
        assertEquals(2 to 0, imported.importNotes(original.exportNotes(), true))
        assertEquals(listOf(createdAt, createdAt), imported.state.value.notes.map { it.createdAt })
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
