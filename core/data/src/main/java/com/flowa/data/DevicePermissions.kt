package com.flowa.data

import android.content.Context
import com.flowa.domain.newId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DeviceCapability(val label: String) { CALENDAR("日历"), REMINDERS("提醒事项"), CLIPBOARD("剪贴板") }
enum class PermissionMode(val label: String) { ASK("每次询问"), ALLOW("始终允许"), DENY("不允许") }
data class DevicePermissionRequest(val id: String = newId(), val capability: DeviceCapability, val command: String, val sessionId: String, val androidPermissions: List<String>, val result: CompletableDeferred<Boolean> = CompletableDeferred())
class DevicePermissions(context: Context) {
    private val prefs = context.getSharedPreferences("device-permissions", Context.MODE_PRIVATE)
    private val queue = mutableListOf<DevicePermissionRequest>()
    private val mutex = Mutex()
    private val allowedSessions = mutableSetOf<Pair<String, DeviceCapability>>()
    private val mutable = MutableStateFlow<DevicePermissionRequest?>(null)
    val pending = mutable.asStateFlow()
    fun mode(capability: DeviceCapability) = runCatching { PermissionMode.valueOf(prefs.getString(capability.name, "ASK")!!) }.getOrDefault(PermissionMode.ASK)
    fun setMode(capability: DeviceCapability, mode: PermissionMode) { prefs.edit().putString(capability.name, mode.name).apply() }
    suspend fun authorize(capability: DeviceCapability, command: String, sessionId: String, missingPermissions: List<String>) {
        val request = DevicePermissionRequest(capability = capability, command = command, sessionId = sessionId, androidPermissions = missingPermissions)
        mutex.withLock {
            check(mode(capability) != PermissionMode.DENY) { "${capability.label}访问已关闭，可在设置中更改。" }
            if (missingPermissions.isEmpty() && (mode(capability) == PermissionMode.ALLOW || sessionId to capability in allowedSessions)) return
            queue.add(request); mutable.value = queue.first()
        }
        try { check(request.result.await()) { "用户未允许访问${capability.label}，请在不访问此数据的情况下继续。" } }
        finally { withContext(NonCancellable) { mutex.withLock { queue.remove(request); mutable.value = queue.firstOrNull() } } }
    }
    suspend fun respond(id: String, allowed: Boolean, session: Boolean = false) = mutex.withLock {
        val request = queue.firstOrNull { it.id == id } ?: return@withLock
        if (allowed && session) allowedSessions.add(request.sessionId to request.capability)
        if (allowed && session) queue.filter { it.sessionId == request.sessionId && it.capability == request.capability && it.androidPermissions.isEmpty() }.forEach { it.result.complete(true) }
        request.result.complete(allowed)
        queue.removeAll { it.result.isCompleted }
        mutable.value = queue.firstOrNull()
    }
}
