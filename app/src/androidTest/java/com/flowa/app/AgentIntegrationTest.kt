package com.flowa.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.flowa.data.*
import com.flowa.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

class AgentIntegrationTest {
    private fun context(): Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        val id = newId()
        override fun getFilesDir() = File(super.getCacheDir(), "agent-test-$id").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("agent-test-$id-$name", mode)
    }
    @Test fun chatCompletionConsumesSseDeltas() = runBlocking {
        val ctx = context(); val repository = LocalRepository(ctx); repository.load()
        val gateway = HttpAiGateway(repository, DeviceToolEngine(ctx, DevicePermissions(ctx)))
        val server = MockServer(listOf("text/event-stream" to "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"世界\"}}]}\n\ndata: [DONE]\n\n"))
        try {
            val updates = mutableListOf<String>()
            val result = gateway.complete(AiSettings("fake-key", server.url), listOf(Message(role = "user", text = "test"))) { updates.add(it) }
            assertEquals("你好世界", result); assertEquals(listOf("你好", "你好世界"), updates)
        } finally { server.close() }
    }
    @Test fun responsesToolLoopSavesRecordAndReturnsAnswer() = runBlocking {
        val ctx = context(); val repository = LocalRepository(ctx); repository.load()
        val gateway = HttpAiGateway(repository, DeviceToolEngine(ctx, DevicePermissions(ctx)))
        val server = MockServer(listOf(
            "application/json" to """{"output":[{"type":"function_call","name":"save_note","call_id":"call_1","arguments":"{\"text\":\"来自工具的记录\",\"tags\":\"工作\",\"tool_title\":\"保存记录\"}"}]}""",
            "application/json" to """{"output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"已经保存"}]}]}"""
        ))
        try {
            val titles = mutableListOf<String>()
            val result = gateway.agent(AiSettings("fake-key", server.url), listOf(Message(role = "user", text = "保存记录")), onTool = { titles.add(it) })
            assertEquals("已经保存", result); assertEquals("来自工具的记录", repository.state.value.notes.single().text); assertEquals(listOf("保存记录"), titles)
        } finally { server.close() }
    }
    @Test fun deviceAuthorizationQueuesAndScopesSessionGrant() = runBlocking {
        val permissions = DevicePermissions(context())
        val first = async { permissions.authorize(DeviceCapability.CALENDAR, "list", "session-a", emptyList()) }
        val request = withTimeout(3000) { permissions.pending.first { it != null }!! }
        permissions.respond(request.id, true, true); first.await()
        withTimeout(1000) { permissions.authorize(DeviceCapability.CALENDAR, "list", "session-a", emptyList()) }
        val next = async { permissions.authorize(DeviceCapability.CALENDAR, "list", "session-b", emptyList()) }
        val request2 = withTimeout(3000) { permissions.pending.first { it?.sessionId == "session-b" }!! }
        assertNotEquals(request.id, request2.id)
        next.cancelAndJoin(); assertNull(permissions.pending.value)
    }
    @Test fun demoDataNeverOverwritesRealDraft() = runBlocking {
        val repo = LocalRepository(context()); repo.load(); repo.update { it.copy(draft = Draft("真实草稿")) }
        repo.setDemo(true); assertTrue(repo.state.value.demoMode); repo.update { it.copy(draft = Draft("演示草稿")) }
        repo.setDemo(false); assertEquals("真实草稿", repo.state.value.draft.text)
        repo.setDemo(true); assertEquals("演示草稿", repo.state.value.draft.text)
    }
}
private class MockServer(responses: List<Pair<String, String>>) : AutoCloseable {
    private val server = ServerSocket(0).apply { soTimeout = 15000 }
    val url = "http://127.0.0.1:${server.localPort}/v1"
    private val worker = thread(isDaemon = true) {
        responses.forEach { (type, body) ->
            server.accept().use { socket ->
                socket.soTimeout = 10000
                val reader = socket.getInputStream().bufferedReader()
                var size = 0
                while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; if (line.startsWith("Content-Length:", true)) size = line.substringAfter(':').trim().toInt() }
                // Consume request bytes using UTF-8 bytes, not character count. Headers were ASCII;
                // buffered reader already owns the body, so read until JSON is complete by braces.
                if (size > 0) { var depth = 0; var quoted = false; var escaped = false; do { val c = reader.read(); if (c < 0) break; val ch = c.toChar(); if (escaped) escaped = false else if (ch == '\\' && quoted) escaped = true else if (ch == '"') quoted = !quoted else if (!quoted && ch == '{') depth++ else if (!quoted && ch == '}') depth-- } while (depth > 0) }
                val bytes = body.toByteArray()
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                socket.getOutputStream().write(bytes); socket.getOutputStream().flush()
            }
        }
    }
    override fun close() { server.close(); worker.join(1000) }
}
