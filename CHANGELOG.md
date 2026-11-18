# 更新日志 / Changelog

## v2026.11.18 — 聊天内容显示修复

- 修复模型回复中的畸形链接、加粗和行内代码标记直接露出的问题；嵌套在加粗段里的反引号也会正确清理。
- 聊天正文按屏幕宽度自适应排版，代码块与来源不再挤在同一行或互相覆盖。
- 围栏代码块继续保持独立横向滚动、语言标记和一键复制。

---

## v2026.10.24.1 — 本机工具与系统能力大版本

- 新增 LinuxHub 本机 Agent 桥接：通过 `127.0.0.1:38473` 连接外部 Ubuntu 工作区，Token 只保存在本机；应用回到前台会自动重新握手，旧 Token 的请求不能覆盖新状态。
- 接入 11 个电脑工具：容器终端、无头桌面启动/状态/停止、X11 截图、鼠标、键盘、UTF-8 文字输入、图形应用启动、Shizuku 状态和系统信息；工具目录由 LinuxHub 实时返回。
- 设置新增“Lotus的电脑”：可粘贴、显示、保存、清空 Token，检测真实连接，查看可用能力并在本机容器执行命令；响应超时、401、异常和超长内容都会如实显示。
- 新增独立 Root 与 Shizuku 后端：Root 优先、Shizuku 兜底；Shizuku 使用独立 UserService 进程，授权只由用户主动触发，不在冷启动时弹窗。
- 新增系统工具白名单：读取设备身份、应用列表、前台应用和系统设置；可冻结或解冻非关键系统应用，关键应用与非法包名由客户端直接拒绝，不开放任意 shell。
- 聊天正式支持函数工具调用：流式拼接 `tool_calls` 参数，执行一次后把结构化结果回填给模型，再生成最终回答；模型看不到真实后端时不会假装已经执行。
- 聊天新增真实工具日志：回答过程中实时显示思考、工具名称、后端、耗时和结果明细；完成后可在“工具记录”中复查，成功和失败分别标为“已完成”“未完成”。
- 修复联网与 Shizuku 工具互斥：开启联网搜索后不再静默删除本机工具；中转站不接受混合 `tools` 时，优先保留 Shizuku/电脑工具，再按需退回纯联网或纯聊天。
- 流式协议增强：支持工具调用分段、更多 reasoning 字段结构、`finish_reason` 截断与内容过滤提示；经过工具调用的回答会累计两轮真实 token 用量，失败提示不再覆盖已经生成的内容。
- 模型显示名与上游模型 ID 分离：界面可以给模型起自己的名字，同时保留真实 ID 发送请求，不会因为改名把模型调坏。
- 工具页与系统工具页新增 Root/Shizuku 实时状态、身份、连接按钮和真实配置提示；未连接的能力不会显示为可用。
- 统一全应用交互动效：输入框、菜单、设置、工具面板和弹窗改用同一套 smooth / snappy / bouncy 弹簧参数，按下、回弹和相邻按钮联动的手感保持一致。
- 发布构建补强：正式签名从仓库外读取；R8 保留 Shizuku UserService；明文 HTTP 只允许 `127.0.0.1` / `localhost`，其他网络仍禁止明文。

---

## v2026.10.24.1 — Local tools and system capabilities

