# Lotus AI —— 压缩上下文（干活前先读这一屏）

> 全量证据/逐条验收在 `docs/CONTEXT.md`（284 行）。这份是压缩版，只留能改变决策的东西。
> 更新：2026-10-11（晚：记忆/提示词/联网已落地）

## 定位
安卓**纯聊天** App，不是 Agent。一次提问 = 一次模型调用。省 token、要快，**思考保留但可调**。
OpenAI 兼容协议，**提供方/地址/key 全由用户自己填，App 不预置任何厂商**。

## 液态玻璃（唯一视觉总纲）
所有玻璃 = **低模糊 + 边缘折射 + 色散 + 白内描边 + 外柔影**，全局纯白零彩色。
```
blur = 16dp × strength × 0.32      # 只要一层雾
lens = 20dp × strength × 1.5       # 玻璃感靠它；chromaticAberration = true
```
⛔ 模糊与折射**不许共用一个系数** —— 那会变成高斯毛玻璃（用户否过，参照图＝一张轻模糊+边角色散的面板）。
按键玻璃一律要：背景模糊 + 折射；长按＝原地放大 + 朝手指倾倒。
⚠️ 未做：两块玻璃**相融**（metaball，见 CONTEXT §7）。

## 玻璃铁律（2026-10-11 晚定，违反过一次）
**同一块玻璃里不许再出现第二块玻璃。** 现实里两块玻璃叠着看不出多出来一圈边。
- 嵌在大玻璃内的按键（输入条的 `+` / 模型键 / 发送键、设置组内的小胶囊、MD 文件行的图标）
  **静止时必须隐形**：不描边、不霜面、不投影。做法：
  - `LiquidGlassIconButton(nested = true)` —— 壳整块撤掉，只剩按下时的折射 + 手指光；
  - 静态图标（设置行 / MD 行）直接不垫玻璃片；
  - 需要一点底的地方用**淡墨底**（`Ink 0.05f`），不是玻璃。
- 它们仍然是**真按键**：按压缩放 / 墨色 / 邻位联动一个都不少。

## 自动记忆（模型自己记，2026-10-11 晚实测通过）
- 协议：模型把该长期记住的事写成一行 HTML 注释 `<!--memo: …-->`，客户端在**同一次回答**里
  剥掉（`Prompts.splitMemos`，能处理流式把标记切碎）并追加进 `MEMORY.md`。
- **零额外请求**：还是那一次调用，不是工具循环。
- 实测：说"remember my name is K and I hate long answers" → `MEMORY.md` 出现
  `## 自动记忆 · 2026-10-11` + 两条；用户看到的回复里没有标记残留。
- 开关：设置 → 记忆 → **自动记忆**（默认开）。关掉只停写入协议，手动「记住这段」照旧。
- 能力边界（`Prompts.AbilityLimits`）固定在人格提示词里注入：看不到手机文件、没开联网就说查不了、
  没有后台、**选图还没接进请求**。实测问"能看我手机相册吗" → 正确回答不能。

## 记忆 / 提示词 / 联网（2026-10-11 晚实测通过）
- **MD 记忆树**（`filesDir/md/`）：6 份文件 = 5 份前线（每轮注入）+ `CAPABILITIES.md`（给人看、不注入）； `IDENTITY / SOUL / AGENTS / USER / MEMORY`；
  `memory/{facts,decisions,lessons,projects,references,tasks,temporary}/` 是档案柜，**不注入**。
- **「⋯ → 记住这段」**：手动把当前会话压成几条长期记忆，**追加**进 `MEMORY.md`（已实测：写了
  `## 记忆快照 · 2026-10-11 17:11` 段，用户原文一个字节没动）。只在点的时候跑一次调用。
- **会话话题**：首轮答完自动生成一次（每会话只一次，`topicTried` 把关，`max_tokens=32`）。
  实测历史面板显示「北京今日天气」「Android开发者介绍」。
- **人设 / 记忆页只留开关 + 助手名**，内容全部跳 `MD 工作区` 里改 —— 消灭了"设置里改了、MD 没变"。
- **联网搜索**：设置开关（默认关）。开着时请求体只多一条 `tools:[{"type":"web_search"}]`，
  搜索由**上游**做，客户端仍只有一次调用。实测问北京天气 → 回「10 月 11 日晴间有霾，约 24°C」，
  该次 `prompt_tokens` 5.2K（搜到的东西会占输入，所以默认关）。
- **删除 = 归档**（软删除），设置 → 已归档能恢复 / 清空。
- 关于页「作者反馈」= `g15013815279@outlook.com`（mailto）。GitHub 号**没有**，等用户给。

## 🔒 锁定的基线 v3 —— 照上游逐行对齐的三节点玻璃泡（2026-10-16 晚定稿）
上游：`Kyant0/AndroidLiquidGlass`（Apache-2.0）· 分支 `kmp` ·
`.../catalog/components/LiquidBottomTabs.kt`。**照着它的三个节点还原**：
1. **① 外栏（可见）**：`drawBackdrop(页面){vibrancy();blur();lens()}` + 霜面 + 描边 + 图标。
2. **② 折射源（`alpha(0f)`+`layerBackdrop`，不可见）**：**把整条底栏的玻璃再画一遍**
   （`vibrancy()+blur()+表面色`），**图标画在这层玻璃之上**，链尾 `tint` 只染图标。
