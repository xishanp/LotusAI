# Lotus AI —— 上下文快照（交接文档）

> 目的：把**所有需求 + 所有已实测事实 + 当前状态 + 已知问题**落到工程里。
> 这样即使对话被压缩、换人接手，也不会丢东西。
> 最后更新：2026-10-11

---

## 0. 一句话定位

**安卓手机端的纯聊天工具**，不是 Agent。

- 一次提问 = **一次模型调用**。不做工具循环、不做自主规划、不做多步反思。
- 省 token、要快；**思考能力保留**，但档位由用户选（默认「标准」）。
- 默认走 **OpenAI 兼容协议**，提供方/地址/key **全部由用户自己填**，App 不预置任何厂商。

---

## 1. 用户需求总清单（逐条 + 当前状态）

图例：`✅ 已完成并实测` · `🟡 已实现未验证` · `❌ 未做` · `⛔ 明确禁止`

### 1.1 视觉总纲：全局液态玻璃（**最高优先级，定稿**）

| # | 需求（用户原话/要点） | 状态 |
|---|---|---|
| V1 | 全局纯白背景、**零彩色** | ✅ |
| V2 | 所有玻璃统一：**低模糊 + 边缘折射 + 色散(散光) + 白描边 + 外柔影** | 🟡 配方已定，未逐面复验 |
| V3 | 「以后 UI 设计都用这种液态玻璃」 | 🟡 已写成全局常量 |
| V4 | 所有按键玻璃都要：**背景模糊 + 折射**；长按要有折射 | 🟡 |
| V5 | 长按行为：**原地放大 + 朝手指方向倾倒**（不是固定往下），按住期间一直有效 | ✅ v11 |
| V6 | 相邻玻璃联动：一枚按下，旁边的**朝它伸过去**（邻位相融） | ✅ 输入条 3 键 |
| V7 | 两块靠近的玻璃可以**相融**（Apple 那种融合感） | ❌ **未做**（见 §7 难点） |
| V8 | 不要残影/重影 | ✅ 根因＝底色画在 layerBackdrop 外面，已修 |
| V9 | 不要「灰色脏玻璃」、不要塑料描边 | ✅ 内描边 glassRim + 去掉卡面实色 |
| V10 | 玻璃要**通透**，不能糊成一团 | 🟡 见 §3 配方 |

**液态玻璃定稿配方**（`ui/glass/Glass.kt`）：

```
blur  = 16dp × strength × LiquidBlurScale(0.32)   ← 只要一层雾
lens  = 20dp × strength × LiquidLensScale(1.5)     ← 玻璃感靠它
        lens(lensPx*0.62, lensPx*1.15, chromaticAberration = true)  ← 色散
+ glassVeilBrush（上透下灰 0→2%→7%）
+ glassRim（内缩半线宽的白/灰渐变描边）
+ shadow（ambient 0x12 / spot 0x1C 黑）
```

⛔ **禁止**：让模糊和折射共用一个系数。上一版就是 `blur=16dp×strength`、`lens=20dp×strength`，
想加强折射就必然把模糊一起抬到 16dp 高斯 —— 用户否过（「你这模糊都模糊成高斯模糊了」）。
参照图：用户存下来的那张面板截图（轻模糊 + 边角色散彩边 + 白描边）。

### 1.2 首页

| # | 需求 | 状态 |
|---|---|---|
| H1 | 立绘 = `lotus_mascot.png`（双手托脸），**往中间挪**、别偏上 | ✅ |
| H2 | 问候语与输入框**靠近**，别离太远 | ✅ |
| H3 | 输入条 placeholder：「此刻，想说些什么？」 | ✅ |
| H4 | 输入条控件顺序：`+` · placeholder · `⚡` · 分隔线 · `⇄` · `↗` | ✅ |
| H5 | 5 个提示词（写文案/创意灵感/随便聊聊/帮我写作/学习问题） | ⛔ **已全删，不许加回来**（否过两轮：「根本做不好看」） |
| H6 | 左上角历史钮 = 玻璃圆钮 | ✅ |

### 1.3 底部导航栏

| # | 需求 | 状态 |
|---|---|---|
| B1 | 4 个图标、**无文字** | ✅ |
| B2 | **扁一点**、别太长 | ✅ BarHeight 52dp / BarWidth 208dp |
| B3 | **往上移**（不能贴最底） | ✅ BarMargin 96dp |
| B4 | 长按玻璃泡要有折射 + 色散 | ✅ lens(10dp·p, 14dp·p, chromatic) |
| B5 | 要稳定，滚回之前稳定版本即可，别花哨 | ✅ |
| B6 | 底栏要有**背景模糊 + 折射 + 散光** | 🟡 已改 blur 3.5dp + lens(12,22,chromatic) |

### 1.4 输入条 / 键盘

| # | 需求 | 状态 |
|---|---|---|
| C1 | 输入条 + 底栏都要**往上移一个身位** | ✅ |
| C2 | 点开输入框跟随键盘，**不能先下沉再弹回来** | ✅ 位移取 `max(底栏高, 键盘高)`，单调 |
| C3 | 输入条不能挡住上面的内容 | ✅ |
| C4 | **输入条只在聊天页出现**（「输入栏怎么跑到这里来了」） | ✅ |
| C5 | 输入条的**散光** | 🟡 走同一套 glassPanel，已带 chromatic |

