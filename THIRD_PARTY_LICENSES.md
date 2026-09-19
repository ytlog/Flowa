# 第三方许可证

## OpenMinis（GPL-3.0）

vibetaking 的 Agent 能力（多轮工具调用循环、Skills 系统、持久记忆、设备集成）移植自开源项目
[OpenMinis](https://github.com/OpenMinis/OpenMinis)（GNU General Public License v3.0）。

因引入 GPL-3.0 代码，vibetaking 作为组合作品整体按 **GPL-3.0** 授权发布（见根目录 [LICENSE](LICENSE)）。
本仓库即为该组合作品的完整对应源码。

### 源自 / 派生自 OpenMinis 的文件

以下文件复制或改编自 OpenMinis 仓库（`src/ios/`），文件头保留了来源标注；
为脱离其 iSH Linux 沙箱运行，移植时删除了沙箱相关调用并做了适配性修改：

| 本仓库文件 | OpenMinis 来源 |
| --- | --- |
| `apps/ios/vibetaking/Agent/AgentProvider.swift` | `Providers/AgentProvider.swift` |
| `apps/ios/vibetaking/Agent/OpenAIResponsesAgentProvider.swift` | `Providers/OpenAI/OpenAIAgentProvider.swift`、`Providers/OpenAI/OpenAIProvider.swift`（裁剪：仅保留 Responses API + SSE 流式 + 工具格式转换） |
| `apps/ios/vibetaking/Agent/ToolLoopDetector.swift` | `Agent/ToolLoopDetector.swift` |
| `apps/ios/vibetaking/Agent/ToolPreflight.swift` | `Agent/Chat/AIChatViewModel+ToolPreflight.swift` |
| `apps/ios/vibetaking/Agent/AgentEngine.swift` | 摘取自 `Agent/Chat/AIChatViewModel.swift` 的 `runAgentLoop()` 及 `AIChatViewModel+ConcurrentTools.swift`（工具并发执行、tool_use/tool_result 顺序缝合、orphan 修复） |
| `apps/ios/vibetaking/Agent/AgentMemoryStore.swift` | `Agent/Chat/AIChatViewModel+MemoryTools.swift` |
| `apps/ios/vibetaking/Agent/SkillStore.swift` | `Agent/Session/SkillStore.swift`（裁剪：去除 GitHub 导入、ZIP 编解码、iCloud 同步） |
| `apps/ios/vibetaking/Agent/OffloadPermissionManager.swift` | `Agent/Offload/OffloadPermissionManager.swift` |
| `apps/ios/vibetaking/NativeOffloads/NativeOffloadUtils.h/.m` | `NativeOffloads/NativeOffloadUtils.h/.m`（改写：去除 iSH guest 文件系统 stub 与 rootfs 路径映射） |
| `apps/ios/vibetaking/NativeOffloads/CalendarOffload.h/.m` | `NativeOffloads/CalendarOffload.h/.m` |
| `apps/ios/vibetaking/NativeOffloads/RemindersOffload.h/.m` | `NativeOffloads/RemindersOffload.h/.m` |
| `apps/ios/vibetaking/NativeOffloads/ClipboardOffload.h/.m` | `NativeOffloads/ClipboardOffload.h/.m` |

OpenMinis 版权归其作者与贡献者所有。完整许可证文本见根目录 [LICENSE](LICENSE)。
