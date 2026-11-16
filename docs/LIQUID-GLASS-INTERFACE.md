# 液态玻璃接口契约（零模糊版）

> 口径修正：液态玻璃应当是**通透的**，不做背景高斯模糊；玻璃感来自**边缘折射、色散、镜面高光和轻微阴影**。
>
> 本文是给新应用接入用的接口说明。Lotus AI 当前 `Glass.kt` 仍保留旧的轻模糊配方，不能原样当成“零模糊版本”复用。

## 1. 依赖

Lotus AI 已验证的折射层依赖：

```kotlin
implementation("io.github.kyant0:backdrop:2.0.1")
implementation("io.github.kyant0:shapes:1.2.1")
```

`backdrop` 负责 `drawBackdrop`、`lens`、`chromaticAberration`、`Highlight` 和 `Shadow`。Compose 基础依赖按新应用自身版本管理；Lotus AI 实测组合是 Kotlin `2.4.20`、Compose BOM `2026.09.00`，这不是强制版本。

## 2. 宿主接口：必须先录制背景

折射层采样的不是“屏幕截图”，而是 Compose 的 `LayerBackdrop`。页面内容和玻璃组件必须分层：

```text
背景内容 -> layerBackdrop(backdrop)
玻璃组件 -> drawBackdrop(backdrop) + lens()
```

硬约束：

1. 背景内容必须画在 `layerBackdrop` 的 modifier 内部。
2. 玻璃组件必须是录制层的兄弟节点，不能采样自己。
3. 嵌在父玻璃里的按钮不要再次画独立玻璃壳，否则会出现第二圈边。
4. 零模糊版本的 `effects` 只保留 `lens`，不要调用 `blur`；也不要加实色 `tint` 或霜面。

Lotus AI 现有的宿主接口：

```kotlin
@Composable
fun GlassBackground(
  modifier: Modifier = Modifier,
  baseColor: Color = LotusPalette.Canvas,
  accent: Color = LotusPalette.Ink,
  style: LotusBackgroundStyle = LotusBackgroundStyle.Apple,
  page: @Composable BoxScope.() -> Unit = {},
  content: @Composable BoxScope.() -> Unit,
)
```

新应用最小化使用时，可以把 `baseColor`、`accent`、`LotusPalette` 替换成新应用自己的颜色 token。`page` 是会被折射的底层内容，`content` 是浮在页面上的玻璃层。

## 3. 零模糊玻璃接口

建议新增下面这个独立入口，不要直接调用 Lotus AI 的旧 `glassPanel()`：

```kotlin
@Composable
fun Modifier.clearLiquidGlass(
  shape: Shape,
  strength: Float = 0.55f,
  lensScale: Float = 1.8f,
  rimAlpha: Float = 0.42f,
  elevation: Dp = 3.dp,
  backdrop: Backdrop? = LocalGlassBackdrop.current,
): Modifier
```

参数范围：

| 参数 | 建议值 | 作用 |
|---|---:|---|
| `strength` | `0.45 ~ 0.65` | 折射总强度 |
| `lensScale` | `1.5 ~ 2.0` | 边缘折射带和位移量 |
| `rimAlpha` | `0.35 ~ 0.50` | 边缘镜面高光；不要变成一圈白描边 |
| `elevation` | `0 ~ 4dp` | 只留很轻的脱离感；不要重投影 |
| `tint` | `null` | 不加灰、不加白霜 |
| `blur` | `0` | 不调用 `blur()` |
| `veil` | `0` | 不铺霜面 |

核心绘制顺序：

```kotlin
val lensPx = with(LocalDensity.current) {
  (20f * strength * lensScale).dp.toPx()
}

Modifier
  .shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color(0x0C000000),
    spotColor = Color(0x14000000),
  )
  .drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
      // 零模糊：这里不要放 blur()
      lens(
        lensPx * 0.62f,
        lensPx * 1.15f,
        chromaticAberration = true,
      )
    },
    highlight = {
      Highlight.Default.copy(width = 1.dp, alpha = 0.85f)
    },
    shadow = { Shadow(alpha = 0f) },
    innerShadow = { InnerShadow(radius = 0.dp, alpha = 0f) },
    onDrawSurface = {
      // 通透玻璃：不要画 glassVeilBrush / 实色 tint
    },
  )
```

