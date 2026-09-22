# 随心记 · Flowa Android

打开即写的纯文本速记工具，参考 [vibetaking](https://github.com/ddhjy/vibetaking) 的 `apps/ios` 源码实现的 Android 客户端。界面使用 Jetpack Compose，保留原宋体字标、黑白控件、靛蓝标签、工作流胶囊和专注模式。

启动直达空白编辑页：自动聚焦、键盘就绪、草稿实时落盘，记录以 Markdown 导入导出。Android 无法访问原应用的 iCloud 私有容器，本版使用本机持久化。

## 设计理念

多数笔记产品打开先看列表，以回顾为中心。随心记把入口放回记录本身：一张空白页，一个就绪的光标。空白减少选择和干扰，也自然产生把它填上的动机。

这种快来自几处取舍：

- **入口快**：主页即编辑页，无需新建、选分类或找笔记。
- **启动快**：原生组件构建，优先保证启动和交互响应。
- **输入快**：纯文本，不区分标题正文，不提供复杂排版。
- **整理快**：只有记录和标签两层；标签积累后，零散文本可分类、可检索、可回顾。

需要更快输入时，配合系统听写或语音转文字工具即可。

参考：[vibetaking](https://github.com/ddhjy/vibetaking) · [App Store](https://apps.apple.com/cn/app/id6778762693)

## 运行

使用 Android Studio 打开本目录，选择 `app`，运行到 Android 8.0（API 26）及以上设备。需要 JDK 17、Android SDK 35。`local.properties` 按本机 SDK 路径配置。

```sh
./gradlew :app:assembleDebug
./gradlew :core:domain:test :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -PisolatedUiTest=true
./gradlew :app:lintDebug
```

UI 自动化使用 `-PisolatedUiTest=true` 安装独立包 `com.flowa.app.uitest`，避免测试操作影响日常笔记。测试完成后，不带此参数重新构建正式调试 APK。

APK：`app/build/outputs/apk/debug/app-debug.apk`。不配置 AI 也可以记录、搜索、导入导出以及使用保存、复制工作流。默认提供三个工作流：保存记录（直接保存）、整理口述（AI 整理后保存）、润色文案（AI 润色后复制）。后两个需要先在设置中配置 AI。升级旧版本时替换原始空工作流并补齐预设，保留自定义工作流、记录和草稿；之后删除的预设不会重新出现。

## 分层

- `core/domain`：纯 Kotlin 模型、仓库/服务接口、搜索规则、Markdown 编解码、工作流执行用例。没有 Android 和 Compose 依赖。
- `core/data`：仓库协调状态；`SnapshotStore` 原子存储、`SnapshotMigrations` 升级旧快照、`NoteArchive` 导入导出笔记、`ConfigurationCodec` 兼容 iOS 配置。平台接口实现网络、Keystore、局域网发现与设备授权。
- `app/presentation`：ViewModel 统一持有编辑器状态；`StateWriter` 顺序保存并传递失败，工作流按草稿版本提交，异步任务随数据环境切换而取消。
- `app/ui`：Compose 页面、表单、底部弹层、动画和无障碍语义。UI 不直接读写文件或调用 HTTP。
- `FlowaApplication`：应用级依赖组装，无全局静态页面状态。

## 功能

- 草稿自动保存、清除/恢复、标签保留，打开自动弹出键盘。
- 多工作流入口、长按专注、胶囊宽度弹簧动画、固定位置的标签/退出与清除控件。
- 工作流新建、复制、隐藏、删除、步骤排序和启停；AI 改写、AI 助手、复制、保存、HTTP 发送五种步骤。
- 全文 AND 搜索、包含/排除标签、无标签、随机回顾、记录展开与复制、侧滑标签/删除、批量选择与标签、统计日历。
- Markdown/ZIP 导入、去重、ZIP 导出；iOS schemaVersion 1 配置导入导出。
- AI 模型列表/手动模型、流式回复、停止生成、历史对话、工具活动展开、笔记工具、记忆与技能。
- 日历/剪贴板设备授权；本机提醒事项和通知；“本次会话允许”按实际对话隔离。
- 独立演示数据、浅深色、系统字体缩放、系统动画缩放。

## 数据与网络

记录、草稿、工作流、对话在应用私有目录的原子快照中持久化；导出时生成与 iOS 兼容的 Markdown。正常数据与演示数据分别存储。API 密钥通过 Android Keystore AES-GCM 加密，密文与状态在同一个快照内原子写入；旧版独立凭据会自动迁移。应用不启用 Android 自动备份。导出配置按原格式包含明文密钥，导出前会明确提示。

AI 使用用户配置的地址：改写调用 `/chat/completions`，助手调用 `/responses`，模型列表调用 `/models`。默认地址和模型沿用参考项目，不代表开发者提供免费 AI 服务。HTTP 步骤发送 `text/plain`，空草稿向 `/send` 发送回车请求。为了兼容原 Mac 接收端和用户自定义本地服务器，应用允许 HTTP。

## 平台适配和验证

实际截图：[首页](docs/screenshots/home.png)、[专注模式](docs/screenshots/focus.png)、[深色](docs/screenshots/home-dark.png)。

见 [移植对照](docs/PORTING.md) 与 [验证记录](docs/VALIDATION.md)。本工程不宣称 Apple 系统控件和 Android 达到像素级、系统行为级完全一致。

## 授权

参考项目 [vibetaking](https://github.com/ddhjy/vibetaking) 采用 GPL-3.0，本移植同样采用根目录 `LICENSE`。原项目的字标、图标和部分文案保留来源，参见 `THIRD_PARTY_LICENSES.md` 与 `NOTICE.md`。

## 开发规范

见 [AGENTS.md](AGENTS.md)。核心要求是先修正错误设计和实现，再继续功能迭代；不依赖重复状态、补偿逻辑或弱化测试掩盖问题。此次重构的边界与验证见 [重构说明](docs/REFACTORING.md)。
