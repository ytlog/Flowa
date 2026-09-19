package com.flowa.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.flowa.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NearbyDevice(val name: String, val host: String, val port: Int)
class AndroidDeviceGateway(private val context: Context) : DeviceGateway {
    private val client = OkHttpClient()
    override suspend fun copy(text: String) = withContext(Dispatchers.Main) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("随心记", text)) }
    override suspend fun post(step: Step, text: String, returnKey: Boolean) {
        val resolved = if (step.serviceName.isNotBlank()) withContext(Dispatchers.Main) {
            val discovery = DeviceDiscovery(context)
            try { discovery.start(); withTimeoutOrNull(3000) { discovery.devices.first { list -> list.any { it.name == step.serviceName } }.first { it.name == step.serviceName } } }
            finally { discovery.stop() }
        } else null
        val host = resolved?.host ?: step.host
        val port = resolved?.port ?: step.port
        require(validHost(host, port)) { "发送地址无法使用，请检查主机和端口。" }
        val request = Request.Builder().url("http://${host}:${port}${if (returnKey) "/send" else "/"}").post(text.toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
        suspendCancellableCoroutine<Unit> { continuation ->
            val call = client.newCall(request); continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("发送失败，请确认接收端已开启且位于同一网络。", e)) }
                override fun onResponse(call: Call, response: Response) { response.use { if (continuation.isActive) { if (it.isSuccessful) continuation.resume(Unit) else continuation.resumeWithException(IOException("接收端未接受请求（${it.code}）。")) } } }
            })
        }
    }
}
class DeviceDiscovery(context: Context) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mutable = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val devices = mutable.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null
    fun start() {
        if (listener != null) return
        val next = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { stop() }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) { mutable.value = mutable.value.filterNot { it.name == service.serviceName } }
            @Suppress("DEPRECATION") override fun onServiceFound(service: NsdServiceInfo) {
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(service: NsdServiceInfo) {
                        val host = service.host?.hostAddress ?: return
                        mutable.value = (mutable.value.filterNot { it.name == service.serviceName } + NearbyDevice(service.serviceName, if (host.contains(':')) "[$host]" else host, service.port)).sortedBy { it.name }
                    }
                })
            }
        }
        listener = next
        runCatching { manager.discoverServices("_vibetaking._tcp.", NsdManager.PROTOCOL_DNS_SD, next) }.onFailure { listener = null }
    }
    fun stop() { listener?.let { runCatching { manager.stopServiceDiscovery(it) } }; listener = null }
}
