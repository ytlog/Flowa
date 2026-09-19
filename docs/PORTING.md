# iOS → Android 移植对照

依据当前 Swift 源码，而不是较旧的 App Store 截图。原截图的底部搜索入口和当前源码不同；本版本遵循当前源码的顶部叠页入口。

| iOS 源码 | Android 位置 | 对应行为 |
| --- | --- | --- |
| QuickCaptureView | ui/FlowaApp.kt | 打开即写、自动键盘、草稿、清除/恢复、工具栏、长按专注 |
| AppTheme / AppTitleWordmark | ui/Components.kt / FlowaApp.kt | 语义颜色、原始字标裁切蒙版、17sp 正文、48dp 操作目标 |
| NoteLibraryView | ui/LibraryScreen.kt | 检索、筛选、多选、侧滑、日历、统计 |
| TagPickerView | ui/LibraryScreen.kt | 标签创建、混合选择、批量增删、重命名合并、AI 推荐 |
| WorkflowManager | domain/Repository.kt / Models.kt | 五类步骤、执行前校验、顺序处理、失败保留输入 |
| WorkflowConfigView | ui/WorkflowScreen.kt | 工作流、图标、步骤编辑、拖动排序、设备发现 |
| NoteStore | data/LocalRepository.kt | 私有数据持久化、原子写入、导入去重 |
| ICloudNotesStorage | domain/MarkdownCodec.kt | Markdown 元数据及日期文件名兼容；无 iCloud 私有容器访问 |
| AppConfigurationTransfer | data/JsonCodec.kt / LocalRepository.kt | schemaVersion 1 与原 JSON key 兼容 |
| AIService | data/HttpAiGateway.kt | 自定义服务、模型列表、SSE 改写 |
| AgentEngine / AgentProvider | data/HttpAiGateway.kt | Responses SSE、工具循环、重复调用保护、取消 |
| AgentChatView / SessionStore | ui/ChatScreen.kt / AppViewModel.kt | 消息、停止、历史、工具展开、自动跟随 |
| AgentMemoryStore / SkillStore | data / SettingsScreen.kt | 记忆开关、查看编辑、技能启停与编辑 |
| OffloadPermissionManager | data/DevicePermissions.kt | 排队、拒绝、一次允许、会话允许、取消清理 |
| NativeOffloads | data/DeviceToolEngine.kt | Android 日历、剪贴板、本机提醒事项 |
| BonjourDiscovery | data/AndroidDeviceGateway.kt | `_vibetaking._tcp.` NSD 发现及主机端口绑定 |
| DemoMode | data/DemoData.kt / LocalRepository.kt | 与真实数据隔离的演示快照 |

## 明确的平台差异

1. **iCloud**：Android 无法访问原 App 的 Apple 私有 iCloud 容器。本版提供本机持久化、兼容 Markdown/ZIP 迁移；没有实现自动跨端云同步。
2. **Liquid Glass / SF Symbols / 字体**：原字标和 App 图标复用。Compose 使用圆角半透明表面、阴影和弹簧动画重现工具栏；系统图标采用 AndroidX 轮廓图标。不是 Apple 的真实 Liquid Glass 折射材质，正文使用 Android 系统字体。
3. **系统键盘与系统弹窗**：使用用户的 Android 输入法、权限窗口、文件选择器和分享面板；不伪造 iOS 键盘。导航跟随 Android 返回机制，Compose 补充页面滑动转场。
4. **日历与提醒事项**：日历通过 Android Calendar Provider；提醒事项是本 App 的本机列表与 Android 通知，不能读写 Apple Reminders。Android 的省电策略可能推迟通知，未申请精确闹钟权限；当前通知闹钟在设备重启后不自动重新注册，需要重新编辑提醒。
5. **文件存储**：运行时用原子 JSON 快照保证状态一致；Markdown 是交换格式，不直接由外部文件管理器编辑应用私有目录。
6. **图标选择范围**：提供常用语义图标，并映射原配置中常见 SF Symbol 名；不是 SF Symbols 全量库。
7. **AI Markdown**：标题、列表、引用、代码块与文字选择有基础排版，复杂表格、嵌套富文本和数学公式未完整覆盖。

## 验收原则

不能将“可编译”“模拟器测试通过”等同于“和 iOS 原封不动一致”。真实 AI 服务、跨设备发送、系统日历、提醒通知、屏幕阅读器和不同厂商输入法需要相应环境验收；验证范围以 VALIDATION.md 为准。
