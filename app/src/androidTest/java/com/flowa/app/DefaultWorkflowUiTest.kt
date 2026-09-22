package com.flowa.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.flowa.data.LocalRepository
import com.flowa.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class DefaultWorkflowUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FlowaApplication
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun requireIsolatedInstall() {
        assertTrue("UI tests must not change the user's library", app.packageName.endsWith(".uitest"))
    }
    @After fun closeActivity() { scenario?.close() }

    private fun launch(state: AppState = AppState()) {
        scenario?.close(); scenario = null
        runBlocking { app.repository.load(); app.repository.update { state } }
        // MIUI rejects app-initiated activity launches while a test package is in the background.
        // Launch from the instrumentation shell, as a user-started foreground app, before Scenario.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "am start -W -n ${app.packageName}/com.flowa.app.MainActivity"
        ).use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("保存记录").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun type(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        compose.onNode(hasSetTextAction()).assertTextEquals(text)
    }
    private fun click(description: String) {
        val node = compose.onNodeWithContentDescription(description)
        var previous: androidx.compose.ui.geometry.Rect? = null
        var stableSince = android.os.SystemClock.elapsedRealtime()
        // Compose idleness does not include the external Baidu keyboard's inset animation.
        compose.waitUntil(5000) {
            val bounds = node.fetchSemanticsNode().boundsInRoot
            if (bounds != previous) { previous = bounds; stableSince = android.os.SystemClock.elapsedRealtime() }
            android.os.SystemClock.elapsedRealtime() - stableSince >= 500
        }
        node.performClick()
    }
    private fun run(name: String) = click(name)
    private fun awaitText(text: String) {
        try { compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) {
            throw AssertionError("Expected: $text; draft=${app.repository.state.value.draft}; notes=${app.repository.state.value.notes}; UI=${compose.onAllNodes(isRoot()).let { roots -> roots.fetchSemanticsNodes().indices.joinToString { roots[it].printToString() } }}", failure)
        }
    }
    private fun awaitWindowFocus() {
        compose.waitUntil(10000) {
            var focused = false
            scenario!!.onActivity { focused = it.hasWindowFocus() }
            focused
        }
    }

    private fun persisted(): AppState = runBlocking { LocalRepository(app).also { it.load() }.state.value }
    private fun settings(server: WorkflowTestServer) = AiSettings("local-test-key", server.url, "test-model")

    @Test fun saveKeepsTagsAndSurvivesActivityRecreation() {
        launch(AppState(draft = Draft(tags = listOf("回归测试"))))
        type("中文记录：今天完成默认工作流测试。")
        run("保存记录"); awaitText("已保存记录")
        val state = persisted()
        assertEquals("中文记录：今天完成默认工作流测试。", state.notes.single().text)
        assertEquals(listOf("回归测试"), state.notes.single().tags)
        assertEquals("", state.draft.text)
        assertEquals(listOf("回归测试"), state.draft.tags)
        scenario!!.recreate()
        compose.onNodeWithContentDescription("记录").performClick()
        awaitText(state.notes.single().text)
    }

    @Test fun clearRestoreAndDraftReload() {
        launch(); type("草稿清除和恢复测试")
        compose.waitUntil(5000) { app.repository.state.value.draft.text == "草稿清除和恢复测试" }
        click("清除草稿")
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("恢复草稿").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(persisted().draft.text.isEmpty())
        click("恢复草稿"); awaitText("草稿清除和恢复测试")
        assertEquals("草稿清除和恢复测试", persisted().draft.text)
        scenario!!.recreate(); awaitText("草稿清除和恢复测试")
    }

    @Test fun missingKeyKeepsDraftForBothAiWorkflows() {
        launch(); type("嗯，这个想法还没有整理好。")
        for (name in listOf("整理口述", "润色文案")) {
            run(name); awaitText("请先在设置中填写 AI 密钥。输入内容已保留。")
            compose.onNodeWithText("继续记录").performClick()
            compose.onNodeWithText("嗯，这个想法还没有整理好。").assertExists()
            assertEquals("嗯，这个想法还没有整理好。", persisted().draft.text)
            assertTrue(persisted().notes.isEmpty())
        }
    }

    @Test fun dictationUsesRealHttpGatewayAndSavesResult() {
        WorkflowTestServer("今天完成默认工作流测试。", delayed = true).use { server ->
            launch(AppState(settings = settings(server))); type("嗯，今天那个完成测试了。")
            run("整理口述")
            compose.waitUntil(10000) { server.request.get() != null }
            assertEquals("嗯，今天那个完成测试了。", server.request.get()!!.getJSONArray("messages").getJSONObject(1).getString("content"))
            server.release.countDown(); awaitText("已保存记录")
            assertEquals("今天完成默认工作流测试。", persisted().notes.single().text)
            assertTrue(persisted().draft.text.isEmpty())
            compose.onNodeWithContentDescription("记录").performClick(); awaitText("今天完成默认工作流测试。")
        }
    }

    @Test fun polishCopiesResultWithoutCreatingNote() {
        WorkflowTestServer("期待与你分享新的灵感。").use { server ->
            launch(AppState(settings = settings(server)))
            val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            awaitWindowFocus()
            val previous = compose.runOnIdle { clipboard.primaryClip }
            try {
                type("有个新的想法，想分享一下。")
                run("润色文案"); awaitText("已复制文本")
                awaitWindowFocus()
                compose.runOnIdle { assertEquals("期待与你分享新的灵感。", clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
                assertTrue(persisted().notes.isEmpty()); assertTrue(persisted().draft.text.isEmpty())
            } finally {
                compose.runOnIdle { if (previous != null) clipboard.setPrimaryClip(previous) else if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", "")) }
            }
        }
    }

    @Test fun invalidKeyKeepsDraftAndAllowsRetry() {
        WorkflowTestServer("", status = "401 Unauthorized").use { server ->
            launch(AppState(settings = settings(server))); type("失败后不能丢失的内容")
            run("整理口述"); awaitText("AI 密钥无效，请在设置中检查。")
            compose.onNodeWithText("继续记录").performClick()
            assertEquals("失败后不能丢失的内容", persisted().draft.text); assertTrue(persisted().notes.isEmpty())
            run("保存记录"); awaitText("已保存记录")
            assertEquals("失败后不能丢失的内容", persisted().notes.single().text)
        }
    }

    @Test fun typingWhileAiRunsPreservesNextDraft() {
        WorkflowTestServer("第一条整理结果", delayed = true).use { server ->
            launch(AppState(settings = settings(server))); type("第一条口述")
            run("整理口述"); compose.waitUntil(10000) { server.request.get() != null }
            type("下一条正在输入")
            compose.waitUntil(5000) { app.repository.state.value.draft.text == "下一条正在输入" }
            server.release.countDown(); awaitText("已保存记录")
            compose.onNodeWithText("下一条正在输入").assertExists()
            assertEquals("下一条正在输入", persisted().draft.text)
            assertEquals("第一条整理结果", persisted().notes.single().text)
        }
    }

    @Test fun editingBackToOriginalDoesNotLetOldWorkflowClearIt() {
        WorkflowTestServer("旧任务的整理结果", delayed = true).use { server ->
            launch(AppState(settings = settings(server))); type("同样的原文")
            run("整理口述"); compose.waitUntil(10000) { server.request.get() != null }
            type("中途改成其他内容"); type("同样的原文")
            compose.waitUntil(5000) { app.repository.state.value.draft.revision >= 3 }
            server.release.countDown(); awaitText("已保存记录")
            compose.onNodeWithText("同样的原文").assertExists()
            assertEquals("同样的原文", persisted().draft.text)
            assertEquals("旧任务的整理结果", persisted().notes.single().text)
        }
    }

    @Test fun changingEnvironmentCancelsPendingWorkflow() {
        WorkflowTestServer("不能写进演示数据的结果", delayed = true).use { server ->
            launch(AppState(settings = settings(server))); type("真实环境草稿")
            run("整理口述"); compose.waitUntil(10000) { server.request.get() != null }
            scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[com.flowa.app.presentation.AppViewModel::class.java].setDemo(true) }
            compose.waitUntil(10000) { app.repository.state.value.demoMode }
            server.release.countDown()
            assertFalse(persisted().notes.any { it.text == "不能写进演示数据的结果" })
            scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[com.flowa.app.presentation.AppViewModel::class.java].setDemo(false) }
            compose.waitUntil(10000) { !app.repository.state.value.demoMode }
            awaitText("真实环境草稿")
            assertTrue(persisted().notes.isEmpty())
        }
    }

    @Test fun chatWriteFailureKeepsInputAndReleasesBusyState() {
        launch(AppState(settings = AiSettings(apiKey = "local-test-key")))
        click("更多操作")
        compose.onNodeWithText("AI 助手").performClick()
        type("发送失败时保留这条消息")
        val root = java.io.File(app.filesDir, "library")
        val backup = java.io.File(app.filesDir, "library-test-backup")
        assertTrue(root.renameTo(backup)); assertTrue(root.createNewFile())
        try {
            click("发送消息"); awaitText("操作未完成")
            compose.onNodeWithText("继续记录").performClick()
            compose.onNode(hasSetTextAction()).assertTextEquals("发送失败时保留这条消息")
            compose.onNodeWithContentDescription("发送消息").assertExists()
            assertTrue(app.repository.state.value.sessions.isEmpty())
        } finally {
            assertTrue(root.delete()); assertTrue(backup.renameTo(root))
        }
    }

    @Test fun emptyAiResultDoesNotConsumeDraft() {
        for (streaming in listOf(true, false)) WorkflowTestServer("", streaming = streaming).use { server ->
            launch(AppState(settings = settings(server))); type("AI 没有结果也要保留")
            run("整理口述"); awaitText("AI 未返回有效文本，草稿已保留，请重试。")
            assertEquals("AI 没有结果也要保留", persisted().draft.text)
            assertTrue(persisted().notes.isEmpty())
        }
    }
}