- Added the LinuxHub local-agent bridge over `127.0.0.1:38473`, connecting to the external Ubuntu workspace. The token stays on-device, the bridge re-handshakes whenever the app returns to the foreground, and a stale token request can no longer overwrite the current state.
- Integrated 11 computer tools: container terminal, headless desktop start/status/stop, X11 screenshots, mouse, keyboard, UTF-8 text input, graphical app launch, Shizuku status, and system information. The tool catalog is discovered live from LinuxHub.
- Added a “Lotus Computer” settings page for pasting, revealing, saving, and clearing the token, verifying the real connection, inspecting available capabilities, and running container commands. Timeouts, 401 responses, malformed payloads, and oversized responses are surfaced honestly.
- Added separate Root and Shizuku backends: Root is preferred and Shizuku is the fallback. Shizuku runs in a dedicated UserService process, and authorization is only requested by an explicit user action, never during cold start.
- Added a system-tool allowlist for reading device identity, installed apps, the foreground app, and system settings. Non-critical system apps can be frozen or unfrozen; critical packages and invalid package names are rejected by the client, and arbitrary shell access is not exposed.
- Added functional tool calling to chat: streamed `tool_calls` fragments are assembled, executed once, and fed back to the model as structured results before the final answer. The model cannot claim an action succeeded when no real backend was available.
- Added real tool activity logs in chat: reasoning, tool name, backend, duration, and result details are shown while the answer is running and remain available under “Tool records” afterward. Successful and failed calls are prefixed with “Completed” and “Not completed” respectively.
- Fixed the conflict between web search and Shizuku tools: enabling web search no longer silently removes local tools. When an upstream gateway rejects mixed `tools`, the client preserves Shizuku/computer tools first, then falls back to web-only or plain chat as needed.
- Strengthened streaming protocol handling with fragment assembly for tool calls, broader reasoning field support, `finish_reason` truncation/content-filter messages, and accumulated real token usage across tool round-trips. Failure messages no longer overwrite content that was already generated.
- Separated model display names from upstream model IDs: the UI can assign a friendly name while the real ID is still used for requests.
- Added live Root/Shizuku status, identity, connection actions, and honest configuration hints to the tools and system-tools pages. Unavailable capabilities are never presented as ready.
- Unified interaction motion across the composer, menus, settings, tool panels, and dialogs with one smooth / snappy / bouncy spring system. Press, release, and linked-button feedback now feel consistent throughout the app.
- Hardened release builds: signing credentials are loaded outside the repository, R8 preserves the Shizuku UserService, and cleartext HTTP is restricted to `127.0.0.1` / `localhost` while remaining disabled elsewhere.

---

## v2026.10.19.2 — 图片多选修复

- 图片选择器改为多选，单条消息最多可附加 9 张图片。
- 输入条支持多张图片缩略图横向预览、逐张移除。
- 同一条消息会把所有图片按 OpenAI 兼容格式发送给支持视觉的模型。
- 聊天记录保存并展示消息关联的多张图片；旧版单图会话继续兼容。

---

## v2026.10.19.2 — Multi-image selection fix

- The photo picker now supports multi-select, with up to 9 images per message.
- The composer previews attached images in a horizontally scrollable strip and allows removing them one by one.
- All images on a message are sent to vision-capable models using the OpenAI-compatible multi-image payload.
- Conversations persist and display multiple images per message while remaining compatible with older single-image sessions.

---

## v2026.10.19 — 记忆与聊天链路更新

- 首次启动重做：欢迎页 → 连接主模型 → 与小莲完成一轮真实初始化对话 → 进入主页；初始化阶段可以跳过，Key 和配置只保存在本机。
- 记忆链路重做：IDENTITY / SOUL / AGENTS / USER / MEMORY 每轮全量读取；自动记忆按主题原位覆盖，旧版“用户 · 自动维护”等尾巴会自动迁移，不再越堆越长。
- 补上记忆兜底：模型漏写隐藏标记时，客户端仍能从明确指令里记录称呼、语言、爱好和回答偏好。
- 思考过程接真实上游字段（`reasoning_content` / `reasoning` / `reasoning_text`）：回答时展开，结束后收起，点小莲头像可复查。
- 工具与来源可追溯：记录本轮真实读写记忆、整理提示词等动作；联网引用支持在应用内查看页面标题和打开来源。
- 聊天消息补齐一键复制、来源入口和系统文字选取；长按正文仍可选词、复制、全选，并修复链接解析导致的闪退。
- 默认提示词改为通用版；输入框从单行变多行时不再丢焦点、自动收起键盘。
- 上下文压缩真正生效：摘要只覆盖已压缩消息，后续请求只带摘要和未处理的新消息，原文仍可回看。
- 手动“记住这段”改为按主题原位合并到 USER / MEMORY，不再追加带日期的记忆快照。
- 默认模型页新增真实能力检测：视觉测试图、原生联网日期核对，以及中转站回显模型名的一致性检查。
- 会话与数据新增密码加密备份，可导入导出模型、会话、MD、头像和图片；错误密码不会破坏现有数据。
- 聊天操作统一到每条消息底部的同一行，移除重复的长按动作条和单独的文字复制键。
- 设置新增独立“版本日志”页，当前版本与历史版本分层保留；检查更新继续直连 GitHub Releases。
- “关于”页移除快速测试版落款；原生联网只在模型实测支持时启用，避免不支持的工具声明导致整轮请求失败。

