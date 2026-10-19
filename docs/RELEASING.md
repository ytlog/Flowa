# Android 发布

## 版本与产物

版本配置位于根目录 `gradle.properties`。首个版本为 `flowa.versionName=0.1.0`、`flowa.versionCode=1`，之后每次更新都必须递增 `versionCode`。标签使用 `v<versionName>`，例如 `v0.1.0`；标签与版本配置不一致时流水线会停止。

Release 启用 R8 优化、混淆和资源收缩，不可调试，正式包名为 `com.flowa.app`。发布附件包括：

- `flowa-v<版本>.apk`：可直接安装的签名 Android 应用。
- `flowa-v<版本>.aab`：签名的 Android App Bundle。
- `flowa-v<版本>-sources.tar.gz`：该标签对应的完整已跟踪源码及构建文件。
- `signing-certificate.txt`：APK 签名证书信息。
- `SHA256SUMS.txt`：上述附件的 SHA-256 校验值。

## 签名

按维护者配置复用 Mobby 的长期发布密钥，使用相同的环境变量名称，不生成新密钥。Flowa 的包名与 Mobby 独立。签名材料保存在仓库外，不提交到 Git、不作为构建附件上传。

在 Flowa 仓库的 **Settings → Secrets and variables → Actions** 配置以下 Repository secrets，值与 Mobby 当前发布配置一致：

| Secret | 内容 |
| --- | --- |
| `MOBBY_RELEASE_KEYSTORE_BASE64` | 现有 keystore 的 Base64 编码 |
| `MOBBY_RELEASE_STORE_PASSWORD` | keystore 口令 |
| `MOBBY_RELEASE_KEY_ALIAS` | 签名密钥别名 |
| `MOBBY_RELEASE_KEY_PASSWORD` | 密钥口令 |

调试版使用 Android 默认 Debug 签名。未设置签名环境变量时，Gradle 可生成未签名的 Release，供无密钥的构建 runner 验证；只设置部分变量会失败。

本地签名构建需通过可信的仓库外凭据加载工具设置 `MOBBY_RELEASE_STORE_FILE`、`MOBBY_RELEASE_STORE_PASSWORD`、`MOBBY_RELEASE_KEY_ALIAS`、`MOBBY_RELEASE_KEY_PASSWORD`，然后执行：

```sh
./gradlew :app:assembleRelease :app:bundleRelease --offline --max-workers=2
```

生成的 APK 位于 `app/build/outputs/apk/release/app-release.apk`，AAB 位于 `app/build/outputs/bundle/release/app-release.aab`。不要在终端输出口令或将签名环境写入仓库配置。长期保管并备份现有密钥；后续正式版本必须沿用相同签名才能覆盖升级。[Android 签名说明](https://developer.android.com/studio/publish/app-signing)。

## GitHub Actions

[Publish Android release](../.github/workflows/release.yml) 在推送 `v*` 标签时运行，也可从 Actions 页面手动输入已经存在的版本标签重试。

1. 构建 runner 检查版本标签和发布说明，运行领域层、Release 单元测试与 Release lint，并在 GitHub 的 Android API 35 独立模拟器上使用 `-PisolatedUiTest=true` 执行设备回归。之后恢复日常包并构建未签名 APK/AAB，打包该提交的源码。此 runner 不读取签名 secrets；测试与 lint 报告以 Actions artifact 保留 7 天。
2. 独立签名 runner 下载产物，通过 Android 官方 `apksigner` 签名 APK、JDK `jarsigner` 签名 AAB；不运行 Gradle 或项目构建脚本。
3. 核验包名、版本代码、版本名、不可调试标志、APK 对齐及 APK/AAB 签名，生成校验文件。
4. 先创建草稿 Release 并上传所有附件，再公开为最新版本。已公开版本拒绝覆盖；草稿可重试。临时 keystore 在签名步骤结束和 job 清理时删除。

工作流仅在签名与发布 job 声明 `contents: write`，通过 GitHub 自带 `GITHUB_TOKEN` 发布，无需个人访问令牌。Actions 的行为参见 [GitHub 发布命令](https://cli.github.com/manual/gh_release_create)。

## 发布步骤

1. 更新 `gradle.properties` 中的版本名与版本代码，新增 `docs/releases/v<版本>.md` 用户发布说明。
2. 按 [开发指南](DEVELOPMENT.md) 运行相关测试和独立包设备回归。真实 AI 服务与本地模拟服务的验证范围分别记录。
3. 检查源码及暂存内容，确认无密钥、缓存或产物后，以 `YTLog` 身份提交并推送默认分支。
4. 创建并推送标签：

   ```sh
   git tag -a v0.1.0 -m 'Flowa Android 0.1.0'
   git push origin v0.1.0
   ```

5. 等待 Actions 成功，在 Releases 确认附件完整，并下载 APK 核对 SHA-256 和签名。

设备回归使用 `-PisolatedUiTest=true` 独立包，禁止用发布包覆盖签名不同的日常 Debug 应用或清空用户数据。测试构建后重新生成不带此参数的日常 APK。已有 Debug 与正式 Release 签名不同，Android 会拒绝直接覆盖；用户应保管好记录与配置导出备份。
