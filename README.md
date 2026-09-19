# 随心记 · Flowa Android

参考 `vibetaking/apps/ios` 源码实现的 Android 客户端。界面使用 Jetpack Compose，保留“打开即写”、原宋体字标、黑白控件、靛蓝标签、工作流胶囊和专注模式。

## 运行

使用 Android Studio 打开本目录，选择 `app`，运行到 Android 8.0（API 26）及以上设备。需要 JDK 17、Android SDK 35。`local.properties` 按本机 SDK 路径配置。

```sh
./gradlew :app:assembleDebug
./gradlew :core:domain:test
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。不配置 AI 也可以记录、搜索、导入导出以及使用保存、复制工作流。首次启动保留原版空工作流：点“添加保存步骤”→默认工作流→添加步骤→保存记录→保存步骤。

## 分层

- `core/domain`：纯 Kotlin 模型、仓库/服务接口、搜索规则、Markdown 编解码、工作流执行用例。没有 Android 和 Compose 依赖。
- `core/data`：原子文件存储、Android Keystore、iOS 配置兼容、ZIP 导入导出、HTTP/SSE、Agent 工具循环、局域网发现和设备授权。
- `app/presentation`：ViewModel、单向状态流、顺序写入队列、异步任务和错误反馈。
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

记录、草稿、工作流、对话在应用私有目录的原子快照中持久化；导出时生成与 iOS 兼容的 Markdown。正常数据与演示数据分别存储。API 密钥通过 Android Keystore AES-GCM 加密，应用不启用 Android 自动备份。导出配置按原格式包含明文密钥，导出前会明确提示。

AI 使用用户配置的地址：改写调用 `/chat/completions`，助手调用 `/responses`，模型列表调用 `/models`。默认地址和模型沿用参考项目，不代表开发者提供免费 AI 服务。HTTP 步骤发送 `text/plain`，空草稿向 `/send` 发送回车请求。为了兼容原 Mac 接收端和用户自定义本地服务器，应用允许 HTTP。

## 平台适配和验证

实际截图：[首页](docs/screenshots/home.png)、[专注模式](docs/screenshots/focus.png)、[深色](docs/screenshots/home-dark.png)。

见 [移植对照](docs/PORTING.md) 与 [验证记录](docs/VALIDATION.md)。本工程不宣称 Apple 系统控件和 Android 达到像素级、系统行为级完全一致。

## 授权

参考项目采用 GPL-3.0，本移植同样采用根目录 `LICENSE`。原项目的字标、图标和部分文案保留来源，参见 `THIRD_PARTY_LICENSES.md` 与 `NOTICE.md`。