3. **③ 玻璃泡**：`drawBackdrop(combined(页面, ②)){ lens() }` —— **只折，不糊**。

**这三条是全部答案，别再自己发明：**
- 「背景只有折射没有模糊」⇒ 模糊属于 ②，不属于泡。**加到泡上就是糊图标**（用户否过）。
- 「② 只录图标」⇒ 泡里没模糊。**必须把玻璃一起录**。
- 「上下两面粘齿轮残片」⇒ 采样源纹理比泡小。**采样源必须是 `combined(页面, ②)`**（页面那半全屏）。
- 泡的 `lens` 上限定在按下 14dp/20dp，再大就把图标压进边缘折射带（= 残影）。

备份 `output/golden/LotusBottomBar.LOCKED-v3-2026-10-16.kt`；
基线图 `baseline-v3-rest.png` / `baseline-v3-pressed-home.png` / `baseline-v3-pressed-settings.png`。
v1 / v2 只作存档（已被上面几条否掉）。
**已废弃（2026-10-16 深夜，模拟器实测）**：把泡的按下折射从 `6+12p/9+18p`（原值 4+10p / 6+14p）调大后，
用户真机/模拟器都判为「折射又有残影圈」⇒ **已回退，源码逐字等于 golden v3（diff 为空）**。
残影截图存档 `~/.Trash/lotus-bottom-bar-b-2026-10-16/`。折射参数不要再动，除非先解决采样源。

**遗留**：首页是纯白底 ⇒ 泡里"没有东西可折"（折射只在设置页/文件页/聊天页这些有内容的页面看得见）。
用户若要求在**首页**也看到"折进来"，只能给底栏后面放内容 —— 这是产品决策，先问用户。

## 已完成
首页（立绘居中+问候语+「此刻，想说些什么？」+ 控件 `+ ⚡ | ⇄ ↗`；**5 个提示词已全删不许加回**）·
底栏（4 图标无文字，52dp 高 / 208dp 宽 / 底边距 96dp）· 输入条（**只在聊天页**，
键盘位移取 `max(底栏高, 键盘高)` 单调不回弹）· 聊天页（双边头像+玻璃气泡+typing）·
启动页（系统页 288dp 只露中间 192dp，图已塞满；Compose 长大终点按墨迹对齐首页立绘）·
桌面图标 · 设置大类（模型提供方/默认模型/联网搜索/人设与提示词/记忆/对话设置/会话与数据/已归档/常规设置/关于）·
提供方数据层（多提供方 + `filesDir/providers.json` + 测试连接 `/models`）· 流式客户端 `LlmApi.chatStream`(SSE)

## 待办
- ~~P0 端到端聊天没验证~~ **已验证**：手填提供方 → 测试连接 → 选模型 → 真聊天（流式 + usage 实测）
- P0 新玻璃配方**没逐面复验**（底栏/输入条/面板/设置卡片）
- P1 玻璃相融（metaball）· 消息操作（复制 / 重新生成 / 编辑重发）· 语音输入
- P1 **图片附件没接进请求**：`+` 选图只做了 UI，模型收不到（已在能力边界里如实说明）。
- P2 消息长按操作（复制 / 重新生成 / 编辑重发）
- **不加** MCP / skill（= 每轮往 system 里塞工具描述，正是 Codex 贵且慢的来源）。要加也只能默认关。

## 已实测（别重试错）
- `/v1/models` → 20 个模型；`gpt-6-luna` 自带思考（reasoning_tokens≈13–15）；`reasoning_effort` 接受；SSE 分片 `data:{"choices":[{"delta":{"content":…}}]}`，`[DONE]` 结束
- **key 有分组**：image 分组的 key 调 chat 会报 `No available channel for model … under group image` ⇒ 测试连接必须真跑一次 `/models`
- 🔒 key 不入库、不写代码（用户在会话里给过测试 key，仅本机手填）
- 系统启动页：画布固定 288dp、只显示中间 192dp 圆、背景只认纯色
- 键盘让位两坑：① 输入条必须在固定层、不能在首页 Column 里；② 位移只能写成一个 `max()`，不能两项相加
- 模拟器之前一直 `-no-window`（所以用户看不到窗口）；要带窗口起，且必须挂在持久 PTY 会话里

## 环境 / 构建
```bash
cd "/Users/l/Documents/ChatGPT/Lotus ai"          # 路径带空格
export JAVA_HOME=/Users/l/.workbuddy/binaries/jdk/temurin-21/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"; export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk   # 真机不许自己装
```
模拟器 `LinuxHub_API35_x86_64` 1080×2400；真机 `3B6585005LE00000`。截图用 `exec-out screencap`，坐标按**实际像素**。

## 协作口径
直接少废话；不确定就说不确定；**别自作主张**（尤其别把用户的 key/厂商写进界面）；
但**要有产品判断**，该主动加的主动加；反馈给**像素级实测**；实时汇报，别丢前面结论。

## 关键文件
`ui/glass/Glass.kt`(★玻璃基础设施) · `ui/LotusApp.kt`(外壳/路由/固定层/send) ·
`ui/settings/SettingsScreen.kt` · `data/LlmApi.kt` + `data/Provider.kt` ·
`ui/home/{HomeScreen,LotusComposer,ComposerMenus}.kt` · `ui/nav/LotusBottomBar.kt` ·
`ui/chat/ChatScreen.kt` · `ui/SplashScreen.kt` · `ui/model/ModelCatalog.kt`