### 1.5 聊天页

| # | 需求 | 状态 |
|---|---|---|
| M1 | 微信式：**两边各一个头像 + 玻璃气泡** | ✅ |
| M2 | AI 头像用立绘裁圆；用户头像用玻璃圆片 | ✅ |
| M3 | 「正在输入」三点动画 | ✅ |
| M4 | **真实流式回复** | 🟡 已接 `LlmApi.chatStream`，未端到端验证 |

### 1.6 启动页 / 图标

| # | 需求 | 状态 |
|---|---|---|
| S1 | 一进去就是**完整一块**（立绘+名字），不要先白屏再跳图 | ✅ v12 |
| S2 | 改的是**系统启动页**，不是自己加一个小的 | ✅ 平台限制见 §4 |
| S3 | 长大动画**终点和首页立绘对齐** | ✅ 实测差 1–2px |
| S4 | 桌面图标用参考图完整缩小、四周留白、呆毛别裁 | ✅ 已做（待复验） |

### 1.7 设置

| # | 需求 | 状态 |
|---|---|---|
| T1 | 设置 = **一个大类列表**：模型提供方/默认模型/联网搜索/人设与提示词/记忆/对话设置/会话与数据/已归档/常规设置/关于 | ✅ |
| T2 | 模型提供方**用户自己填**，App 不预置任何厂商/地址/key | ✅ |
| T3 | 支持**多个**提供方 | ✅ |
| T4 | 每个提供方显示**多少个模型** | ✅ |
| T5 | **测试连接** | ✅ 请求 `{BaseURL}/models` |
| T6 | 默认模型从提供方拉回来的**真实模型**里选 | ✅ |
| T7 | 思考档位**跟模型真实映射**（`reasoning_effort` / `thinking.budget_tokens`） | ✅ |
| T8 | **别乱改**已经定好的模型选择 / 思考档位 | ⛔ |
| T9 | 没接的功能不许摆假开关 | ✅ 统一标「未接入」 |

### 1.8 工程 / 协作

| # | 需求 | 状态 |
|---|---|---|
| E1 | 不要自己 `adb install` 到**真机**；APK 放桌面 | ⛔ |
| E2 | 要**实时汇报进度**，别闷头做 | ⛔ |
| E3 | 顺手把上下文压缩成文档 | ✅ 本文件 |

---

## 2. 技术栈 / 上游

| 用途 | 坐标 | 版本 | 许可 |
|---|---|---|---|
| 折射玻璃效果层 | `io.github.kyant0:backdrop` | 2.0.1 | Apache 2.0 |
| 形状/圆角 | `io.github.kyant0:shapes` | 1.2.1 | Apache 2.0 |
| 模糊底座（**已引未用**） | `dev.chrisbanes.haze:haze(-blur)(-blur-materials)` | 2.0.1 | Apache 2.0 |

- 上游仓库：`Kyant0/AndroidLiquidGlass`（**个人开发者，不是厂商**）。
- 库**只发效果层**，示例代码不随包发布 —— 所以下面这三份是**手抄进工程**的，可以随便改：
  `ui/glass/DragGestureInspector.kt`、`InteractiveHighlight.kt`、`DampedDragAnimation.kt`。
- Android 官方至今没有 App 级 liquid glass API（Android 16 只有系统级那套）。

构建工具链：Gradle 9.7.1 / AGP 9.4.1 / Kotlin 2.4.20 / Compose BOM 2026.09.00 /
compileSdk 37 / minSdk 28 / targetSdk 35。

---

## 3. 工程结构（关键文件）

```
app/src/main/java/ai/lotus/app/
  MainActivity.kt                 宿主 + 启动页编排（hero 对齐）
  data/
    LlmApi.kt                     OpenAI 兼容客户端：listModels(测试连接) + chatStream(SSE 流式)
    Provider.kt                   ProviderConfig + ProviderStore(filesDir/providers.json)
  ui/
    LotusApp.kt                   外壳：tab 路由 + 输入条/底栏固定层 + 键盘让位 + send()
    SplashScreen.kt               Compose 层启动页长大动画（终点对齐首页立绘）
    theme/LotusTheme.kt           LotusPalette：纯白 + 两级灰
    glass/Glass.kt                ★ 液态玻璃基础设施（GlassBackground / glassPanel / LiquidGlassIconButton / glassRim / glassVeilBrush）
    glass/DampedDragAnimation.kt  底栏玻璃泡的拖拽弹簧
    glass/DragGestureInspector.kt 手抄上游
    glass/InteractiveHighlight.kt 手抄上游
    home/HomeScreen.kt            首页（立绘 + 问候语；**没有任何引导语 chip**）
    home/LotusComposer.kt         输入条（+ / placeholder / ⚡ / ⇄ / ↗）
    home/ComposerMenus.kt         输入条上方的玻璃菜单 + 附件缩略片
    nav/LotusBottomBar.kt         底部导航（4 图标无文字，玻璃泡拖拽）
    chat/ChatScreen.kt            聊天页（双边头像 + 玻璃气泡 + typing）
    settings/SettingsScreen.kt    ★ 设置：大类列表 + 子页 + 多提供方 + 测试连接
    icons/LotusGlyphs.kt          自绘图标（含 Chevron）
    model/ModelCatalog.kt         内置模型能力表 + thinkingFor(modelId)
app/src/main/res/drawable-nodpi/
  lotus_mascot.png                首页/聊天页立绘
  lotus_splash_icon.png           系统启动页图标（1152px，塞满 288dp 画布）
docs/
  CONTEXT.md                      ← 本文件
  UI-SPEC.md                      视觉规格（逐条像素级验收记录）
  PLAN.md                         产品计划
```

