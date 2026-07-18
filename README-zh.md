# Noter

Noter 是一个使用 Kotlin 和 Jetpack Compose 构建的语音优先 Android 闹钟应用，支持本地闹钟管理、文字和语音辅助创建、精准闹钟调度、中英文界面以及后台状态通知。

English documentation: [README.md](README.md)

## 当前状态

Noter 将 agent loop、闹钟/日历工具、调度、权限和所有本地写入保留在 Android 端。AI 服务通过仓库自有的 first-party API 访问：

- 文字创建把本地 agent 消息、工具 schema 和工具结果发送到 `POST /api/v1/agent/completions`。
- 语音创建录制临时 m4a 音频，并发送到 `POST /api/v1/asr/transcriptions`。
- 服务端统一选择上游地址、凭据、模型、限制和超时；应用内不提供 provider、API key 或模型设置。
- 设置页保留外观、声音、日历和设备权限恢复入口。
- 前台请求和后台任务使用有边界且职责清晰的重试策略；本地写入一旦提交，结果优先级保持不变。

## 技术栈

- Kotlin 2.2
- Android Gradle Plugin 8.13
- Jetpack Compose Material 3
- Room、DataStore、WorkManager
- Kotlin serialization、OkHttp
- `server/noter-api` 使用 TypeScript、Fastify 和 Node.js 20
- 生产环境使用 Docker Compose 和 Caddy
- JUnit、Robolectric、AndroidX Test、Compose UI tests、Vitest

## 环境要求

- JDK 17
- 已安装 API 35 的 Android SDK
- Android Studio 或 Gradle CLI
- 后端开发需要 Node.js 20 和 npm
- 容器和 smoke check 需要 Docker、Docker Compose

应用目标 SDK 为 Android 35，最低支持 Android 8.0（`minSdk 26`）。

## 快速开始

构建 debug APK：

```sh
./gradlew assembleDebug
```

安装到已连接设备：

```sh
./gradlew installDebug
```

运行 Android 检查：

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebugAndroidTest
```

有设备或模拟器时运行连接端 UI 测试：

```sh
./gradlew connectedDebugAndroidTest
```

## First-Party API 开发

Android 构建输入来自构建环境或未跟踪的 `local.properties` 文件，不在应用设置中输入：

```properties
NOTER_API_BASE_URL=https://api.example.com
NOTER_CLIENT_TOKEN=构建时使用的访问令牌
```

Release workflow 从 repository variable 读取 `NOTER_API_BASE_URL`，从 repository secret 读取 `NOTER_CLIENT_TOKEN`。缺少任一输入时，release 构建会提前失败。

后端是独立 npm 包：

```sh
cd server/noter-api
npm ci
npm run format:check
npm run lint
npm run typecheck
npm test
```

服务端配置从部署环境加载。上游 key 和模型名只放在生产服务器私有的 `/opt/noter/.env` 中，不复制到 Android，也不提交到仓库。

## 本地容器 Smoke Check

打包的 smoke stack 使用 fake compatible upstream，不需要真实付费凭据：

```sh
./deploy/production/tests/fake-upstream-smoke.sh
```

生产 Compose 只由 Caddy 对外发布 80 和 443 端口，API 容器不发布 host port。`deploy/production/deploy.sh` 只接受不可变的 GHCR 镜像引用；readiness 失败时会恢复旧镜像和配置。

## 权限

完整闹钟体验需要授权：

- 麦克风：用于语音录制。
- 通知：Android 13 及以上用于闹钟和 AI 状态通知。
- 精准闹钟：用于可靠的分钟级触发。
- 电池优化豁免：建议开启，以提高后台可靠性。
- 日历访问：仅在请求日历同步时需要。

设置页会为需要处理的设备权限提供恢复入口。

## 项目结构

```text
app/src/main/java/com/cory/noter/
  agent/          Android 端 agent loop 和协议
  agent/tools/    闹钟、管理、终止任务和澄清工具
  alarm/          调度、广播接收器、状态恢复和响铃服务
  data/           Room 和 DataStore 持久化
  di/             应用容器装配
  domain/         闹钟、设置和 AI 领域模型
  notifications/  创建和响铃通知
  ui/             Compose 页面和 ViewModel
  voice/          m4a 采集、first-party ASR 和清理边界

server/noter-api/
  src/            Fastify 路由、配置、服务和上游适配器
  test/           contract、限制、隐私、provider 和错误测试

deploy/production/
  compose.yml     私有 API 网络和公开 Caddy 入口
  Caddyfile       直连 DNS 的 HTTPS 反向代理
  deploy.sh       不可变镜像部署和回滚
  tests/          fake-upstream smoke 和部署回滚测试
```

## 验证门禁

最强的本地 Android 门禁是：

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
```

后端或部署改动还应运行 npm 门禁、Compose 校验、部署脚本测试和 fake-upstream smoke。实现交付时，新的证据会记录到 `artifacts/`。

## 发布输入

Android release workflow 需要：

- Repository variable `NOTER_API_BASE_URL`
- Repository secret `NOTER_CLIENT_TOKEN`

可选的签名 secrets：

- `ANDROID_SIGNING_KEYSTORE_BASE64`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_PASSWORD`

后端 workflow 发布 `ghcr.io/<owner>/noter-api:<完整 commit SHA>`。在生产服务器、known-host 和凭据准备完毕前，SSH 部署保持关闭；只有显式设置 `PRODUCTION_DEPLOY_ENABLED=true` 后才会启用。

## 开发文档

- [仓库指南](AGENTS.md)
- [架构概览](docs/architecture/overview.md)
- [分层说明](docs/architecture/layers.md)
- [测试策略](docs/testing/strategy.md)
- [进度记录](PROGRESS.md)
- [下一步指针](NEXT_STEP.md)
- [长期记忆](MEMORY.md)
