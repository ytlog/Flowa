# 开发指南

## 环境与运行

使用 Android Studio 打开仓库，选择 `app` 运行。需要 JDK 17、Android SDK 35 和 Build Tools 35.0.0，最低运行版本为 Android 8.0（API 26）。`local.properties` 按本机 SDK 路径配置，不提交到 Git。

```sh
./gradlew :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。macOS 注册了 JDK 17 后，可显式选择该版本运行：

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 17)" ./gradlew :app:assembleDebug --offline --max-workers=2
```

版本统一配置在根目录 `gradle.properties`：`flowa.versionName` 为用户可见版本，`flowa.versionCode` 为递增的 Android 版本代码。应用内版本和配置导出中的版本信息均来自此处。

## 分层与状态所有权

- `core/domain`：纯 Kotlin 模型、仓库/服务接口、搜索规则、Markdown 编解码、工作流执行用例；不依赖 Android、Compose、JSON 或网络实现。
- `core/data`：仓库协调状态；`SnapshotStore` 原子存储、`SnapshotMigrations` 升级旧快照、`NoteArchive` 导入导出笔记、`ConfigurationCodec` 兼容 iOS 配置；平台实现负责网络、Keystore、局域网发现与设备授权。
- `app/presentation`：ViewModel 统一持有编辑器状态；`StateWriter` 顺序保存并传递失败，工作流按草稿版本提交，异步任务随数据环境切换而取消。
- `app/ui`：Compose 页面、表单、底部弹层、动画和无障碍语义；UI 不直接读写文件或调用 HTTP。
- `FlowaApplication`：应用级依赖组装，无全局静态页面状态。

先确定期望行为、状态流转和失败处理，再实现。不要增加与 ViewModel 竞争的业务状态；写入失败保留草稿，旧任务不得覆盖新输入。完整协作规范见 [AGENTS.md](../AGENTS.md)，重构边界见 [REFACTORING.md](REFACTORING.md)。

## 测试与设备回归

```sh
./gradlew :core:domain:test :app:testDebugUnitTest --offline --max-workers=2
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PisolatedUiTest=true --offline --max-workers=2
ANDROID_SERIAL=<设备序列号> ./gradlew :app:connectedDebugAndroidTest -PisolatedUiTest=true --offline --max-workers=2
./gradlew :app:assembleDebug :app:lintDebug --offline --max-workers=2
```

真实设备 UI 自动化必须使用 `-PisolatedUiTest=true`，目标包为 `com.flowa.app.uitest`，测试包为 `com.flowa.app.uitest.test`。禁止清空日常应用数据或向用户笔记写测试内容。可使用 `ANDROID_SERIAL` 选择设备。

独立测试和日常构建共享输出目录。测试完成后，不带 `isolatedUiTest` 参数重新构建日常 APK，并核对包名后再安装。该参数也可用于构建独立的 Release 冒烟测试包，发布流水线会拒绝上传测试包。

修改业务逻辑运行对应单元测试；涉及界面交互、输入法或平台接口运行设备回归。构建成功不能代替功能验证。历史范围和测试服务与真实服务的区别见 [VALIDATION.md](VALIDATION.md)，平台适配见 [PORTING.md](PORTING.md)。

## 存储与兼容性

记录、草稿、工作流、对话保存在应用私有目录的原子快照中；正常数据与演示数据分别存储。API 密钥通过 Android Keystore AES-GCM 加密，密文与状态在同一快照内原子写入，旧版独立凭据会迁移。应用不启用 Android 自动备份。

Markdown/ZIP 导入导出兼容 iOS；配置交换保持 `schemaVersion: 1`，导出包含明文 AI 密钥，界面会在导出前提醒。改变存储格式时提供迁移与重载测试，不覆盖用户自定义内容。

默认工作流包括保存记录、整理口述（AI → 保存）、润色文案（AI → 复制）。升级旧版本时替换原始空工作流并补齐预设，保留自定义工作流、记录和草稿；用户之后删除的预设不再自动补回。

## 网络与权限

AI 改写调用 `/chat/completions`，助手调用 `/responses`，模型列表调用 `/models`。默认地址和模型不代表开发者提供免费 AI 服务，测试优先使用本地固定响应服务器。

HTTP 步骤发送 `text/plain`，空草稿向 `/send` 发送回车请求。为兼容 Mac 接收端和用户自定义的局域网服务器，应用允许 HTTP。设备操作需要 Android 系统权限；应用内的“本次会话允许”按实际对话隔离。

## 发布

正式版使用独立于 Debug 的发布签名，复用 Mobby 的现有密钥。签名配置、GitHub Actions 和版本发布步骤见 [RELEASING.md](RELEASING.md)。只有用户要求提交、推送或发布时才执行对应操作；提交使用仓库配置的 `YTLog` 身份。