/** Local SSE fixture exercises networking, ViewModel, storage, clipboard and the Compose UI. */
private class WorkflowTestServer(result: String, status: String = "200 OK", delayed: Boolean = false, streaming: Boolean = true) : AutoCloseable {
    private val socket = ServerSocket(0).apply { soTimeout = 60000 }
    val url = "http://127.0.0.1:${socket.localPort}/v1"
    val request = AtomicReference<JSONObject?>(null)
    val release = CountDownLatch(if (delayed) 1 else 0)
    private val worker = thread(isDaemon = true) {
        try {
            socket.accept().use { connection ->
                connection.soTimeout = 15000
                val input = connection.getInputStream()
                val headers = ByteArrayOutputStream()
                var tail = ""
                while (!tail.endsWith("\r\n\r\n")) {
                    val byte = input.read(); check(byte >= 0); headers.write(byte)
                    tail = (tail + byte.toChar()).takeLast(4)
                }
                val size = headers.toString("UTF-8").lineSequence().first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                val body = ByteArray(size); var read = 0
                while (read < size) { val count = input.read(body, read, size - read); check(count > 0); read += count }
                request.set(JSONObject(body.toString(Charsets.UTF_8)))
                check(release.await(15, TimeUnit.SECONDS))
                val event = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("delta", JSONObject().put("content", result))))
                val bytes = if (streaming) "data: $event\n\ndata: [DONE]\n\n".toByteArray() else
                    JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", result)))).toString().toByteArray()
                val contentType = if (streaming) "text/event-stream" else "application/json"
                connection.getOutputStream().apply {
                    write("HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes); flush()
                }
            }
        } catch (_: java.io.IOException) { /* Closing a fixture after a failed assertion. */ }
    }
    override fun close() { release.countDown(); socket.close(); worker.join(1000) }
}