---

## 4. 已实测事实（别再重新试错）

### 4.1 模型 API（mzhcloud，OpenAI 兼容）

- `GET {base}/v1/models` → **20 个模型**，含：
  `gpt-6-luna`、`gpt-6-sol`、`gpt-6-astra`、`gpt-6.1-sol`、`gpt-5.6-luna/sol/terra`、
  `gpt-5.5`、`gpt-5.5-pro`、`gpt-5.4`、`gpt-5.4-mini/nano`、`gpt-5.3-codex`、`gpt-5.2`、
  `gpt-5.2-pro`、`gpt-4.1`、`gpt-4.1-mini`、`gpt-4o`、`gpt-4o-mini`、`codex-auto-review`
- `POST /v1/chat/completions`（`gpt-6-luna`）→ 正常返回，`usage.reasoning_tokens ≈ 13–15`
  ⇒ **它自带思考**。
- `reasoning_effort: "high"` → **接受**，不报错。
- `stream: true` → **SSE 正常**，分片形如 `data: {"choices":[{"delta":{"content":"1"}}]}`，
  以 `data: [DONE]` 结束。
- ⚠️ 旧的另一把 key 属于 **image 分组**，调 chat 会报
  `No available channel for model … under group image`。**key 有分组，测试连接必须真的跑一次 `/models`。**
- 🔒 **key 不入库、不写进代码**。用户在会话里给过测试 key，只用于本机手填。

### 4.2 系统启动页的平台硬限制（实测）

- 系统启动页画布固定 **288dp**，**只显示中间 192dp 的圆**，其余全裁。
- `windowSplashScreenBackground` **只认纯色**，不能给图。
- ⇒ 想让立绘看起来"铺满"，只能把 `lotus_splash_icon.png` 做到 1152px、把画布塞满。
- Compose 层的长大动画终点必须**按墨迹**（不是按画布）对齐首页立绘。

### 4.3 模拟器

- AVD：`LinuxHub_API35_x86_64`，1080×2400 / density 420。
- ⚠️ 之前一直是用 **`-no-window`（headless）** 起的，所以**用户桌面上看不到窗口**。
  改法：去掉 `-no-window` 起进程（`-gpu swiftshader` 保持不变），窗口会出现在桌面上。
  ⚠️ 必须挂在一个**持久 PTY 会话**里跑，`nohup ... &` 会被回收掉。
- 真机：`3B6585005LE00000`（PLE110, Android 16, 1080×2362）。**不要自己往真机装。**

### 4.4 键盘让位（踩过的坑）

- 底栏和输入条必须是**同一个 Box 下的兄弟节点**，输入条不能在首页那个纵向 Column 里
  （Column 会被键盘挤爆，输入条被顶出屏幕，表现＝「移动的是导航栏，输入框不动」）。
- 位移量必须写成 `max(底栏那一条的高度, 键盘高度)` **一个表达式**。两个机制相加＝先变小再变大＝
  「往下沉一点再弹回来」。
- 底栏隐藏用 `imeCover`（0..1 连续量）淡出，**不能用 Boolean 开关**（跳变会把输入条顶上去）。

---

## 5. 当前完成度

- ✅ 首页 / 底栏 / 输入条 / 输入条菜单 / 聊天页版式 / 启动页 / 桌面图标 / 设置大类列表
- ✅ 液态玻璃基础设施（两层 backdrop、折射、色散、描边、霜面、按压动画、邻位联动）
- ✅ 提供方数据层（多提供方 + 落盘 + `listModels` 测试连接）
- ✅ 流式聊天客户端 `LlmApi.chatStream`
- 🟡 **没端到端验证过**：在模拟器里手填提供方 → 测试连接 → 选模型 → 真聊天

---

## 6. 已知问题 / 待办（按优先级）

1. **P0 · 端到端聊天没跑通验证**：模拟器里手填提供方那一串操作还没走完。
2. **P0 · 全局液态玻璃只改了配方，没逐面复验**：底栏 / 输入条 / 面板 / 设置卡片要一个个看。
3. **P1 · 玻璃相融（V7）没做** —— 见 §7。
4. **P1 · 记忆框架 / 人设分层 MD / 会话持久化**：目前只有设置页里的说明和占位，代码没落地。
5. **P1 · 启动页 / 图标**：改完之后没再看真机。
6. **P2 · 语音输入 / 会话历史列表 / 消息操作（复制·重新生成·编辑重发）**：全没做。
7. **P2 · 联网搜索**：必须走服务端，纯客户端做不了。

---

## 7. 难点记录：玻璃相融（V7）

用户要的是「两块靠近的玻璃，一方长按，可能两块相融」——Apple 的 metaball / 液体融合。

**为什么难**：现在每块玻璃是**各自独立**的 `drawBackdrop` + 各自的 shape。要做相融，需要
（任一）：
- a) 把相邻两块画进**同一张离屏纹理**，用 metaball 距离场生成**并集轮廓**，再对这条合并轮廓
  做一次折射采样 —— 也就是要绕开"各自 drawBackdrop"的结构，改成"一块合并玻璃 + 内部挖空"；
