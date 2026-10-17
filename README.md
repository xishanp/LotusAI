<p align="center">
  <img src="assets/banner.png" alt="Lotus AI" width="100%">
</p>

<h1 align="center">Lotus AI</h1>

<p align="center">
  <strong>当对话有了记忆，玻璃也有了呼吸。</strong>
</p>

<p align="center">
  Android · 全局液态玻璃 · Markdown 人格与记忆 · OpenAI Compatible
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

## 关于 Lotus AI

Lotus AI 是一个为 Android 重新设计的 AI 对话空间。

它把真正影响日常体验的几件事放在一起：统一而克制的液态玻璃界面、稳定可改的人物设定、能长期维护的 Markdown 记忆、清晰的上下文成本，以及不受单一厂商限制的模型接入。

模型由你选择，人设由你定义，记忆留在本机。没有多余的工具栏，也没有被隐藏的上下文消耗。

## 为什么是 Lotus AI

### 液态玻璃，不只是一层毛玻璃

Lotus AI 的玻璃不是半透明背景加模糊，而是一套贯穿整个界面的材质系统：

- **低模糊 + 边缘折射 + 色散**，在纯白背景上仍能看出玻璃的厚度与光线变化。
- 按键按压缩放、随手指方向倾倒，相邻玻璃会产生轻微联动。
- 玻璃面板、输入框、菜单、弹层与阅读器共享同一套视觉语言。
- 静态时保持干净，操作时才释放折射、拉伸与柔和高光。

玻璃应当帮助内容呼吸，而不是抢走注意力。

### 一套真正可维护的人格与记忆

Lotus AI 不使用封闭的“记忆数据库”。人格、偏好和长期记忆都是可以直接打开、直接修改的 Markdown 文件：

```text
filesDir/md/
├── IDENTITY.md      我是谁、如何自称
├── SOUL.md          性格、语气、审美与表达方式
├── AGENTS.md        行为规则与边界
├── USER.md          你的称呼、偏好与长期画像
├── MEMORY.md        长期事实、约定与关系背景
├── CAPABILITIES.md  当前能力与使用说明
└── memory/          项目、决定、事实、课程等明细档案
```

开启提示词与记忆后，`IDENTITY / SOUL / AGENTS / USER / MEMORY` 会在每轮对话中完整读取。`memory/` 作为明细档案保留，不强行塞进每一次请求。

模型可以在同一次回答里写入隐藏标记，客户端摘出标记并按主题**原位合并**到对应文件；不是一遍遍向后追加。你也可以在聊天页手动选择“记住这段”，或随时进入文件页亲自修改。

人设不是一次性配置。它会随着对话被校准，也能被完整清空、备份和迁移。

### 上下文看得见，也管得住

Lotus AI 会区分两件事：**服务端返回的真实 token**，以及客户端在拿不到 usage 时给出的明确估算。不会把估算值伪装成计费结果。

- 每个模型提供方可以设置上下文窗口。
- 会话会显示当前上下文占用与剩余空间。
- 支持上下文压缩：压缩摘要只覆盖已经处理过的消息，后续请求发送“摘要 + 新消息”，原始对话仍保留在界面中。
- 聊天页可以按需显示模型、头像、完成时间、token 与文字数量。
- 每轮实际发送的是当前人设、记忆、摘要和仍在上文窗口内的对话，不额外夹带无关内容。

### 模型由你接入，能力按真实结果说话

Lotus AI 使用通用的 OpenAI 兼容协议，不绑定任何单一模型厂商。

- 可配置多个模型提供方，每个提供方独立保存 Base URL、API Key、模型列表和上下文窗口。
- 通过 `/models` 拉取模型目录，并提供连接测试。
- 聊天使用 `/chat/completions` 流式响应，思考内容读取上游真实的 `reasoning_content`、`reasoning` 或 `reasoning_text` 字段。
- 思考档位不是写死的装饰项。客户端会向上游逐档验证，模型真正接受哪些档位，界面才开放哪些档位。
- 可检测模型是否支持图片输入、原生联网，以及中转站回显的模型名是否与所选模型一致。
- 生图通过独立的多模态服务接入，出图时会在对话中显示生成状态，完成后直接落到消息里。
- 原生联网只在模型实测支持时启用，避免不支持的工具声明让普通聊天失败。

