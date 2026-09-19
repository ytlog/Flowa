@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.flowa.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Indigo = Color(0xFF5856D6)
val Red = Color(0xFFFF3B30)
val Grouped: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF000000) else Color(0xFFF2F2F7)
val Secondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun FlowaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = Color.Black, surface = Color(0xFF1C1C1E), onSurface = Color.White, onSurfaceVariant = Color(0xFF98989F), secondary = Indigo, outlineVariant = Color(0xFF38383A))
    else lightColorScheme(primary = Color(0xFF111111), onPrimary = Color.White, background = Color.White, surface = Color.White, onSurface = Color(0xFF111111), onSurfaceVariant = Color(0xFF8E8E93), secondary = Indigo, outlineVariant = Color(0xFFE5E5EA))
    MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp), bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp), bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)), content = content)
}
fun symbol(name: String): ImageVector = when (name) {
    "save", "square.and.arrow.down" -> Icons.Outlined.SaveAlt
    "copy", "doc.on.doc" -> Icons.Outlined.ContentCopy
    "send", "paperplane", "paperplane.circle" -> Icons.Outlined.Send
    "sparkles", "ai" -> Icons.Outlined.AutoAwesome
    "chat", "bubble.left", "bubble.left.and.bubble.right", "brain.head.profile" -> Icons.Outlined.ChatBubbleOutline
    "star" -> Icons.Outlined.StarOutline
    "bolt" -> Icons.Outlined.Bolt
    "heart" -> Icons.Outlined.FavoriteBorder
    "book" -> Icons.Outlined.MenuBook
    "pencil" -> Icons.Outlined.Edit
    "checkmark" -> Icons.Outlined.Check
    "tag" -> Icons.Outlined.LocalOffer
    else -> Icons.Outlined.AccountTree
}
@Composable fun GlyphButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(icon, label, Modifier.size(22.dp)) }
}
@Composable fun Glass(modifier: Modifier = Modifier, emphasized: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = if (emphasized) Color.Black else MaterialTheme.colorScheme.surface.copy(alpha = .96f), shadowElevation = 12.dp, border = BorderStroke(.7.dp, if (isSystemInDarkTheme()) Color.White.copy(.12f) else Color.White), contentColor = if (emphasized) Color.White else MaterialTheme.colorScheme.onSurface) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
@Composable fun PageHeader(title: String, back: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(Icons.Outlined.ChevronLeft, "返回", onClick = back)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        trailing()
    }
}
@Composable fun SheetPage(title: String, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Grouped, dragHandle = { Box(Modifier.padding(top = 8.dp).size(36.dp, 5.dp).clip(RoundedCornerShape(4.dp)).background(Secondary.copy(.35f))) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                actions()
                TextButton(onClick = onClose) { Text("完成") }
            }
            content()
        }
    }
}
@Composable fun FormSection(title: String = "", footer: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (title.isNotEmpty()) Text(title, Modifier.padding(start = 16.dp, bottom = 7.dp), color = Secondary, style = MaterialTheme.typography.bodySmall)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface), content = content)
        if (footer.isNotEmpty()) Text(footer, Modifier.padding(start = 16.dp, top = 7.dp, end = 8.dp), color = Secondary, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable fun FormRow(title: String, subtitle: String? = null, icon: ImageVector? = null, destructive: Boolean = false, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp), tint = if (destructive) Red else MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (destructive) Red else MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, color = Secondary, style = MaterialTheme.typography.bodySmall)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = Secondary, modifier = Modifier.size(20.dp))
    }
}
@Composable fun Separator() { HorizontalDivider(Modifier.padding(start = 16.dp), thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant) }
@Composable fun EmptyState(icon: ImageVector, title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(46.dp), tint = Secondary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = Secondary, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}
@Composable fun ConfirmDelete(title: String, detail: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(detail) }, confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("删除", color = Red) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("保留") } })
}
@Composable fun SearchField(value: String, onChange: (String) -> Unit, hint: String = "搜索") {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), placeholder = { Text(hint, color = Secondary) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (value.isNotEmpty()) GlyphButton(Icons.Outlined.Close, "清除搜索") { onChange("") } }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent, unfocusedContainerColor = Secondary.copy(.10f), focusedContainerColor = Secondary.copy(.10f)))
}
