<p align="center">
  <img src="assets/banner.png" alt="Lotus AI" width="100%">
</p>

<h1 align="center">Lotus AI</h1>

<p align="center">
  <strong>把模型、记忆和设备工具，放进同一个聊天界面。</strong>
</p>

<p align="center">
  Android · Liquid Glass · Markdown Memory · Local Tools
</p>

<p align="center">
  <b>中文</b> · <a href="README.en.md">English</a> ·
  <a href="../../releases/latest">下载 APK</a> ·
  <a href="CHANGELOG.md">更新日志</a>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B%20(API%2028)-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09.00-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/License-Apache--2.0-blue">
</p>

---

Lotus AI 不是只换了一层皮肤的聊天客户端。

它把多个 OpenAI 兼容模型、可编辑的人格与长期记忆、真实思考过程、上下文用量、图片理解与生图，以及 Android 本机工具，收进同一个以液态玻璃为视觉语言的 Android 应用。

模型由你接入，记忆留在本机，工具必须由你明确触发。

## 现在能用什么

| 能力 | 你可以做什么 |
| --- | --- |
| 多模型聊天 | 配置多个 OpenAI 兼容提供方，分别设置 Base URL、API Key、模型和上下文窗口 |
| 流式对话 | 边生成边阅读，支持停止、重新回答、编辑、复制、引用与会话标题 |
| 真实思考过程 | 读取上游 `reasoning_content`、`reasoning_text` 等字段，回答中展开，结束后收起 |
| 模型能力实测 | 检测图片输入、原生联网、思考档位和上游回显模型名，不靠猜 |
| Markdown 人格与记忆 | 直接编辑 IDENTITY、SOUL、AGENTS、USER、MEMORY 与归档目录 |
| 图片理解与生图 | 接收多张图片，并通过独立生图模型在对话中直接产图 |
| Android 系统工具 | 通过 Root 或 Shizuku 读取设备信息、应用列表、前台应用和系统设置，冻结或解冻白名单应用 |
| LinuxHub 电脑工具 | 连接本机 Ubuntu 环境，使用终端、无头桌面、截图、鼠标、键盘和图形应用 |
| 工具执行记录 | 查看工具名、后端、耗时、结果和失败原因，成功与失败不会混在一起 |
| 上下文管理 | 查看真实或明确标注的估算 token 用量，压缩摘要，并保留原对话 |
| 会话管理 | 新建、重命名、归档、恢复、永久删除和生成话题标题 |
| 本机数据 | API Key、对话、提示词、记忆、头像和图片保存在应用私有目录，可加密备份 |

## 对话不是唯一功能

### 一套你能打开、修改和迁移的记忆

Lotus AI 不使用封闭的记忆数据库。人格、偏好和长期事实都是普通 Markdown：

```text
filesDir/md/
├── IDENTITY.md      我是谁、如何自称
├── SOUL.md          性格、语气、审美与表达方式
├── AGENTS.md        行为规则与边界
├── USER.md          用户称呼、偏好与长期画像
├── MEMORY.md        长期事实、约定与关系背景
├── CAPABILITIES.md  当前能力与使用说明
└── memory/          项目、决定、事实、课程等明细档案
```

- 开启提示词后，核心 Markdown 会在每轮对话中读取，不依赖不可见的向量库。
- 模型写回记忆时按主题**原位合并**，不会一轮一轮追加重复快照。
- 模型漏写隐藏标记时，客户端仍会从明确指令中兜底记录称呼、语言和偏好。
- 你也可以在聊天里选择“记住这段”，或在文件页直接修改。
- 会话、提示词、记忆和配置都能进入密码加密备份。

### 模型能力按真实结果开放

Lotus AI 使用通用 OpenAI 兼容协议，不绑定单一厂商。