- b) 近似做法：两块互相靠近时，往中间补一条**渐变过渡带**（视觉上"连起来"），成本低但只能骗眼睛。

**建议**：先做 (b) 的近似，等其它 P0/P1 清完再上 (a)。**不要**在没验证前就动 `Glass.kt` 的
backdrop 结构 —— 那层是踩了三次坑才稳的（见 §4.4 和 Glass.kt 顶部注释）。

---

## 8. 构建 / 测试流程

```bash
cd "/Users/l/Documents/ChatGPT/Lotus ai"     # ⚠️ 路径带空格，必须引号
export JAVA_HOME=/Users/l/.workbuddy/binaries/jdk/temurin-21/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleDebug

# 装模拟器（可以）
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
# 真机不要自己装，APK 复制到 ~/Desktop/
```

- 截图验收：`adb -s emulator-5554 exec-out screencap -p > /tmp/x.png`
- 坐标要用**实际像素**（截图 1080×2400；不要按缩放后的预览图估）。

---

## 9. 沟通口径（用户偏好）

- 直接、少废话；**不确定就说不确定**，别空口保证。
- **不要自作主张**加用户没要求的东西（尤其别把用户的 key / 厂商名写进界面）。
- 反过来，**要自己有产品判断**：该有的功能主动提、主动做（用户原话：「你不应该自己添加吗」）。
- 反馈要**像素级实测数据**，不要"应该没问题"。
- 别闷头做，要实时汇报；**别中途就把前面的结论忘了**。

---

## 10. 最新进展：记忆 / 提示词 / 联网（2026-10-11 晚，已装机实测）

### 10.1 提示词与记忆的文件结构（唯一真相来源）

```
filesDir/md/
├── IDENTITY.md   身份          ← 每轮注入
├── SOUL.md       说话方式      ← 每轮注入
├── AGENTS.md     规则与边界    ← 每轮注入
├── USER.md       用户画像      ← 每轮注入
├── MEMORY.md     长期记忆      ← 每轮注入
└── memory/
    ├── facts/ decisions/ lessons/ projects/
    ├── references/ tasks/ temporary/        ← 档案柜，**不注入**
```

- `Prompts.ensureDefaults()` 在 `MainActivity.onCreate` 里同步调用（`setContent` 之前），
  只写**缺失**的文件；旧版默认内容按**逐字匹配**升级，用户改过的文件一个字节都不动。
- `Prompts.systemBlocks()` 只拼真正非空的部分 —— 空文件不发，不给每轮请求加固定开销。
- **不按日期切文件**（用户否过"今天/明天"那套）。记忆是"一条条事实"，不是流水账。

### 10.2 「⋯ → 记住这段」（手动记忆）

- 为什么要有：压缩只在超阈值时才发生，没压到的对话就永远留不下东西（用户原话）。
- 行为：把**当前会话全文**交给模型 → 提取 ≤8 条长期事实 → **追加**写进 `MEMORY.md`，
  带 `## 记忆快照 · yyyy-MM-dd HH:mm` 小标题；**用户自己写的内容不被覆盖**。
- 代价：**只在点的时候跑一次调用**，`reasoningEffort=null`、`max_tokens=400`。
- 状态：`rememberBusy` / `rememberHint`，面板行尾显示「已写进 MEMORY.md（N 条）」。
- 实测证据（模拟器 `files/md/MEMORY.md`）：
  ```
  ## 记忆快照 · 2026-10-11 17:11
  - 用户名是 K，从事 Android 应用开发。
  ```

### 10.3 会话话题（用户说的"会话总结"）

- 首轮答完自动跑**一次**：`summarizeTopic(id)`，`Conversation.topicTried` 把关，失败不重试。
- 请求：system 要求"12 字以内话题名"、`max_tokens=32`、`reasoningEffort=null`。
- `Conversation.displayTitle` = 用户命名 > `topic` > 首条用户消息。
- 实测 `files/conversations.json`：`topic='北京今日天气'` / `topic='Android开发者介绍'`，
  历史面板显示同名条目。

### 10.4 联网搜索（走上游，不变成 Agent）

- ⚠️ 试过两条路，**只有第二条有用**：
  - `web_search_options:{search_context_size:"low"}` → 被上游忽略，模型仍答"查不了"。
  - `tools:[{"type":"web_search"}]` → **真的搜**：问北京天气回了「10 月 11 日晴间有霾，约 24°C」，
    该次 `prompt_tokens=4784`（搜索结果注入了输入）。
- ⛔ **不要**改用 `{"type":"function", function:{name:"web_search",...}}` —— 那会让模型回
  `tool_calls` 交给客户端执行，变成"客户端搜索 + 二次请求"的 Agent 循环，正是要避开的。
- 客户端只加一个字段，**不多发任何请求**；开关在 `设置 → 联网搜索`，**默认关**
  （`AppPrefs.webSearchEnabled`），因为开了之后真搜的那一次输入会涨到几千 token。

### 10.5 人设 / 记忆页与 MD 的对接

