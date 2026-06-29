# Noter

Noter 是一个语音优先的 Android 闹钟应用，使用 Kotlin 和 Jetpack Compose 构建。它支持手动管理闹钟、通过 OpenRouter 进行 AI 辅助创建、精准闹钟调度、英文/中文界面，以及后台状态通知。

English documentation: [README.md](README.md)

## 当前状态

Noter 目前是仍在活跃开发中的 Android MVP。已经实现的主要流程包括：

- 按住说话创建语音闹钟，优先使用 Android 系统语音识别，失败时回退到 OpenRouter ASR。
- 通过基于工具调用的 OpenRouter agent loop 进行文本 AI 闹钟创建。
- 手动创建、编辑、删除和查看闹钟。
- 一次性、每天、工作日、自定义星期、按周间隔重复等规则。
- 精准闹钟调度、开机/启动状态恢复、响铃服务和停止通知。
- OpenRouter API key、LLM 模型、ASR 模型、默认铃声和 Android 权限设置。
- 英文和简体中文字符串资源。

## 技术栈

- Kotlin 2.2
- Android Gradle Plugin 8.13
- Jetpack Compose Material 3
- Room
- DataStore
- WorkManager
- Kotlin serialization
- OkHttp
- JUnit、Robolectric、AndroidX Test、Compose UI tests

## 环境要求

- JDK 17
- 已安装 API 35 的 Android SDK
- Android Studio 或 Gradle CLI
- 使用 AI 和 ASR 功能时需要 OpenRouter API key

应用目标 SDK 为 Android 35，最低支持 Android 8.0（`minSdk 26`）。

## 快速开始

克隆仓库后构建 debug APK：

```sh
./gradlew assembleDebug
```

安装到已连接的设备：

```sh
./gradlew installDebug
```

运行单元测试：

```sh
./gradlew testDebugUnitTest
```

运行 lint：

```sh
./gradlew lintDebug
```

有设备或模拟器时运行连接端 UI 测试：

```sh
./gradlew connectedDebugAndroidTest
```

## 使用 AI 功能

AI 创建和 OpenRouter ASR 需要 OpenRouter API key。

1. 打开 Noter。
2. 进入设置。
3. 输入并保存 OpenRouter API key。
4. 选择 LLM 模型和语音识别模型。
5. 使用语音首页或文本 AI 创建页面。

默认语音路径会先尝试 Android 系统语音识别。如果系统语音识别不可用或失败，Noter 会录制临时音频，并发送到配置的 OpenRouter ASR 模型。

## 权限

完整闹钟体验需要授权：

- 麦克风：用于语音闹钟录制。
- 通知：Android 13 及以上用于闹钟通知和 AI 创建状态通知。
- 精准闹钟：用于在准确分钟可靠触发闹钟。
- 电池优化豁免：建议开启，以提高后台可靠性。

设置页面会为需要处理的权限提供恢复入口。

## 项目结构

```text
app/src/main/java/com/cory/noter/
  agent/          与供应商无关的工具调用 agent runtime
  agent/tools/    闹钟、终止任务和澄清请求工具
  alarm/          Android 闹钟调度、广播接收器和响铃服务
  data/           Room 和 DataStore 持久化
  di/             应用容器装配
  domain/         闹钟、设置和 AI 领域模型
  notifications/  AI 创建和响铃通知
  ui/             Compose 页面和 ViewModel
  voice/          语音采集、系统 STT、ASR 回退和清理边界

docs/
  architecture/   架构说明
  project/        项目上下文
  testing/        测试策略
  superpowers/    spec、plan 和模板

artifacts/        验证日志和手工测试证据
```

## 验证门禁

当前仓库的本地验证门禁是：

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

有设备或模拟器时，还应运行：

```sh
./gradlew connectedDebugAndroidTest
```

新的验证证据会记录在 `artifacts/` 目录下。

## 发布说明

GitHub release 工作流默认可以构建未签名的 release APK。若要产出已签名的 release APK，需要配置以下仓库 secrets：

- `ANDROID_SIGNING_KEYSTORE_BASE64`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_PASSWORD`

## 开发文档

- [仓库指南](AGENTS.md)
- [架构概览](docs/architecture/overview.md)
- [分层说明](docs/architecture/layers.md)
- [测试策略](docs/testing/strategy.md)
- [进度记录](PROGRESS.md)
- [下一步指针](NEXT_STEP.md)
- [长期记忆](MEMORY.md)