---

## v2026.10.19 — Memory and chat pipeline update

- Rebuilt onboarding: welcome → connect the primary model → complete a real initialization conversation with Lotus → home. The initialization chat can be skipped; keys and configuration stay on-device.
- Rebuilt the memory pipeline: IDENTITY / SOUL / AGENTS / USER / MEMORY are read in full on every turn. Automatic memory now replaces entries by topic in place, and legacy “auto-maintenance” tails are migrated instead of accumulating.
- Added a client-side memory fallback for explicit instructions when the model omits its hidden write marker.
- Reasoning is wired to real upstream fields (`reasoning_content` / `reasoning` / `reasoning_text`), expands while answering, collapses afterward, and can be reopened from Lotus's avatar.
- Tool activity and web sources are traceable. Web citations open in-app with page titles instead of treating links in the answer as citation records.
- Messages now include one-tap copy, source entry points, and system text selection. A crash caused by URL parsing was also fixed.
- The default prompt is now generic, and the composer keeps focus when growing from one line to multiple lines.
- Context compression now changes what is sent: the summary covers compressed messages, and later requests send only the summary plus newer messages while keeping the original transcript visible.
- “Remember this” now merges topics into USER / MEMORY in place instead of appending dated memory snapshots.
- The default-model page can probe real capabilities: a vision test image, native-web-search date check, and the upstream model-name echo for consistency.
- Encrypted password backups can export and import models, conversations, Markdown, avatars, and images; a wrong password cannot overwrite current data.
- Message actions now live in one row at the bottom of each message; the duplicate long-press bar and separate text copy chip were removed.
- Added a dedicated version-log page with current and historical releases; update checks continue to use GitHub Releases.
- Removed the rapid-test footer from About. Native web search is now sent only when the model has passed the real capability probe, so unsupported tool declarations cannot break a normal chat turn.

---

## v2026.10.11 — 首个公开版本

- 发布应用。
- 全局液态玻璃：低模糊 + 边缘折射 + 色散，纯白底上也能一眼看出是玻璃。
- MD 记忆体系：IDENTITY / SOUL / AGENTS / USER / MEMORY 五份提示词文件 + memory/ 档案柜。
- 模型能自己改提示词：追加用 `write`，整理自己的记忆段用 `tidy`。
- 生图接进对话：出图时先摆一块扫色散的玻璃占位，图到了再收清楚。
- 输入条多行自增高，删空自动缩回。
- 附件支持图片 / 视频 / 文件。

---

## v2026.10.11 — first public release

- First release.
- Liquid Glass everywhere: light blur + edge refraction + chromatic dispersion, legible even on a
  pure white canvas.
- Markdown memory system: IDENTITY / SOUL / AGENTS / USER / MEMORY plus the `memory/` archive.
- The model can edit its own prompts: append with `write`, tidy its own memory section with `tidy`.
- Image generation wired into the conversation, with a glass placeholder while it renders.
- Composer grows with multi-line input and shrinks back when cleared.
- Attachments: image / video / file.