- 症状（用户报）：设置里改了人设，MD 工作区没同步 —— 根因是**两处都在编辑同一批文件**。
- 修法：设置页**不再放内容编辑器**，只留「助手名字 + 是否注入」+ 一行
  「身份 / 说话方式 / 规则 · 在 MD 里改」→ `onOpenMd` 直接切到 `LotusTab.Files`。

### 10.6 删除 = 归档（软删除）

- `deleteConversation()` 只打 `archived=true`；`restoreArchived()` / `purgeArchived()`。
- 历史列表与设置里的会话计数都排除归档；设置 → 已归档是真的列表（点开 = 恢复并打开）。

### 10.7 踩过的坑（新）

- **Kotlin 块注释可以嵌套**：注释里写 `` `md/*.md` `` 会让 `/*` 开始一个**嵌套注释**，
  于是后面整个文件被注释掉，报错却出现在文件末尾（`Syntax error: Unclosed comment`）。
  写路径时用 `md/` 目录下的 .md 这种写法，别在注释里留 `/*`。
- `input text` 经 ADB **不能输中文**（`NullPointerException`），模拟器里只能用英文测输入。
- 模拟器上**别用 `input keyevent 4` 代替"回上一页"**：某些界面没拦返回键，直接退出 App，
  后续点击就落到别的 App（曾误开 Chrome 欢迎页）上。切页请用 `am start -n ai.lotus.app/.MainActivity`。

---

## 11. 嵌套玻璃 / 自动记忆 / 能力说明书（2026-10-11 晚）

### 11.1 玻璃分层铁律（用户 2026-10-11 定，已被违反过一次）

> 用户原话：「既然都在一个玻璃下，就不应该再分出一个玻璃。因为在现实中，两块玻璃重叠是
> 看不到的，所以在有些玻璃衬托下，就不能出现第二个玻璃。比如发送键、模型选择键，以及设置里
> 绝大多数的按键，还有跟这文件区那里也有很多……不要描边了，直接隐藏起来。
> 但是它们是真实存在的，长按的时候会有真实反馈。」

改法（三层，别混）：

| 场景 | 做法 |
| --- | --- |
| 嵌在大玻璃里的**可按键**（输入条的 `+` / 模型键 / 发送键） | `LiquidGlassIconButton(nested = true)` —— 玻璃壳整块撤掉，只留按下时的折射/色散/手指光 |
| 行内的**静态图标**（设置行、MD 文件行） | **不垫玻璃片**，裸图标直接画在组玻璃上 |
| 需要一点底的小胶囊（设置里的新建/重置/获取、模型行减号） | **淡墨底** `LotusPalette.Ink.copy(alpha = 0.05f)`，按下加深 —— 这不是玻璃，不会有边 |

⛔ 别再给这些地方写 `glassPanel(...)`：那正是"玻璃叠玻璃"的脏边来源。
✅ 按下反馈一个都不能少：按压缩放、墨色、邻位联动、长按折射。

### 11.2 自动记忆 = 提示词约定的"软工具调用"（**不是** function calling）

- 协议（`Prompts.MemoryProtocol`，随记忆开关注入）要求模型把该长期记的事写成一行
  `<!--memo: 内容-->`，放在回复最后。
- 客户端在同一次流式回答里：
  1. `Prompts.splitMemos(raw)` 把标记摘掉（**能处理 delta 把标记切碎**：已闭合的用正则删，
     还没闭合的从 `<!--` 起整段藏住，用户不会看到半截标记闪过去）；
  2. 正文照常显示，条目收进 `memoEntries`；
  3. 流结束后 `Prompts.appendAutoMemory()` 追加进 `MEMORY.md`（同一天合并到一个
     `## 自动记忆 · yyyy-MM-dd` 小标题下；**用户手写内容永远在前、不被覆盖**）。
- 为什么不用 function calling / MCP：那需要"请求→tool_call→客户端执行→再请求"的多轮循环，
  每次请求还要带上工具清单 —— 正是用户抱怨 Codex "贵且慢"的来源。这里**仍然只有一次调用**。
- 实测（模拟器）：发 "remember my name is K and I hate long answers" →
  `MEMORY.md` 里出现 `## 自动记忆 · 2026-10-11` / `- 用户叫 K。用户不喜欢长回答。`，
  而**用户看到的那条回复里没有标记残留**。
- 开关：设置 → 记忆 → 「自动记忆」（`AppPrefs.autoMemoryEnabled`，默认开）。
  关掉 = 不注入协议；手动「记住这段」照旧可用。

### 11.3 能力边界 + 能力说明书

- 新增 `md/CAPABILITIES.md`（**不注入**，只在 MD 工作区里给人看）：能力清单 / 记忆怎么运作 /
  做不到什么。维护规则：**只写真的做了的**。
- 新增 `Prompts.AbilityLimits`（**注入**，挂在人格提示词后面）：
  看不到手机文件、联网只在开着时才有、没有后台任务、不要假装调工具、**选图还没接进请求**。
- 实测：问 "can you look at the photos on my phone" → 回答
  "I can look at photos you upload here, but I can't access your phone's photo library directly."
  （没乱承诺）。

### 11.4 待修（本轮发现，尚未动手）

- **图片附件没接进请求**：`LotusApp` 里的 `attachment` 只有选图 UI，`requestTurns()` 里
  根本没用它。要么接上（OpenAI 兼容的 `image_url` + base64），要么把 `+` 收起来 ——
  现在这样点选图是**没有效果**的，已在能力边界里如实说明。