如果新应用不需要任何亮度修正，连 `vibrancy()` 也不要放。`vibrancy()` 不是模糊，但会改变背景亮度，不符合“完全通透、只做折射”的严格定义。

## 4. 按压接口

按压不是整块变灰，也不是固定往下沉。规则：

```text
静止：透明，只显示边缘折射
按下：从触摸点附近增强折射，轻微放大
抬手：弹簧回弹
嵌套玻璃：不新增外框，只增强折射和高光
```

推荐接口：

```kotlin
@Composable
fun LiquidPress(
  active: Float,
  shape: Shape,
  scale: Float = 1.03f,
  lensStrength: Float = 1.0f,
): Modifier
```

按压阶段只改变：

- `graphicsLayer.scaleX/scaleY`
- `lens()` 的强度
- 可选的手指径向高光

不要在按压阶段：

- 加灰色 ripple
- 加白色 border
- 加第二层 `GlassSurface`
- 加默认方向的位移
- 加高斯模糊

## 5. Lotus AI 现有公开接口

源码位置：

`/Users/l/Documents/ChatGPT/Lotus ai/app/src/main/java/ai/lotus/app/ui/glass/Glass.kt`

| 接口 | 用途 | 新应用是否可直接用 |
|---|---|---|
| `GlassBackground` | 双层背景录制 + 玻璃内容宿主 | 可参考，需替换颜色 token |
| `Modifier.glassPanel` | 旧版轻模糊玻璃 | 不建议原样用；它固定调用 `vibrancy()`，并带 `blur()`、霜面和描边 |
| `Modifier.floatGlass` | 旧版浮层玻璃 | 不建议原样用；继承了旧 `glassPanel` 配方 |
| `Modifier.glassInline` | 嵌套玻璃按压 | 不建议原样用；内部固定带 `blur()` |
| `Modifier.pressLiquid` | 行级按压 | 可参考动画结构，需去掉模糊和霜面 |
| `rowPress` | 列表行按压状态机 | 可复用交互结构 |
| `GlassSurface` | Box 包装器 | 可参考盒模型，需换零模糊 preset |
| `LiquidGlassIconButton` | 图标按钮 | 可参考触摸点跟随和折射逻辑，需删除 `blur()` |

### 不能直接照搬的原因

当前 `glassPanel()` 的实际配方仍是：

```kotlin
vibrancy()
if (blurPx > 0f) blur(blurPx)
lens(lensPx * 0.62f, lensPx * 1.15f, chromaticAberration = true)
onDrawSurface = { drawRect(brush = glassVeilBrush(scale = veil)) }
```

即使把 `blurScale` 设为 `0f`，它仍会执行 `vibrancy()`，并且默认绘制霜面。新应用必须使用单独的 `clearLiquidGlass`，不要只是把旧参数改成 0 后继续沿用整个旧配方。

## 6. 推荐最小接入顺序

1. 引入 `backdrop` 和 Compose 依赖。
2. 接入 `GlassBackground` 或等价的双层 `LayerBackdrop` 宿主。
3. 复制 `clearLiquidGlass`，只保留 `lens + Highlight + 轻阴影`。
4. 所有普通面板使用同一个 `clearLiquidGlass`，不要为每个页面写不同 tint。
5. 嵌套按钮只触发折射和高光，不再画第二块玻璃。
6. 最后再调 `strength`、`lensScale`、`rimAlpha`；不要再通过增加 `blur` 解决可见性问题。

## 7. 验收标准

- 静止状态：能透过玻璃看清背景内容，不能出现磨砂、灰雾或白板。
- 边缘：有可见的弯曲/位移和轻微色散，不是均匀描边。
- 按压：玻璃原地轻微放大，折射增强，抬手回弹；不能固定向下移动。
- 嵌套：父玻璃和子按键不能出现两层边框。
- 白色背景：玻璃仍能靠边缘折射和高光辨认，而不是靠模糊。

## 8. 关键源码参考

- 液态玻璃实现：`app/src/main/java/ai/lotus/app/ui/glass/Glass.kt`
- 主题色和背景：`app/src/main/java/ai/lotus/app/ui/theme/LotusTheme.kt`
- 依赖版本：`gradle/libs.versions.toml`
- 现有界面规格：`docs/UI-SPEC.md`