- 每个提供方独立保存地址、Key、模型目录与上下文窗口。
- 模型显示名和真实上游 ID 分离：界面可以起中文名，请求仍使用真实模型 ID。
- 思考档位会向上游验证；模型不接受 `max` 或 `ultra`，界面就不会假装支持。
- 原生联网只在能力检测通过后发送，避免不支持的工具声明导致整轮请求失败。
- 视觉能力通过测试图片检测；生图使用独立的多模态服务。
- SSE 流式解析覆盖文本、推理、引用、usage、工具调用分片和异常结束状态。

### 工具真的会执行，而不是只写在回答里

聊天支持单步 function calling：模型发起工具调用，Lotus 执行一次，把结构化结果回填给模型，然后生成最终回答。工具不可用或执行失败时，结果会明确标记为“未完成”。

#### Root / Shizuku 系统工具

| 工具 | 作用 |
| --- | --- |
| `system_device_status` | 设备身份、Android 版本、机型和当前特权后端 |
| `system_list_apps` | 查询已安装应用，按包名或应用名筛选 |
| `system_foreground_app` | 当前前台应用与 Activity |
| `system_read_setting` | 只读访问 `system`、`secure`、`global` 设置 |
| `system_set_app_enabled` | 冻结或解冻用户明确指定的非关键应用 |

Root 优先，Shizuku 作为兼容后端。授权只由你主动触发，不在冷启动时弹窗。客户端不向模型开放任意 shell，关键系统应用和非法包名会直接拒绝。

#### LinuxHub 电脑桥

在设置中填入 LinuxHub 生成的本机 Token，即可通过 `127.0.0.1:38473` 使用：

- 容器终端
- 无头桌面启动、状态与停止
- X11 截图
- 鼠标、按键和 UTF-8 文字输入
- 图形应用启动
- Shizuku 状态与系统信息

Token 只保存在本机，不写日志、不进入模型上下文。应用回到前台会重新握手，只有健康检查和工具目录都通过后，电脑工具才会出现在聊天里。

### 上下文和成本看得见

- 服务端返回真实 token 时显示真实值；拿不到时明确标为估算。
- 可设置每个模型的上下文窗口，查看当前占用和剩余空间。
- 压缩后的请求只发送“摘要 + 新消息”，原对话仍保留在界面中。
- 可选择显示模型、用户头像、完成时间、token 数和文字数。
- 工具调用产生的两轮真实 token 用量会累计，而不是只算最后一轮。

### 液态玻璃是一套完整材质

界面不是“半透明背景 + 高斯模糊”：

- 低模糊、边缘折射、色散和高光贯穿主页、聊天、菜单、设置与弹层。
- 按键按压缩放、回弹，并与相邻控件使用统一的 smooth / snappy / bouncy 动效参数。
- 输入框随内容增长并自动收回，不丢失焦点或擅自关闭键盘。
- 文件页、应用内来源阅读器、统计、版本日志和系统工具页使用同一套视觉语言。

### 对话体验

- SSE 流式回答，首字到达即可阅读。
- 思考过程、工具记录和联网来源可以展开复查。
- 长按正文支持系统级选取、复制与全选。
- 图片、视频和文件可以直接从输入条添加。
- 联网来源在应用内阅读器打开，不中断当前会话。
- 会话自动生成标题，支持重命名、归档、恢复与永久删除。
- 首次启动通过一轮真实对话完成称呼、语言和表达偏好初始化。

## 本地优先

- 不需要账号，没有遥测。
- API Key、对话和记忆保存在应用私有目录。
- 加密备份包含模型配置、会话、Markdown、头像和图片；错误密码不会覆盖现有数据。
- 完整源码使用 Apache License 2.0 开源。

## 界面预览

| 主页 | 对话 |
| --- | --- |
| <img src="assets/screenshots/home.png" width="330" alt="Lotus AI 主页"> | <img src="assets/screenshots/chat.png" width="330" alt="Lotus AI 对话页"> |