## 🔒 锁定的底栏基线 —— "真玻璃"（2026-10-16 晚）

用户原话：「是真玻璃」「保留这一版 并标为重点」「把其他可能会造成污染的不重要的版本删除」。

**锁定的实现**（`ui/nav/LotusBottomBar.kt`，顶部有同样的锁定注释）：
玻璃泡 = **一层** `drawBackdrop(backdrop = rememberCombinedBackdrop(页面背景, 图标层))`，
effects **只有** `lens((4dp + 10dp·p), (6dp + 14dp·p), chromaticAberration = true)`；静止基线 4dp/6dp（2026-10-16 二次修订：原来 p=0 时折射为 0，被用户判成「塑料板」）；
无 blur、无 vibrancy、无霜面；静止态 6% 墨，按下褪到 0。

**逐字备份**：`output/golden/LotusBottomBar.LOCKED-2026-10-16.kt`
（回滚：`cp output/golden/LotusBottomBar.LOCKED-2026-10-16.kt app/src/main/java/ai/lotus/app/ui/nav/LotusBottomBar.kt`）
**基线外观**：`output/golden/baseline-pressed-真玻璃.png`（设置页按住第三个 tab 的实拍）。

**当晚逐条试错、已被用户否掉、不许重试**：
1. 采样里拼 `barBackdrop`（外栏导出底座）→「这一版的玻璃又没了」——底座是白的，折白＝白。
2. 泡裁进外栏胶囊 / 最上层补一道边光 →「全是 bug，而且还给它故意描边了」。
3. 给泡加 blur（哪怕"再糊一遍"）→ 二次模糊，长按成毛玻璃。
4. 把外栏底座改到泡上面画（调绘制顺序）→「这一版都有 bug」。

**未解**：用户说"底座没有真正显示进去"——目前为止 4 条路子全错，没有正确解法在案；再动手前必须先问。

---

## 🔒 v2：三层玻璃泡 —— 底栏玻璃的收官版（2026-10-16 深夜）

**v1（单层、只有折射）已判为 bug 版并出局**：`output/golden/LotusBottomBar.LOCKED-2026-10-16.kt`
与 `baseline-pressed-真玻璃.png` 已移入废纸篓 `~/.Trash/lotus-bottom-bar-v1-old-golden/`。
出局原因（用户原话）：「你上一版本把底栏的图标给覆盖掉了，导致看起来像是在一块玻璃后面」。

### 症状 → 根因（两条，都是**采样源**的问题，不是参数问题）

1. 「背景只有折射没有模糊」／「把图标覆盖掉了」
   - 把 `blur(2.6dp)` 加在**同一层采样**上 ⇒ 采样源是 `底色+页面+图标` 的合集，
     一糊**连选中的图标一起糊**。实测对照图 `output/verify/dock_ab.png` 上半张：
     泡里的气泡图标明显发虚，旁边没被泡盖住的文件夹/齿轮是清晰的。
2. 「长按背景不折射，只有里面的东西折射，外面的东西不折射」
   - 泡的采样是 `combined(底色, 页面, 图标)`，**唯独不含底栏自己**。
     外栏画在泡**旁边另一棵树**上（`GlassBackground` 的 content 槽），泡根本看不见它。

### 解法：把"糊谁"和"折谁"拆成三层（同一块玻璃、同一几何、同一 `pillLayerBlock`）

| 层 | 采样源 | effects | 作用 |
| --- | --- | --- | --- |
| 底层 | `backdrop`（底色+页面） | `blur(2.6dp)` + `lens((6+14p)dp,(9+20p)dp, 色散)` + 静止 6% 墨 | 静态模糊 |
| 中层 | `barShellBackdrop`（外栏**面+边**，**不含图标**） | 只 `lens` | 长按把"底座"折进泡里 |
| 顶层 | `tabsBackdrop`（图标） | 只 `lens` | 图标永远清晰 |

- `barShellBackdrop` 的录法：把外栏的 `veil + glassRim` 按同样几何再画一遍，
  `.alpha(0f)` 写在 `.layerBackdrop()` **之前**（顺序反了录进去就是全透明 —— 和 Glass.kt
  里 `background` 必须写在 `layerBackdrop` **之内**是同一条机制）。
- ⚠️ 三层必须是**兄弟节点**，各自挂 `pillLayerBlock`。套成父子会让 `layerBlock` 的
  graphicsLayer 作用到子节点、`-offset` 补偿只抵消自己那份 ⇒ 图标被按着泡心整体放大位移
  （历史 bug：「加粗的图标乱跳 / 底栏下面凭空多出一枚图标」）。
- 实测（模拟器 1080×2400，按住第三个 tab）：底座被折进泡里、页面文字在泡内糊成柔雾、
  三枚图标保持清晰。基线图 `output/golden/baseline-v2-{rest,pressed}.png`。
- **遗留**：用户口径「背景是有模糊了 但是折射还是不够」⇒ 下一步只动 `lens` 的**按下**项，
  静止 6/9dp 别动（那档是用户认可的基线折射）。

---

## 🔒 v3：照上游逐行对齐 —— 底栏玻璃的真正解法（2026-10-16 深夜）

**已拉上游源码对过**：`Kyant0/AndroidLiquidGlass`（Apache-2.0），默认分支 `kmp`，
`app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt`。

