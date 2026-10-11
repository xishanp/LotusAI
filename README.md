<p align="center">
  <img src="assets/banner.png" alt="Lotus AI" width="100%">
</p>

<h3 align="center">纯聊天 · 液态玻璃 · 有记忆的 AI</h3>

<p align="center">
  <b>中文</b> · <a href="README.en.md">English</a>
</p>

---

**Lotus AI** 是一个安卓端的**纯聊天应用**。不做 Agent，不堆工具链，只把「一次对话」做到最好：
**一次提问 = 一次模型调用**，token 消耗压到最低、回答足够快，同时保留一套**完整的记忆框架**
和**可改写的人设与提示词**。

## 为什么不是「又一个 Agent」

同一个模型，聊天工具的账单可以是 Agent 的几分之一 —— 差别不在模型，在**请求里塞了什么**：

| | 普通聊天（Lotus 这一列） | Agent / Codex / OpenClaw 那一类 |
| --- | --- | --- |
| 一次提问 | **一次调用** | 一次调用 × 多轮工具循环 |
| 系统提示词 | 人设 + 记忆 | 人设 + 记忆 + 几十个工具的说明书 |
| 上下文 | 你说过的话 | 工具结果、文件内容、日志全塞进去 |
| 体感 | 快、便宜 | 慢、贵 |

Lotus 只站在左边这一列。**技能 / 工具 / MCP 一律不做** —— 那正是上面那张表右列的来源。

## 界面：全局液态玻璃

- 全部玻璃统一为：**低模糊 + 边缘折射 + 色散 + 白色内描边 + 外柔影**，纯白底、零彩色。
- 同一块玻璃里**不会**再长出一块小玻璃：嵌在里面的按键静止时隐形，按下去才浮出折射。
- 每个按键都有真实反馈 —— 按压缩放、边缘折射，相邻的玻璃会轻轻联动。

## 记忆与人格：一套可以直接改的 MD 文件

人格和记忆都是**纯文本**，在「文件」页里随时能改：

```
filesDir/md/
├── IDENTITY.md   我是谁        ← 每轮注入
├── SOUL.md       怎么说话       ← 每轮注入
├── AGENTS.md     规则与边界     ← 每轮注入
├── USER.md       关于你        ← 每轮注入
├── MEMORY.md     长期记忆       ← 每轮注入
└── memory/       档案柜（不注入）
    facts · decisions · lessons · projects · references · tasks · temporary
```

- **自动记忆**：模型把该长期记住的事写成一行 `<!--memo: 你叫 K -->`，客户端在**同一次回答**里把
  标记剥掉并追加进 `MEMORY.md` —— 不产生第二次请求，也不额外烧 token。
- **手动记忆**：聊天页 `⋯ → 记住这段`，把当前会话压成几条长期记忆。
- **会话话题**：每个会话首轮答完自动生成一次标题。

## 模型

- **OpenAI 兼容协议**；提供方、地址、Key **全部由你自己填**，App 不预置任何厂商。
- 思考档位按模型的真实能力映射（`medium` / `high` / `xhigh` … 到 `max` / `ultra`），不多不少。
- 联网搜索是可选项：打开后请求体里只多一条 `tools:[{"type":"web_search"}]`，搜索交给上游，
  客户端**仍然只有一次调用**。

## 截图

| 主页 | 聊天 |
| --- | --- |
| <img src="assets/screen-home.png" width="330"> | <img src="assets/screen-chat.png" width="330"> |

| 文件（人格与记忆） | 关于 |
| --- | --- |
| <img src="assets/screen-files.png" width="330"> | <img src="assets/screen-about.png" width="330"> |

## 开源依赖

液态玻璃不是写一个 `blur()` 就能了事的，感谢上游：

| 依赖 | 用途 | 许可 |
| --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（`io.github.kyant0:backdrop` / `shapes`） | 折射 / 色散 / 模糊的底座 | Apache-2.0 |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | 模糊底座 | Apache-2.0 |

## 下载

到 [Releases](../../releases) 里取 APK。当前是**快速测试版**（debug 签名）。

App 内也可以直接查：`设置 → 关于 → 检查更新`，读的就是本仓库最新的 Release 版本号。

---

**此为快速版，本周更新。**
