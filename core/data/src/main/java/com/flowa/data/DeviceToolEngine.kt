package com.flowa.data

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import com.flowa.domain.CommandLine
import com.flowa.domain.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

class DeviceToolEngine(private val context: Context, val permissions: DevicePermissions) {
    private val reminders = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    suspend fun execute(name: String, raw: String, session: String): String {
        val words = CommandLine.tokenize(raw)
        val command = words.firstOrNull() ?: "--help"
        val options = CommandLine.options(words)
        if ("--help" in words || command == "-h") return when(name) {
            "calendar_manage" -> "calendars; list/freebusy --start ISO或-7d --end ISO或7d; create --title 标题 --start ISO --end ISO [--calendar ID] [--notes 文本] [--location 地点] [--alarm 分钟]; update --id ID [--title 标题] [--start ISO] [--end ISO]; delete --id ID"
            "reminders_manage" -> "list [--completed|--incomplete]; create --title 标题 [--due ISO或1d] [--notes 文本]; update --id ID [--title 标题] [--due ISO]; complete --id ID; delete --id ID。提醒事项保存在随心记本机，不连接 Apple 提醒事项。"
            else -> "get; set --text 文本; clear; status。访问设备剪贴板前需要用户授权。"
        }
        val capability = when(name) { "calendar_manage" -> DeviceCapability.CALENDAR; "reminders_manage" -> DeviceCapability.REMINDERS; else -> DeviceCapability.CLIPBOARD }
        val required = when(capability) {
            DeviceCapability.CALENDAR -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            DeviceCapability.REMINDERS -> if (Build.VERSION.SDK_INT >= 33 && command in listOf("create", "update") && options.containsKey("due")) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
            else -> emptyList()
        }.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        permissions.authorize(capability, raw, session, required)
        return when(capability) {
            DeviceCapability.CLIPBOARD -> withContext(Dispatchers.Main) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                when(command) {
                    "get" -> clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    "set" -> { clipboard.setPrimaryClip(ClipData.newPlainText("随心记", options["text"] ?: words.drop(1).joinToString(" "))); "已复制文本" }
                    "clear" -> { if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", "")); "已清除剪贴板" }
                    "status" -> "包含文本：${clipboard.hasPrimaryClip()}"
                    else -> error("未知命令，请先使用 --help。")
                }
            }
            DeviceCapability.CALENDAR -> withContext(Dispatchers.IO) { calendar(command, options) }
            DeviceCapability.REMINDERS -> withContext(Dispatchers.IO) { reminder(command, options) }
        }
    }
    private fun time(raw: String): Long {
        val relative = Regex("([+-]?\\d+)([dhm])").matchEntire(raw)
        if (relative != null) return System.currentTimeMillis() + relative.groupValues[1].toLong() * when(relative.groupValues[2]) { "d" -> 86400000L; "h" -> 3600000L; else -> 60000L }
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrElse { runCatching { LocalDateTime.parse(raw.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrElse { LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } }
    }
    private fun calendar(command: String, o: Map<String, String>): String {
        val resolver = context.contentResolver
        if (command == "calendars") {
            val result = JSONArray()
            resolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf("_id", "calendar_displayName", "calendar_access_level"), null, null, null)?.use { cursor -> while(cursor.moveToNext()) result.put(JSONObject().put("id", cursor.getLong(0)).put("name", cursor.getString(1)).put("writable", cursor.getInt(2) >= 500)) }
            return result.toString()
        }
        if (command in listOf("list", "freebusy")) {
            val start = o["start"]?.let(::time) ?: System.currentTimeMillis() - (o["days"]?.toLongOrNull() ?: 7L) * 86400000
            val end = o["end"]?.let(::time) ?: System.currentTimeMillis() + 7L * 86400000
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
            val result = JSONArray()
            resolver.query(uri, arrayOf("event_id", "title", "begin", "end", "eventLocation", "calendar_id"), null, null, "begin ASC")?.use { cursor -> while(cursor.moveToNext() && result.length() < (o["limit"]?.toIntOrNull() ?: 100).coerceIn(1,500)) {
                if (o["calendar"] == null || o["calendar"] == cursor.getLong(5).toString()) result.put(JSONObject().put("id", cursor.getLong(0)).put("title", cursor.getString(1)).put("start", Instant.ofEpochMilli(cursor.getLong(2))).put("end", Instant.ofEpochMilli(cursor.getLong(3))).put("location", cursor.getString(4)))
            } }
            return result.toString()
        }
        val id = o["id"]?.toLongOrNull()
        if (command == "delete") { require(id != null) { "请提供 --id" }; val count = resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null); return "已删除 $count 个事件" }
        require(command in listOf("create", "update")) { "未知命令，请先使用 --help。" }
        val values = ContentValues().apply {
            o["title"]?.let { put("title", it) }; o["notes"]?.let { put("description", it) }; o["location"]?.let { put("eventLocation", it) }
            o["start"]?.let { put("dtstart", time(it)) }; o["end"]?.let { put("dtend", time(it)) }
        }
        if (command == "update") { require(id != null) { "请提供 --id" }; return "已更新 ${resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), values, null, null)} 个事件" }
        require(o["title"].isNullOrBlank().not() && o.containsKey("start") && o.containsKey("end")) { "创建事件需要标题、开始和结束时间。" }
        require(time(o.getValue("end")) > time(o.getValue("start"))) { "结束时间必须晚于开始时间。" }
        var calendarId = o["calendar"]?.toLongOrNull()
        if (calendarId == null) resolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf("_id"), "calendar_access_level >= ? AND visible = ?", arrayOf("500", "1"), null)?.use { if (it.moveToFirst()) calendarId = it.getLong(0) }
        require(calendarId != null) { "没有可写入的系统日历，请先在日历 App 添加账户。" }
        values.put("calendar_id", calendarId); values.put("eventTimezone", ZoneId.systemDefault().id)
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: error("日历保存失败。")
        o["alarm"]?.toIntOrNull()?.let { minutes -> resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply { put("event_id", ContentUris.parseId(uri)); put("minutes", minutes); put("method", CalendarContract.Reminders.METHOD_ALERT) }) }
        return "已创建事件 id=${ContentUris.parseId(uri)}"
    }
    private fun reminder(command: String, o: Map<String, String>): String = synchronized(reminders) {
        val all = JSONArray(reminders.getString("items", "[]")).objects().toMutableList()
        if (command == "list") return@synchronized JSONArray(all.filter { if (o.containsKey("completed")) it.optBoolean("completed") else if (o.containsKey("incomplete")) !it.optBoolean("completed") else true }).toString()
        val item = if (command == "create") JSONObject().put("id", newId()).put("completed", false) else all.firstOrNull { it.getString("id") == o["id"] } ?: error("提醒事项不存在。")
        when(command) {
            "create", "update" -> { if (command == "create") require(!o["title"].isNullOrBlank()) { "请提供标题。" }; o["title"]?.let { item.put("title", it) }; o["notes"]?.let { item.put("notes", it) }; o["due"]?.let { item.put("due", time(it)) }; if (command == "create") all.add(item) }
            "complete" -> item.put("completed", true)
            "delete" -> all.remove(item)
            else -> error("未知命令，请使用 --help。")
        }
        check(reminders.edit().putString("items", JSONArray(all).toString()).commit()) { "提醒事项保存失败" }
        val intent = Intent(context, ReminderReceiver::class.java).setAction(item.getString("id")).putExtra("title", item.optString("title")).putExtra("notes", item.optString("notes"))
        val pending = PendingIntent.getBroadcast(context, item.getString("id").hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
        if (command != "delete" && !item.optBoolean("completed") && item.optLong("due") > System.currentTimeMillis()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.getLong("due"), pending)
        "已保存提醒事项 id=${item.getString("id")}"
    }
}