### 上游的真相（三个节点）

| 节点 | 上游写法 | 作用 |
| --- | --- | --- |
| ① 外栏（可见） | `drawBackdrop(页面){vibrancy();blur(8dp);lens(24,24)}` + 表面色 + 图标 | 底栏本身 |
| ② 折射源（`alpha(0)`+`layerBackdrop`） | **整条底栏的玻璃再画一遍**（`vibrancy();blur();lens(24dp·p)`）+ 表面色，**图标画在玻璃之上**，链尾 `ColorFilter.tint` 只染图标 | 录进 `tabsBackdrop` 的纹理 = **糊过的底 + 清晰的图标** |
| ③ 玻璃泡 | `drawBackdrop(combined(页面, ②)){lens(10dp·p,14dp·p,色散)}` + highlight/shadow/innerShadow·p + 表面 10% 黑·(1-p) | 只折，不糊 |

### 我们错在哪（本轮全部答案）

1. **我们的 ② 只录了图标**（没画那层玻璃）⇒ 泡里**没有模糊**、只剩图标。
   用户口径「背景只有折射没有模糊」就是这么来的。
2. 为了补模糊，把 `blur` 加到 **③ 泡自己**那层 ⇒ 一糊**连图标一起糊**
   （用户：「把底栏的图标给覆盖掉了，导致看起来像是在一块玻璃后面」）。
   实测对照图 `output/verify/dock_ab.png` 上半张。
3. 中途还自创过"外栏外壳层"（把外栏的面 veil + 边 rim 录进泡）⇒ 泡里糊上一层灰罩，
   用户：「被某行代码的灰色给盖住了，字体包括底栏那些全部显示不出来」。**已删**。
4. 「泡的上下两面粘齿轮残片」= 采样源**纹理比泡小**。当时 ③ 只采样 ②（或图标层），
   lens 往边缘外采样直接采到纹理边界残留。上游采样 `combined(页面, ②)`，
   **页面那半是全屏的**，永远有像素可采 ⇒ 残片消失。实拍对照：
   `output/verify/v3_press_pill_zoom.png`（坏）对比 `output/verify/v8_set_press.png`（好）。

### 落地结果（模拟器实拍，`output/verify/v8_*.png`）

- 长按 → 泡放大、边缘出现色散彩边、**上下两面不再有残片**；图标全程清晰。
- 拖动换 tab 正常（`v8_drag_dock.png`）。
- ⚠️ 首页是纯白底 ⇒ 泡里"没有东西可折"，这一点是物理事实，不是 bug；
  设置页/文件页/聊天页有内容时，泡里折的就是那层"糊过的底"。

### ✅ 用户确认为默认基线（2026-10-16 深夜）

用户口径：「对，就这一版，把它作为默认的（基线）」。

- 当前 `ui/nav/LotusBottomBar.kt` **逐字等于** `output/golden/LotusBottomBar.LOCKED-v3-2026-10-16.kt`。
- 任何新改动必须先复制一份 golden 备份，改坏了直接 `cp` 回来。
- ⛔ 泡的 `lens` 参数（静止 4dp/6dp、按下 4+10p / 6+14p）**不许再动**：
  实测调大到 6+12p/9+18p 会立刻出现「残影圈」，用户已否。

## 🧪 底栏折射源实验 E1 / E2（2026-10-16 深夜，均已回退）

用户口径：「长按玻璃里最右边的底栏你以为显示出来了，但是上面和下面没有 —— 那头像有没有真正显示出来」。

### v3 的实拍定量结论（`output/verify/s1_press_settings_zoom.png`）

在设置页长按设置格放大后：
- 球内**能看到**页面文字的模糊残余（"对话设置""头像 · 显示项"的字形被折进来）；
- 球内**看不到**底座的上下边缘 —— 根因确切：折射源 ② 的底栏副本 `height = InnerHeight(50dp)`
  **比真实底栏 `BarHeight(60dp)` 矮 10dp**，而且 ② **没有画 rim（边）**。
  球按下后高约 72dp，上下各超出 ② 副本 10dp 以上 ⇒ 那两块采到的是纯白页面；
  底座本身又是浅色玻璃，与白底没有对比 ⇒ 上下两面"什么也看不出"。
- 所以严格说：页面内容只是"糊着透进来一点"，**没有真正清晰显示**。

### E1（有效，未采纳）

② 改成与 ① 同高（`BarHeight`）并补 `.glassRim(shape = barShape, alpha = 0.85f)`。
实测 `output/verify/E1_press_chat_zoom.png`：球内出现一条**完整的底座下边弧线**（从球左贯到球右），
页面文字依然可见。底座上下边第一次读得出来。→ 这是目前唯一走对的方向。

### E2（失败，已删）

在 ② 上加 `graphicsLayer { scaleX = 0.92f; scaleY = 0.92f }`（试图让副本"缩小一圈"）。
实测 `output/verify/E2_press_chat_zoom.png`：**球变成一块不透明白圆**，折射/模糊/底座全丢。
原因：缩放挂在 `layerBackdrop` 内层，破坏了录制层的内容/裁剪。**这条路不要再走。**

### E3a（未编译即被否）

把 ② 的 `glassVeilBrush(scale = 0.55f)` 减淡 —— 未实测。

### 🔬 E5 / E4a（2026-10-16 深夜）