| 人格与记忆文件 | 模型能力检测 |
| --- | --- |
| <img src="assets/screenshots/files.png" width="330" alt="Lotus AI 人格与记忆文件页"> | <img src="assets/screenshots/model.png" width="330" alt="Lotus AI 模型能力检测"> |

## 快速开始

### 环境

- Android 8.0（API 28）或更高版本
- JDK 17
- Android SDK Platform 37

### 构建

```bash
git clone https://github.com/xishanp/LotusAI.git
cd LotusAI
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次启动后填写主模型的 OpenAI 兼容 `Base URL`、`API Key` 和模型 ID。Shizuku、Root 和 LinuxHub 都是可选能力，不配置也能正常聊天。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 2.4.20 · Jetpack Compose BOM 2026.09.00 |
| 构建 | AGP 9.4.1 · Gradle 9.7.1 · JDK 17 |
| 系统范围 | Android 8.0+ · `compileSdk 37` · `targetSdk 35` |
| 网络 | `HttpURLConnection` + 手写 SSE 解析 |
| 序列化 | kotlinx.serialization |
| 液态玻璃 | Kyant0 `backdrop` / `shapes` + Chris Banes `haze` |
| 系统工具 | Root `su` + Shizuku UserService |
| 存储 | 本机 JSON + 纯文本 Markdown |

## 项目结构

```text
app/src/main/java/ai/lotus/app/
├── MainActivity.kt              应用入口、启动页衔接与前台重连
├── data/
│   ├── LlmApi.kt                OpenAI 兼容流式聊天与工具调用
│   ├── Prompts.kt               人格、记忆、上下文与隐藏写入协议
│   ├── ModelCapabilities.kt     视觉、联网与模型一致性探测
│   ├── ThinkingTiers.kt         真实思考档位探测
│   ├── ImageGen.kt              对话内图片生成
│   └── BackupManager.kt         加密导入与导出
├── computer/                    LinuxHub 本机 HTTP 工具桥
├── shizuku/                     Root / Shizuku 后端与白名单工具
└── ui/
    ├── glass/                   液态玻璃基础设施
    ├── home/                    首页、输入条与浮层
    ├── chat/                    对话、消息动作与生成动画
    ├── md/                      Markdown 人格与记忆工作区
    ├── reader/                  应用内来源阅读器
    ├── computer/                Lotus的电脑设置与工具面板
    ├── settings/                模型、系统工具、数据与关于
    └── motion/                  全局动效参数
```

## 版本与更新

当前版本：`2026.10.24.1`

`设置 → 关于 → 检查更新` 会读取 GitHub Releases 的最新版本并与本机比较。发现新版本时只打开下载页，不会后台静默安装。

完整记录见 [CHANGELOG.md](CHANGELOG.md)，也可以在应用内通过 `设置 → 关于 → 版本日志` 查看。

## 设计素材

| 小莲 | 思考 | 阅读 Markdown |
| --- | --- | --- |
| <img src="assets/gallery/01-mascot.png" width="230" alt="Lotus AI 小莲"> | <img src="assets/gallery/02-thinking.png" width="230" alt="Lotus AI 思考状态"> | <img src="assets/gallery/03-md-reading.png" width="230" alt="Lotus AI 阅读 Markdown"> |

| 应用图标 | 首页设计稿 |
| --- | --- |
| <img src="assets/gallery/04-app-icon.png" width="230" alt="Lotus AI 应用图标"> | <img src="assets/gallery/05-home-approved.png" width="330" alt="Lotus AI 首页设计稿"> |

## 开源许可

本项目以 [Apache License 2.0](LICENSE) 开源。第三方依赖与素材说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 致谢

液态玻璃的折射、色散、模糊与高光建立在这些开源项目之上：

| 项目 | 用途 | 许可 |
| --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | 折射玻璃、色散、高光与形状基础 | Apache-2.0 |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | 模糊与材质底座 | Apache-2.0 |
| [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) | 特权 Binder 与 UserService | Apache-2.0 |

也感谢所有让 Android 上的表达方式继续变得更好的开源作者。
