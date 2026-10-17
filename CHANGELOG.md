# 更新日志 / Changelog

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