**用户口径**：「静止状态下右边那一块都不透，再查查源码啥原因」。

**源码层原因（确定）**：③ 泡的采样源是 `combined(页面, ②)`，而 ② 是一层**不透明**的玻璃
（`vibrancy + blur + 霜面` 把页面整片盖住）⇒ 泡内根本采不到真背景；再叠 ③ 自己那层
静止 6% 墨，泡永远比周围底栏暗一档 ⇒ 观感就是「不透 / 一块实心板」。

| 实验 | 改法 | 结果 |
| --- | --- | --- |
| **E4a** | ② 链上加 `graphicsLayer { alpha = 0.62f }`（让副本半透明，页面透出 38%） | 泡没有更透（页面本来就是白），**图标一起被染淡** ⇒ 副作用，已撤 `output/verify/G0_ball_x3.png` |
| **E5** | ③ 静止墨 `0.06 → 0.025` | 泡与周围底栏亮度拉平，泡内开始读得出页面文字 `output/verify/H0_rest_settings_ball_x3.png` |

**当前源码状态 = E1 + E5**（候选备份 `output/golden/LotusBottomBar.CAND-E1E5-2026-10-16.kt`）：
- E1：② 与 ① 同高（`BarHeight`）+ `.glassRim(0.85f)`
- E5：③ 静止墨 0.025
- 回退到基线：`cp output/golden/LotusBottomBar.LOCKED-v3-2026-10-16.kt app/src/main/java/ai/lotus/app/ui/nav/LotusBottomBar.kt`

### 🔬 E6 / E8（2026-10-16 深夜）—— 「把上下两边做成跟左右两边一样」

**用户口径**：「这次怎么有点卡卡的感觉」「左右两边都能有折射，上下照左右那一下做不就可以了」。

| 实验 | 改法 | 理由 |
| --- | --- | --- |
| **E6** | ② 的 `glassRim(..., spectrum = 0f)` | `glassRim` 默认多画一圈 `sweepGradient` 光谱描边；② 是每帧录制的折射源，那圈既会被 lens 折掉、又白烧一份渐变 ⇒ 卡顿来源。关掉光谱只留纯色边 |
| **E8** | ② 高度 `60dp → InnerHeight(50dp)`，**保留 rim** | 边线落点决定可见度：② =60dp 时边线落在离球顶/底 6.25dp（8.6%），正好压在 lens 折射最强的一圈里被扯没；② =50dp 时落在 11.25dp（15%），落在球内可读的位置。同时少录 10dp 少一点开销 |

**结论**：上下两缘现在都能折出和左右端同类的边缘折射（色散彩边），实拍
`output/verify/J1_press_settings_ball_x3.png`（长按）/ `J0_rest_settings_ball_x3.png`（静止）。
纯白页面上"球里读出底座轮廓"依然很淡 —— 这是白底没有对比度的物理限制，不是算法。

当前源码 = v3 + E5 + E6 + E8，候选备份 `output/golden/LotusBottomBar.CAND-E1E5E6E8-2026-10-16.kt`。

## ★ 2026-10-17：底栏照 NASclaw 抄 + 记忆改成"合并"

### 底栏（`ui/nav/LotusBottomBar.kt`，备份 `output/golden/LotusBottomBar.NASCLAW-2026-10-17.kt`）

用户口径：「你可以在我机器里找一个叫 NASclaw 的底栏，那个底栏做的就非常完美 你直接抄外观，
抄得一模一样就行」。源文件：`/Users/l/WorkBuddy/nasclaw/upstream/openclaw/apps/android/app/src/main/java/ai/openclaw/app/ui/NasBottomBar.kt`

| 项 | NASclaw（已抄入） | 之前 Lotus |
| --- | --- | --- |
| 栏体模糊 | `blur(8dp)` | 2.56dp |
| 栏体折射 | `lens(24dp, 24dp)` | 9.3 / 17.25dp |
| 表面色 | 画布色 ×0.4 | 自造渐变霜面 |
| 描边 | 无（它的注释：删掉全部手写黑纱/描边/rim） | 有 rim |
| 泡折射 | `10dp·p / 14dp·p` | 4+10p / 6+14p |
| 栏高/内层 | 64dp / 56dp | 60dp / 50dp |

实拍：`output/verify/R1_settings_zoom.png`（静止）/ `R2_press_zoom.png`（长按，球里上下都读得出边 + 色散）。

### 记忆写入（`data/Prompts.kt`，备份 `output/golden/Prompts.UPSERT-2026-10-17.kt`）

用户口径：「不是靠追加，而是靠直接改里面的内容」「记忆的总是追加，没有整体感，后面会越堆越多」。

- 新增 `upsertAiEntries()`：AI 区**固定**只有 `## 记忆 · 自动维护` 一段；条目格式 `- 主题：内容`；
  同主题命中 → **整条替换**；新主题才追加。用户手写区（第一个 AI 标题之前）一个字节不动。
- `applyWrites()` / `appendAutoMemory()` 都改走 upsert。
- `normalizeEntries()`：把模型写字面的 `\n` 还原成真换行，按行拆成多条，去掉 `-`/`#` 前缀。
- 提示词同步：`ToolIndex` / `AbilityLimits` / `MemoryProtocol` 都改成 `主题：内容` + "同主题自动替换"。
