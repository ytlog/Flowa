@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
package com.flowa.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowa.app.R
import com.flowa.app.presentation.AppViewModel
import com.flowa.domain.*
import kotlinx.coroutines.delay

@Composable fun FlowaApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("home") }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var initialSearch by rememberSaveable { mutableStateOf("") }
    var taggedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importNotes) }
    val openLibrary: (String) -> Unit = { initialSearch = it; page = "library" }
    BackHandler(page != "home" && sheet == null) { page = "home" }
    fun share(config: Boolean) { vm.export(config) { file ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (config) "application/json" else "application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "导出随心记"))
    } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else AnimatedContent(page, transitionSpec = {
                if (targetState == "home") (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn()) togetherWith slideOutHorizontally(tween(300)) { it }
                else slideInHorizontally(tween(300)) { it } togetherWith (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut())
            }, label = "页面导航") { destination ->
                when (destination) {
                    "home" -> {
                        val configuration = LocalViewConfiguration.current
                        CompositionLocalProvider(LocalViewConfiguration provides remember(configuration) { object : ViewConfiguration by configuration { override val longPressTimeoutMillis = 350L } }) {
                            HomeScreen(state, vm, sheet == null, openLibrary, { sheet = it }, { page = "chat" })
                        }
                    }
                    "library" -> LibraryScreen(state, initialSearch, vm, { page = "home" }, { taggedIds = it; sheet = "noteTags" }, { sheet = "stats" }, { importer.launch(arrayOf("text/*", "application/zip", "application/octet-stream")) }, { share(false) })
                    "chat" -> ChatScreen(state, vm, { page = "home" }, { sheet = "history" })
                }
            }
        }
        if (status != null && page != "home") Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) { Snackbar { Text(status!!) } }
    }
    LaunchedEffect(status) { if (status != null) { delay(4000); vm.status.value = null } }
    DevicePermissionDialog(vm)
    when (sheet) {
        "tags" -> TagSheet(state, vm, emptySet()) { sheet = null }
        "noteTags" -> TagSheet(state, vm, taggedIds) { sheet = null }
        "workflows" -> WorkflowSheet(state, vm) { sheet = null }
        "settings" -> SettingsSheet(state, vm, { share(true) }) { sheet = null }
        "stats" -> StatisticsSheet(state.notes, { initialSearch = ""; sheet = null }) { sheet = null }
        "history" -> HistorySheet(state, vm) { sheet = null }
    }
    if (error != null) AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("操作未完成") }, text = { Text(error!!) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("继续记录") } }, dismissButton = { TextButton(onClick = { vm.error.value = null; sheet = "settings" }) { Text("检查设置") } })
}

