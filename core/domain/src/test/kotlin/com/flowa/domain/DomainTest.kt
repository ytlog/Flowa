package com.flowa.domain

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun builtInWorkflowsSaveOrCopyAsAdvertised() = runTest {
        for (flow in DefaultWorkflows.create()) {
            val repo = FakeRepository(); val device = FakeDevice()
            RunWorkflow(repo, FakeAi(), device)(flow, repo.state.value.draft) {}
            when (flow.name) {
                "保存记录" -> { assertEquals("不能丢失", repo.state.value.notes.single().text); assertTrue(device.copies.isEmpty()) }
                "整理口述" -> { assertEquals("改写结果", repo.state.value.notes.single().text); assertTrue(device.copies.isEmpty()) }
                "润色文案" -> { assertTrue(repo.state.value.notes.isEmpty()); assertEquals(listOf("改写结果"), device.copies) }
            }
            assertTrue(repo.state.value.draft.text.isEmpty())
        }
    }

    @Test fun emptyAiOutputPreservesDraftWithoutSavingOrCopying() = runTest {
        for (type in listOf(StepType.AI, StepType.AGENT)) {
            val repo = FakeRepository(); val device = FakeDevice()
            val ai = object : AiGateway {
                override suspend fun complete(settings: AiSettings, messages: List<Message>, onDelta: (String) -> Unit) = "  "
                override suspend fun agent(settings: AiSettings, messages: List<Message>, onDelta: (String) -> Unit, onTool: (String) -> Unit, sessionId: String) = "  "
                override suspend fun models(settings: AiSettings) = emptyList<String>()
            }
            val workflow = Workflow(steps = listOf(Step(type = type, prompt = "整理"), Step(type = StepType.COPY), Step(type = StepType.SAVE)))
            try { RunWorkflow(repo, ai, device)(workflow, repo.state.value.draft) {}; fail("Empty AI result must fail") } catch (_: IllegalArgumentException) { }
            assertEquals("不能丢失", repo.state.value.draft.text)
            assertTrue(repo.state.value.notes.isEmpty()); assertTrue(device.copies.isEmpty())
        }
    }
    @Test fun searchRequiresEveryKeywordAndEveryIncludedTag() {
        val note = Note(text = "今天讨论 Compose 动画", tags = listOf("工作", "灵感"))
        assertTrue(NoteFilter("compose 动画", setOf("工作")).matches(note))
        assertFalse(NoteFilter("compose 日历").matches(note))
        assertFalse(NoteFilter(excluded = setOf("灵感")).matches(note))
        assertFalse(NoteFilter(untagged = true).matches(note))
    }
    @Test fun markdownRoundTripPreservesTagsMultilineAndQuotes() {
        val original = Note(text = "想法：\"中文\"\n\n- 第一步\n```kotlin\nval a = 1\n```", tags = listOf("灵感", "带\"引号", "路径\\文本"), createdAt = 1700000000000)
        val decoded = MarkdownCodec.decode(MarkdownCodec.encode(original))
        assertEquals(original.text, decoded.text); assertEquals(original.tags, decoded.tags); assertEquals(original.createdAt, decoded.createdAt)
    }
    @Test fun importsPlainMarkdownWithoutFrontMatter() { assertEquals("# 标题\n内容", MarkdownCodec.decode("# 标题\n内容").text) }
    @Test fun invalidWorkflowsFailBeforeAnySideEffect() = runTest {
        val repo = FakeRepository(); val device = FakeDevice(); val ai = FakeAi()
        val runner = RunWorkflow(repo, ai, device)
        for (workflow in listOf(Workflow(), Workflow(steps = listOf(Step(type = StepType.AI))))) {
            try { runner(workflow, Draft("不能丢失")) {}; fail("Should reject") } catch (_: IllegalArgumentException) {}
        }
        assertEquals(0, device.copies.size); assertEquals("不能丢失", repo.state.value.draft.text)
    }
    @Test fun pipelineTransformsCopiesThenSavesFinalOutputAndRetainsTags() = runTest {
        val repo = FakeRepository(); repo.state.value = repo.state.value.copy(draft = Draft("原文", listOf("工作")))
        val device = FakeDevice()
        RunWorkflow(repo, FakeAi(), device)(Workflow(steps = listOf(Step(type = StepType.SAVE), Step(type = StepType.AI, prompt = "改写"), Step(type = StepType.COPY))), repo.state.value.draft) {}
        assertEquals("改写结果", repo.state.value.notes.single().text); assertEquals(listOf("工作"), repo.state.value.notes.single().tags)
        assertEquals(listOf("改写结果"), device.copies); assertEquals(Draft(tags = listOf("工作")), repo.state.value.draft)
    }
    @Test fun failureDoesNotConsumeDraftOrCreateRecord() = runTest {
        val repo = FakeRepository(); val device = FakeDevice(fail = true)
        try { RunWorkflow(repo, FakeAi(), device)(Workflow(steps = listOf(Step(type = StepType.SAVE), Step(type = StepType.HTTP))), repo.state.value.draft) {}; fail("Must fail") } catch (_: IllegalStateException) {}
        assertTrue(repo.state.value.notes.isEmpty()); assertEquals("不能丢失", repo.state.value.draft.text)
    }
    @Test fun emptyHttpWorkflowSendsReturnWithoutSavingEmptyRecord() = runTest {
        val repo = FakeRepository(); val device = FakeDevice()
        RunWorkflow(repo, FakeAi(), device)(Workflow(steps = listOf(Step(type = StepType.HTTP))), Draft()) {}
        assertTrue(device.sentReturn); assertTrue(repo.state.value.notes.isEmpty())
    }
    @Test fun newTypingDuringExecutionIsNotCleared() = runTest {
        val repo = FakeRepository()
        RunWorkflow(repo, FakeAi(), FakeDevice())(Workflow(steps = listOf(Step(type = StepType.SAVE))), repo.state.value.draft) { repo.state.value = repo.state.value.copy(draft = Draft("下一条")) }
        assertEquals("下一条", repo.state.value.draft.text); assertEquals("不能丢失", repo.state.value.notes.single().text)
    }
    @Test fun validatesHostAndPort() {
        assertTrue(validHost("192.168.1.2", 7788)); assertTrue(validHost("[::1]", 9999))
        assertFalse(validHost("https://localhost", 80)); assertFalse(validHost("localhost/path", 80)); assertFalse(validHost("localhost", 65536)); assertFalse(validHost("", 80))
    }
}
private class FakeRepository : AppRepository {
    override val state = MutableStateFlow(AppState(draft = Draft("不能丢失")))
    override suspend fun load() {}
    override suspend fun update(transform: (AppState) -> AppState) { state.value = transform(state.value) }
    override suspend fun exportNotes() = byteArrayOf()
    override suspend fun importNotes(bytes: ByteArray, zip: Boolean) = 0 to 0
    override suspend fun exportConfiguration() = ""
    override suspend fun importConfiguration(json: String) {}
}
private class FakeDevice(private val fail: Boolean = false) : DeviceGateway {
    val copies = mutableListOf<String>(); var sentReturn = false
    override suspend fun copy(text: String) { copies.add(text) }
    override suspend fun post(step: Step, text: String, returnKey: Boolean) { if (fail) error("network failed"); sentReturn = returnKey }
}
private class FakeAi : AiGateway {
    override suspend fun complete(settings: AiSettings, messages: List<Message>, onDelta: (String) -> Unit) = "改写结果"
    override suspend fun agent(settings: AiSettings, messages: List<Message>, onDelta: (String) -> Unit, onTool: (String) -> Unit, sessionId: String) = "助手结果"
    override suspend fun models(settings: AiSettings) = emptyList<String>()
}

class CommandLineTest {
    @Test fun quotedArgumentsAndNegativeDatesStayIntact() {
        val words = CommandLine.tokenize("create --title '带 空格的标题' --start -7d --notes \"引号文本\"")
        val options = CommandLine.options(words)
        assertEquals("带 空格的标题", options["title"])
        assertEquals("-7d", options["start"])
        assertEquals("引号文本", options["notes"])
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnterminatedQuotes() { CommandLine.tokenize("set --text 'unfinished") }
}
