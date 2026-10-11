<p align="center">
  <img src="assets/banner.png" alt="Lotus AI" width="100%">
</p>

<h3 align="center">液态玻璃 · 有记忆的 AI 对话</h3>

<p align="center">
  <b>中文</b> · <a href="README.en.md">English</a>
</p>

---

**Lotus AI** 是一个安卓端 AI 对话应用。界面是一整片液态玻璃，对话本身干净、利落、回得快。

它记得住你、听得懂人设，token 消耗压到最低 —— 聊起来没有负担。

## 为什么聊得顺

- **轻。** 一次提问 = 一次模型调用。请求里只有人设、记忆和你说过的话，没有别的东西拖慢它，
  每一分 token 花在哪都看得见。
- **快。** 流式回答，边生成边显示。思考能力保留，档位跟着模型走 —— 模型支持几档就给几档。
- **省。** 上下文用量实时可见，可以按会话裁剪、压缩；压缩只动摘要，原文一条不删。
- **记得住。** 长期记忆写在一份你能打开、能改的 `MEMORY.md` 里。
- **像个人。** 人设和说话方式都是纯文本，想改就改。

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

- **自动记忆**：模型把该长期记住的事写成一行隐藏标记，客户端在**同一次回答**里摘走、写进
  `MEMORY.md` —— 不产生第二次请求，也不额外烧 token。你说过"记住…"，它就真的存下来了。
- **手动记忆**：聊天页 `⋯ → 记住这段`，把当前会话压成几条长期记忆。
- **会话话题**：每个会话首轮答完自动生成一次标题。
- **人物设定**：改名字、改语气、改规则，改的都是你自己那份 Markdown。

## 对话

- **长按任意一条消息** → 复制 / 引用 / 编辑重发 / 重新回答 / 撤回。
- 发图：选了图会真的随消息发出去，看得见的模型就能看见。
- 生图：在「默认模型 → 外围能力」里填好生图服务，直接说"画一张…"就行。

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

## 致谢

液态玻璃不是写一个 `blur()` 就能了事的，感谢上游：

| 依赖 | 用途 | 许可 |
| --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（`io.github.kyant0:backdrop` / `shapes`） | 折射 / 色散 / 模糊的底座 | Apache-2.0 |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | 模糊底座 | Apache-2.0 |

## 下载

当前是**快速测试版**（debug 签名）。

---

**此为快速版，本周更新。**