@Composable private fun Wordmark() {
    val resources = LocalContext.current.resources
    val bitmap = remember {
        val source = BitmapFactory.decodeResource(resources, R.drawable.wordmark_source, BitmapFactory.Options().apply { inScaled = false })
        // Approved iOS artwork uses this exact crop and converts white to transparent ink.
        val scale = source.width / 1774f
        val crop = Bitmap.createBitmap(source, (253 * scale).toInt(), (237 * scale).toInt(), (1297 * scale).toInt(), (387 * scale).toInt())
        val pixels = IntArray(crop.width * crop.height); crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
        pixels.indices.forEach { i -> val p = pixels[i]; val grey = ((p shr 16 and 255) + (p shr 8 and 255) + (p and 255)) / 3; pixels[i] = ((255 - grey) shl 24) }
        Bitmap.createBitmap(pixels, crop.width, crop.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap, "随心记", Modifier.size(114.dp, 34.dp).semantics { heading() }, colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
}

@Composable private fun HomeScreen(state: AppState, vm: AppViewModel, active: Boolean, openLibrary: (String) -> Unit, openSheet: (String) -> Unit, openChat: () -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.draft.text)) }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    var more by remember { mutableStateOf(false) }
    var focusPicker by remember { mutableStateOf(false) }
    val executing by vm.execution.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val draftReset by vm.draftReset.collectAsStateWithLifecycle()
    val focused = state.workflows.firstOrNull { it.id == state.focusedId && it.isOpen }
    LaunchedEffect(draftReset) { val saved = vm.state.value.draft.text; if ((vm.draftResetExpectedText == null || text.text == vm.draftResetExpectedText) && text.text != saved) text = TextFieldValue(saved, androidx.compose.ui.text.TextRange(saved.length)) }
    LaunchedEffect(active) { if (active) { delay(250); requester.requestFocus(); keyboard?.show() } else { focusManager.clearFocus(); keyboard?.hide() } }
    fun navigate(action: () -> Unit) { keyboard?.hide(); focusManager.clearFocus(); action() }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(); Spacer(Modifier.weight(1f))
            Glass { GlyphButton(Icons.Outlined.Layers, "记录", executing == null) { navigate { openLibrary(text.text.trim()) } } }
            Spacer(Modifier.width(12.dp))
            Box {
                Glass { GlyphButton(Icons.Outlined.MoreHoriz, "更多操作") { more = true } }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem({ Text("AI 助手") }, { more = false; navigate(openChat) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) })
                    DropdownMenuItem({ Text("工作流") }, { more = false; openSheet("workflows") }, leadingIcon = { Icon(Icons.Outlined.AccountTree, null) })
                    DropdownMenuItem({ Text("进入专注模式") }, { more = false; focusPicker = true }, leadingIcon = { Icon(Icons.Outlined.CenterFocusStrong, null) })
                    HorizontalDivider()
                    DropdownMenuItem({ Text("设置") }, { more = false; openSheet("settings") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) })
                }
                DropdownMenu(focusPicker, { focusPicker = false }) { state.workflows.filter { it.isOpen }.forEach { flow -> DropdownMenuItem({ Text(flow.name) }, { focusPicker = false; vm.focus(flow.id) }, leadingIcon = { Icon(symbol(flow.icon), null) }) } }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            BasicTextField(text, { text = it; vm.draftText(it.text) }, Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 20.dp).padding(top = 20.dp).focusRequester(requester), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface), keyboardOptions = KeyboardOptions(imeAction = if (focused != null) ImeAction.Send else ImeAction.Default), keyboardActions = KeyboardActions(onSend = { focused?.let { vm.run(it, text.text) } }), decorationBox = { inner ->
                Box { if (text.text.isEmpty()) Text("写下此刻的想法…", color = Secondary); inner() }
            })
        }
        if (focused == null && text.text.isEmpty() && state.notes.isEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("草稿随输入自动保存。添加“保存记录”步骤后，就能在记录列表中回顾。", style = MaterialTheme.typography.bodySmall, color = Secondary)
            TextButton(onClick = { openSheet("workflows") }, contentPadding = PaddingValues(0.dp)) { Text("添加保存步骤") }
        }
        if (focused == null && (executing != null || status != null)) Text(executing?.let { "正在运行：第 ${it.step} 步" } ?: status.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = Secondary)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp, top = 8.dp).height(48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val available = maxWidth
                val compact = minOf(available, (state.workflows.count { it.isOpen } + 1).times(52).dp + 4.dp)
                val width by androidx.compose.animation.core.animateDpAsState(if (focused != null) available else compact, spring(dampingRatio = .92f, stiffness = 320f), label = "工作流胶囊展开")
                Glass(Modifier.width(width).height(48.dp), focused != null) {
                    if (focused != null) {
                        Row(Modifier.fillMaxWidth().height(48.dp).combinedClickable(enabled = executing == null, onClick = { vm.run(focused, text.text) }, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.focus(null) }).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            if (executing != null) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp) else Icon(symbol(focused.icon), null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp)); Text(focused.name, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        }
                    } else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GlyphButton(Icons.Outlined.Tune, "工作流设置", executing == null) { openSheet("workflows") }
                        state.workflows.filter { it.isOpen }.forEach { flow ->
                            Box(Modifier.size(48.dp).combinedClickable(enabled = executing == null, onClickLabel = "运行${flow.name}", onLongClickLabel = "进入专注模式", onClick = { vm.run(flow, text.text) }, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.focus(flow.id) }), contentAlignment = Alignment.Center) {
                                if (executing?.id == flow.id) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(symbol(flow.icon), flow.name, Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            Glass { Box { GlyphButton(if (focused != null) Icons.Outlined.CenterFocusStrong else Icons.Outlined.LocalOffer, if (focused != null) "退出专注" else "草稿标签", executing == null) { if (focused != null) vm.focus(null) else openSheet("tags") }; if (focused == null && state.draft.tags.isNotEmpty()) Text(state.draft.tags.size.toString(), Modifier.align(Alignment.TopEnd).padding(end = 4.dp), style = MaterialTheme.typography.bodySmall, color = Indigo) } }
            Glass { val restore = state.draft.text.isEmpty() && state.draft.tags.isEmpty() && state.cleared != null; GlyphButton(if (restore) Icons.Outlined.Undo else Icons.Outlined.Close, if (restore) "恢复草稿" else "清除草稿", executing == null && (restore || state.draft.text.isNotEmpty() || state.draft.tags.isNotEmpty())) { vm.clearDraft() } }
        }
    }
}