### 对话本身该有的细节

- SSE 流式回答，首字到达即可阅读。
- 回答过程中展开真实思考与工具记录，结束后收起；点小莲头像可以再次查看。
- 消息底部提供复制、引用、编辑重发、重新回答与撤回。
- 长按正文支持系统级选取、复制和全选。
- 联网来源使用应用内阅读器打开，不打断当前对话。
- 图片、视频和文件可以直接从输入条添加。
- 会话自动生成标题，支持重命名、归档、恢复与永久删除。
- 首次启动提供引导流程：连接主模型，再通过一轮真实对话完成称呼、语言和表达偏好初始化。

### 本机优先，数据可以带走

- 不需要账号，没有遥测。
- API Key 与对话数据保存在应用私有目录。
- 会话、模型配置、Markdown 文件、头像和图片可以导出为密码加密备份；错误密码不会覆盖现有数据。
- 完整源码以 Apache License 2.0 开源。

## 界面预览

| 主页 | 对话 |
| --- | --- |
| <img src="assets/screenshots/home.png" width="330" alt="Lotus AI 主页"> | <img src="assets/screenshots/chat.png" width="330" alt="Lotus AI 对话页"> |

| 人格与记忆文件 | 模型能力实测 |
| --- | --- |
| <img src="assets/screenshots/files.png" width="330" alt="Lotus AI 人格与记忆文件页"> | <img src="assets/screenshots/model.png" width="330" alt="Lotus AI 模型能力检测结果"> |

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
```

安装调试包：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次启动后，按引导填写 OpenAI 兼容接口的 `Base URL`（通常到 `/v1`）、`API Key` 和模型 id。主模型连接成功后即可开始对话。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 2.4.20 · Jetpack Compose BOM 2026.09.00 |
| 构建 | AGP 9.4.1 · Gradle 9.7.1 · JDK 17 |
| 系统范围 | Android 8.0+ · `compileSdk 37` · `targetSdk 35` |
| 网络 | `HttpURLConnection` + 手写 SSE 解析 |
| 序列化 | kotlinx.serialization |
| 液态玻璃 | Kyant0 `backdrop` / `shapes` + Chris Banes `haze` |
| 存储 | 本机 JSON + 纯文本 Markdown |

## 项目结构

```text
app/src/main/java/ai/lotus/app/
├── MainActivity.kt              应用入口与系统启动页衔接
├── data/
│   ├── LlmApi.kt                OpenAI 兼容流式聊天
│   ├── Provider.kt              多模型提供方与选择持久化
│   ├── Prompts.kt               人格、记忆、上下文与隐藏写入协议
│   ├── ModelCapabilities.kt     视觉、联网与模型一致性探测
│   ├── ThinkingTiers.kt         真实思考档位探测
│   ├── ImageGen.kt              对话内图片生成
│   ├── BackupManager.kt         加密导入与导出
│   └── Changelog.kt             应用内版本日志
└── ui/
    ├── LotusApp.kt              页面外壳与固定层
    ├── glass/                   液态玻璃基础设施
    ├── home/                    首页、输入条与浮层
    ├── chat/                    对话、消息动作与生成动画
    ├── onboarding/              首次使用引导
    ├── md/                      Markdown 人格与记忆工作区
    ├── reader/                  应用内来源阅读器
    ├── settings/                模型、记忆、数据与关于
    └── nav/                     底部导航
```

## 版本与更新

当前版本：`2026.10.19`

`设置 → 关于 → 检查更新` 会读取 GitHub Releases 的最新版本，并与本机版本比较。发现新版本时只打开下载页，不会在后台静默安装 APK。

完整版本记录见 [CHANGELOG.md](CHANGELOG.md)，也可以在应用内通过 `设置 → 关于 → 版本日志` 查看。

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

液态玻璃的折射、色散、模糊与高光建立在这些优秀开源项目之上：

| 项目 | 用途 | 许可 |
| --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | 折射玻璃、色散、高光与形状基础 | Apache-2.0 |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | 模糊与材质底座 | Apache-2.0 |

也感谢所有让 Android 上的表达方式继续变得更好的开源作者。
