package com.flowa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowa.app.presentation.AppViewModel

@Composable fun DevicePermissionDialog(vm: AppViewModel) {
    val request by vm.permissions.pending.collectAsStateWithLifecycle()
    var pendingId by remember { mutableStateOf<String?>(null) }
    var sessionAllowed by remember { mutableStateOf(false) }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> pendingId?.let { vm.respondPermission(it, result.values.all { granted -> granted }, sessionAllowed) }; pendingId = null }
    request?.let { item ->
        fun allow(session: Boolean) {
            if (item.androidPermissions.isEmpty()) vm.respondPermission(item.id, true, session)
            else { pendingId = item.id; sessionAllowed = session; runtime.launch(item.androidPermissions.toTypedArray()) }
        }
        AlertDialog(onDismissRequest = { vm.respondPermission(item.id, false, false) }, title = { Text("允许助手访问${item.capability.label}？") }, text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            Text("助手将执行以下操作。相关结果可能发送到你配置的 AI 服务。")
            Spacer(Modifier.height(12.dp)); Text(item.command, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp)); Text("“本次会话允许”只适用于当前对话；工作流每次运行单独授权。", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { Column { TextButton(onClick = { allow(false) }) { Text("允许这一次") }; TextButton(onClick = { allow(true) }) { Text("本次会话允许") } } }, dismissButton = { TextButton(onClick = { vm.respondPermission(item.id, false, false) }) { Text("不允许") } })
    }
}
