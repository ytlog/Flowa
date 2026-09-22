# Flowa 项目协作规范

## 正确实现优先

- 先明确用户期望的行为、状态流转和失败处理，再设计实现；不要把现有代码当成天然正确的前提。
- 发现已有实现或新生成代码存在错误时，先定位根因，修正或重写有问题的实现。禁止在错误设计上持续叠加特殊判断、重复状态、临时标志或补偿代码来勉强通过当前场景。
- 重构应形成一条清晰、完整、可维护的实现路径。新路径验证通过后删除已替代的旧代码，不保留两套互相修补的逻辑。
- 优先解决状态所有权、职责边界、调用顺序和数据一致性问题；不要用增加延时、吞掉异常、清空用户数据或弱化断言掩盖产品缺陷。
- 根据用户可观察的行为验证正确性。自动化遇到系统动画、键盘或设备限制时，应修正测试同步方式，区分测试问题与产品问题，不为通过测试改变正确的产品行为。

## 架构与代码

- `core/domain` 放业务模型、规则和用例，不依赖 Android、Compose、JSON 或网络实现。
- `core/data` 实现仓库和平台接口；存储、版本迁移、网络解析、导入导出各自负责明确的任务。
- `app/presentation` 负责界面状态和用户动作的协调。Compose 负责渲染和输入，不另存一份与 ViewModel 竞争的业务状态。
- 对并发、取消和持久化失败给出明确语义：失败保留草稿，旧任务不覆盖新输入，旧数据迁移不能覆盖用户定制。
- 保持常规 Kotlin 格式；避免把多个职责和多条语句压在一行。只在有真实复用或清晰职责边界时引入抽象。

## 验证和交付

- 保留已有记录、草稿、配置、导入导出兼容性；对改变存储格式的工作提供迁移与重载测试。
- 修改业务逻辑后运行对应单元测试；涉及界面交互、输入法或平台接口时运行设备回归。不能用构建成功代替功能验证。
- 真机 UI 测试使用 `-PisolatedUiTest=true` 独立包，禁止清空日常应用数据或往用户笔记写测试内容。
- 仅在测试遇到明确失败或代码再次变化时重复相关检查。报告实际验证范围，以及模拟服务和真实服务的区别。
- 提交使用本仓库配置的 `YTLog` 身份。只有用户要求提交或推送时才执行提交或推送。

## 常用命令

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :core:domain:test :app:testDebugUnitTest --offline --max-workers=2
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PisolatedUiTest=true --offline --max-workers=2
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:lintDebug --offline --max-workers=2
```

独立测试构建和日常构建共享输出目录。测试完成后不带 `isolatedUiTest` 参数重新构建日常 APK，并核对应用包名再安装。
