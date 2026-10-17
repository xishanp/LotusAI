package ai.lotus.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Lotus 自绘图标集。
 *
 * ## 为什么不用 `material-icons-extended`
 *
 * 那个包会把 ~2000 个图标全部编进 dex。实测 debug APK：dex **62.9MB**、整包 64.6MB。
 * 装到手机上时 installd 要做 dexopt，一条 `adb install` 要卡好几分钟，
 * 表现就是"装不上去"。换成自绘之后整包 ~3MB。
 *
 * 别为了省事把那个依赖加回来。缺图标就在这里加一个 [LotusGlyph]。
 *
 * 所有图标统一 1.45dp 细线 + 圆头圆角，坐标用 0..1 相对值，换尺寸不走形。
 */
enum class LotusGlyph {
  /** ☰ 历史对话 */
  Menu,
  /** 对话 */
  Chat,
  /** 记忆（时钟） */
  Clock,
  /** 人设 */
  Person,
  /** 设置（齿轮） */
  Settings,
  /** 小莲 —— 底栏「设置」那格用它，按下去花瓣会弹开（bloom）。 */
  Lotus,
  /** + 附件 */
  Add,
  /** ↗ 发送（纸飞机） */
  Send,
  /** ⚡ 思考强度 */
  Bolt,
  /** ⇄ 模型切换（滑杆） */
  Tune,
  /** ← 返回 */
  Back,
  /** × 关闭 / 移除 */
  Close,
  /** › 设置行尾的箭头 */
  Chevron,
  /** ⋯ 更多 */
  More,
  /** ✦ 应用（设置页第一行） */
  Sparkle,
  /** ▦ 模型提供方 */
  Grid,
  /** ⬡ 默认模型 */
  Cube,
  /** 🌐 联网搜索 */
  Globe,
  /** 📄 文档 */
  Doc,
  /** 📁 文件夹（MD 工作区） */
  Folder,
  /** 🗄 已归档 */
  Archive,
  /** ⓘ 关于 */
  Info,
  /** 🔑 API Key */
  Key,
  /** 👁 视觉能力标记 */
  Eye,
  /** ↻ 获取 / 刷新 */
  Refresh,
  /** ⧉ 复制（两个叠着的圆角框） */
  Copy,
  /** ❝ 引用 */
  Quote,
  /** ✎ 编辑 */
  Edit,
  /** ✓ 复制完成 */
  Check,
  /** ↗ 外链 / 消息来源 */
  Link,
}

@Composable
fun LotusIcon(glyph: LotusGlyph, tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke =
      Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (glyph) {
      LotusGlyph.Menu -> drawMenu(w, h, tint, stroke)
      LotusGlyph.Chat -> drawChat(w, h, tint, stroke)
      LotusGlyph.Clock -> drawClock(w, h, tint, stroke)
      LotusGlyph.Person -> drawPerson(w, h, tint, stroke)
      LotusGlyph.Settings -> drawSettings(w, h, tint, stroke)
      LotusGlyph.Lotus -> drawLotus(w, h, tint, stroke)
      LotusGlyph.Add -> drawAdd(w, h, tint, stroke)
      LotusGlyph.Send -> drawSend(w, h, tint)
      LotusGlyph.Bolt -> drawBolt(w, h, tint)
      LotusGlyph.Tune -> drawTune(w, h, tint, stroke)
      LotusGlyph.Back -> drawBack(w, h, tint, stroke)
      LotusGlyph.Close -> drawClose(w, h, tint, stroke)
      LotusGlyph.Chevron -> drawChevron(w, h, tint, stroke)
      LotusGlyph.More -> drawMore(w, h, tint)
      LotusGlyph.Sparkle -> drawSparkle(w, h, tint)
      LotusGlyph.Grid -> drawGrid(w, h, tint)
      LotusGlyph.Cube -> drawCube(w, h, tint, stroke)
      LotusGlyph.Globe -> drawGlobe(w, h, tint, stroke)
      LotusGlyph.Doc -> drawDoc(w, h, tint, stroke)
      LotusGlyph.Folder -> drawFolder(w, h, tint, stroke)
      LotusGlyph.Archive -> drawArchive(w, h, tint, stroke)
      LotusGlyph.Info -> drawInfo(w, h, tint, stroke)
      LotusGlyph.Key -> drawKey(w, h, tint, stroke)
      LotusGlyph.Eye -> drawEye(w, h, tint, stroke)
      LotusGlyph.Refresh -> drawRefresh(w, h, tint, stroke)
      LotusGlyph.Copy -> drawCopy(w, h, tint, stroke)
      LotusGlyph.Quote -> drawQuote(w, h, tint, stroke)
      LotusGlyph.Edit -> drawEdit(w, h, tint, stroke)
      LotusGlyph.Check -> drawCheck(w, h, tint, stroke)
      LotusGlyph.Link -> drawLink(w, h, tint, stroke)
    }
  }
}

private fun DrawScope.drawChevron(w: Float, h: Float, c: Color, s: Stroke) {
  val p =
    Path().apply {
      moveTo(0.38f * w, 0.22f * h)
      lineTo(0.64f * w, 0.50f * h)
      lineTo(0.38f * w, 0.78f * h)
    }
  drawPath(p, c, style = s)
}

private fun DrawScope.drawClose(w: Float, h: Float, c: Color, s: Stroke) {
  drawLine(c, Offset(0.24f * w, 0.24f * h), Offset(0.76f * w, 0.76f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.76f * w, 0.24f * h), Offset(0.24f * w, 0.76f * h), s.width, StrokeCap.Round)
}

private fun DrawScope.drawBack(w: Float, h: Float, c: Color, s: Stroke) {
  drawLine(c, Offset(0.16f * w, 0.50f * h), Offset(0.84f * w, 0.50f * h), s.width, StrokeCap.Round)
  val head =
    Path().apply {
      moveTo(0.44f * w, 0.20f * h)
      lineTo(0.15f * w, 0.50f * h)
      lineTo(0.44f * w, 0.80f * h)
    }
  drawPath(head, c, style = s)
}

private fun DrawScope.drawMenu(w: Float, h: Float, c: Color, s: Stroke) {
  listOf(0.30f to 0.62f, 0.50f to 0.90f, 0.70f to 0.50f).forEach { (y, endX) ->
    drawLine(c, Offset(0.10f * w, y * h), Offset(endX * w, y * h), s.width, StrokeCap.Round)
  }
}

private fun DrawScope.drawChat(w: Float, h: Float, c: Color, s: Stroke) {
  drawRoundRect(
    color = c,
    topLeft = Offset(0.08f * w, 0.12f * h),
    size = Size(0.84f * w, 0.62f * h),
    cornerRadius = CornerRadius(0.24f * w),
    style = s,
  )
  val tail =
    Path().apply {
      moveTo(0.28f * w, 0.72f * h)
      lineTo(0.26f * w, 0.94f * h)
      lineTo(0.46f * w, 0.74f * h)
    }
  drawPath(tail, c, style = s)
  for (i in -1..1) {
    drawCircle(c, radius = 0.05f * w, center = Offset((0.5f + i * 0.19f) * w, 0.43f * h))
  }
}

private fun DrawScope.drawClock(w: Float, h: Float, c: Color, s: Stroke) {
  drawCircle(c, radius = 0.42f * w, center = Offset(0.5f * w, 0.5f * h), style = s)
  drawLine(c, Offset(0.5f * w, 0.5f * h), Offset(0.5f * w, 0.26f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.5f * w, 0.5f * h), Offset(0.70f * w, 0.58f * h), s.width, StrokeCap.Round)
}

private fun DrawScope.drawPerson(w: Float, h: Float, c: Color, s: Stroke) {
  drawCircle(c, radius = 0.43f * w, center = Offset(0.5f * w, 0.5f * h), style = s)
  drawCircle(c, radius = 0.16f * w, center = Offset(0.5f * w, 0.36f * h), style = s)
  drawArc(
    color = c,
    startAngle = 195f,
    sweepAngle = 150f,
    useCenter = false,
    topLeft = Offset(0.17f * w, 0.58f * h),
    size = Size(0.66f * w, 0.60f * h),
    style = s,
  )
}

private fun DrawScope.drawSettings(w: Float, h: Float, c: Color, s: Stroke) {
  val cx = 0.5f * w
  val cy = 0.5f * h
  drawCircle(c, radius = 0.30f * w, center = Offset(cx, cy), style = s)
  drawCircle(c, radius = 0.11f * w, center = Offset(cx, cy), style = s)
  for (i in 0 until 8) {
    val a = Math.toRadians((i * 45).toDouble())
    val dx = kotlin.math.cos(a).toFloat()
    val dy = kotlin.math.sin(a).toFloat()
    drawLine(
      c,
      Offset(cx + dx * 0.30f * w, cy + dy * 0.30f * w),
      Offset(cx + dx * 0.44f * w, cy + dy * 0.44f * w),
      s.width * 1.9f,
      StrokeCap.Round,
    )
  }
}

/**
 * 小莲：五片花瓣 + 底下一条水线。
 *
 * 用户口径：「把设置替换成一个小莲」。花瓣用三次贝塞尔从花心展开，中心的最高、两侧依次低,
 * 所以缩到 22dp 也看得出是朵花，不是一团线。
 */
private fun DrawScope.drawLotus(w: Float, h: Float, c: Color, s: Stroke) {
  val baseX = 0.5f * w
  val baseY = 0.78f * h
  // (角度°, 长度系数) —— 0° 是正上方，正角度往右偏。
  val petals = listOf(-58f to 0.46f, -30f to 0.58f, 0f to 0.68f, 30f to 0.58f, 58f to 0.46f)
  petals.forEach { (deg, lenF) ->
    val a = Math.toRadians(deg.toDouble())
    val dx = kotlin.math.sin(a).toFloat()
    val dy = -kotlin.math.cos(a).toFloat()
    val px = kotlin.math.cos(a).toFloat()
    val py = kotlin.math.sin(a).toFloat()
    val len = lenF * h
    fun pt(along: Float, side: Float) =
      Offset(baseX + dx * len * along + px * len * side, baseY + dy * len * along + py * len * side)
    val p =
      Path().apply {
        moveTo(baseX, baseY)
        cubicTo(
          pt(0.30f, 0.34f).x, pt(0.30f, 0.34f).y,
          pt(0.72f, 0.24f).x, pt(0.72f, 0.24f).y,
          pt(1f, 0f).x, pt(1f, 0f).y,
        )
        cubicTo(
          pt(0.72f, -0.24f).x, pt(0.72f, -0.24f).y,
          pt(0.30f, -0.34f).x, pt(0.30f, -0.34f).y,
          baseX, baseY,
        )
        close()
      }
    drawPath(p, c, style = s)
  }
  // 水线：花心底下一条浅浅的弧。
  val water =
    Path().apply {
      moveTo(0.12f * w, 0.86f * h)
      cubicTo(
        0.30f * w, 0.99f * h,
        0.70f * w, 0.99f * h,
        0.88f * w, 0.86f * h,
      )
    }
  drawPath(water, c, style = s)
}

private fun DrawScope.drawAdd(w: Float, h: Float, c: Color, s: Stroke) {
  drawLine(c, Offset(0.5f * w, 0.16f * h), Offset(0.5f * w, 0.84f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.16f * w, 0.5f * h), Offset(0.84f * w, 0.5f * h), s.width, StrokeCap.Round)
}

private fun DrawScope.drawSend(w: Float, h: Float, c: Color) {
  val p =
    Path().apply {
      moveTo(0.08f * w, 0.86f * h)
      lineTo(0.94f * w, 0.50f * h)
      lineTo(0.08f * w, 0.14f * h)
      lineTo(0.33f * w, 0.50f * h)
      close()
    }
  drawPath(p, c)
}

private fun DrawScope.drawBolt(w: Float, h: Float, c: Color) {
  val p =
    Path().apply {
      moveTo(0.58f * w, 0.04f * h)
      lineTo(0.18f * w, 0.56f * h)
      lineTo(0.44f * w, 0.56f * h)
      lineTo(0.38f * w, 0.96f * h)
      lineTo(0.82f * w, 0.42f * h)
      lineTo(0.54f * w, 0.42f * h)
      close()
    }
  drawPath(p, c)
}

/** 四角星：两个方向各一条"两头细"的星形。直接画实心路径。 */
private fun DrawScope.drawSparkle(w: Float, h: Float, c: Color) {
  val p =
    Path().apply {
      moveTo(0.50f * w, 0.04f * h)
      lineTo(0.60f * w, 0.40f * h)
      lineTo(0.96f * w, 0.50f * h)
      lineTo(0.60f * w, 0.60f * h)
      lineTo(0.50f * w, 0.96f * h)
      lineTo(0.40f * w, 0.60f * h)
      lineTo(0.04f * w, 0.50f * h)
      lineTo(0.40f * w, 0.40f * h)
      close()
    }
  drawPath(p, c)
}

/** 四方块（模型提供方 = 一堆可接的东西）。 */
private fun DrawScope.drawGrid(w: Float, h: Float, c: Color) {
  listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (dx, dy) ->
    drawRoundRect(
      color = c,
      topLeft = Offset((0.10f + dx * 0.46f) * w, (0.10f + dy * 0.46f) * h),
      size = Size(0.34f * w, 0.34f * h),
      cornerRadius = CornerRadius(0.09f * w),
      style = Stroke(width = 1.35.dp.toPx()),
    )
  }
}

/** 立方体（默认模型）。 */
private fun DrawScope.drawCube(w: Float, h: Float, c: Color, s: Stroke) {
  val hex =
    Path().apply {
      moveTo(0.50f * w, 0.06f * h)
      lineTo(0.90f * w, 0.29f * h)
      lineTo(0.90f * w, 0.71f * h)
      lineTo(0.50f * w, 0.94f * h)
      lineTo(0.10f * w, 0.71f * h)
      lineTo(0.10f * w, 0.29f * h)
      close()
    }
  drawPath(hex, c, style = s)
  drawLine(c, Offset(0.50f * w, 0.50f * h), Offset(0.50f * w, 0.94f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.10f * w, 0.29f * h), Offset(0.50f * w, 0.50f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.90f * w, 0.29f * h), Offset(0.50f * w, 0.50f * h), s.width, StrokeCap.Round)
}

/** 地球（联网搜索）。 */
private fun DrawScope.drawGlobe(w: Float, h: Float, c: Color, s: Stroke) {
  drawCircle(c, radius = 0.42f * w, center = Offset(0.5f * w, 0.5f * h), style = s)
  drawLine(c, Offset(0.08f * w, 0.5f * h), Offset(0.92f * w, 0.5f * h), s.width, StrokeCap.Round)
  drawOval(
    color = c,
    topLeft = Offset(0.30f * w, 0.08f * h),
    size = Size(0.40f * w, 0.84f * h),
    style = s,
  )
}

/** 文档。 */
private fun DrawScope.drawDoc(w: Float, h: Float, c: Color, s: Stroke) {
  drawRoundRect(
    color = c,
    topLeft = Offset(0.22f * w, 0.08f * h),
    size = Size(0.56f * w, 0.84f * h),
    cornerRadius = CornerRadius(0.10f * w),
    style = s,
  )
  listOf(0.34f, 0.50f, 0.66f).forEach { y ->
    drawLine(
      c,
      Offset(0.34f * w, y * h),
      Offset(if (y > 0.6f) 0.56f * w else 0.66f * w, y * h),
      s.width,
      StrokeCap.Round,
    )
  }
}

/** 文件夹：MD 工作区（提示词 + 记忆都落在这个目录里）。 */
private fun DrawScope.drawFolder(w: Float, h: Float, c: Color, s: Stroke) {
  val p =
    Path().apply {
      // 左上角那个小"标签"，folder 之所以一眼认得出就靠它。
      moveTo(0.10f * w, 0.28f * h)
      lineTo(0.38f * w, 0.28f * h)
      lineTo(0.46f * w, 0.38f * h)
      lineTo(0.90f * w, 0.38f * h)
      lineTo(0.90f * w, 0.82f * h)
      lineTo(0.10f * w, 0.82f * h)
      close()
    }
  drawPath(p, c, style = s)
}

/** 归档盒。 */
private fun DrawScope.drawArchive(w: Float, h: Float, c: Color, s: Stroke) {
  drawRoundRect(
    color = c,
    topLeft = Offset(0.08f * w, 0.14f * h),
    size = Size(0.84f * w, 0.20f * h),
    cornerRadius = CornerRadius(0.07f * w),
    style = s,
  )
  drawRoundRect(
    color = c,
    topLeft = Offset(0.16f * w, 0.38f * h),
    size = Size(0.68f * w, 0.48f * h),
    cornerRadius = CornerRadius(0.08f * w),
    style = s,
  )
  drawLine(c, Offset(0.38f * w, 0.55f * h), Offset(0.62f * w, 0.55f * h), s.width, StrokeCap.Round)
}

/** ⓘ 关于。 */
private fun DrawScope.drawInfo(w: Float, h: Float, c: Color, s: Stroke) {
  drawCircle(c, radius = 0.42f * w, center = Offset(0.5f * w, 0.5f * h), style = s)
  drawCircle(c, radius = 0.045f * w, center = Offset(0.5f * w, 0.30f * h))
  drawLine(c, Offset(0.5f * w, 0.44f * h), Offset(0.5f * w, 0.72f * h), s.width, StrokeCap.Round)
}

/** 钥匙（API Key）。 */
private fun DrawScope.drawKey(w: Float, h: Float, c: Color, s: Stroke) {
  drawCircle(c, radius = 0.20f * w, center = Offset(0.30f * w, 0.34f * h), style = s)
  drawLine(c, Offset(0.44f * w, 0.46f * h), Offset(0.90f * w, 0.80f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.72f * w, 0.66f * h), Offset(0.84f * w, 0.56f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.82f * w, 0.74f * h), Offset(0.92f * w, 0.66f * h), s.width, StrokeCap.Round)
}

/** 眼睛（视觉能力）。 */
private fun DrawScope.drawEye(w: Float, h: Float, c: Color, s: Stroke) {
  val p =
    Path().apply {
      moveTo(0.06f * w, 0.50f * h)
      cubicTo(0.26f * w, 0.20f * h, 0.74f * w, 0.20f * h, 0.94f * w, 0.50f * h)
      cubicTo(0.74f * w, 0.80f * h, 0.26f * w, 0.80f * h, 0.06f * w, 0.50f * h)
      close()
    }
  drawPath(p, c, style = s)
  drawCircle(c, radius = 0.13f * w, center = Offset(0.5f * w, 0.5f * h), style = s)
}

/** 循环箭头（获取 / 刷新）。 */
private fun DrawScope.drawRefresh(w: Float, h: Float, c: Color, s: Stroke) {
  // 大半个圆 + 一枚实心箭头，接在圆的右上缺口上。小尺寸下比细线箭头清楚得多。
  drawArc(
    color = c,
    startAngle = -55f,
    sweepAngle = 295f,
    useCenter = false,
    topLeft = Offset(0.16f * w, 0.16f * h),
    size = Size(0.68f * w, 0.68f * h),
    style = s,
  )
  val head =
    Path().apply {
      moveTo(0.58f * w, 0.06f * h)
      lineTo(0.88f * w, 0.24f * h)
      lineTo(0.56f * w, 0.40f * h)
      close()
    }
  drawPath(head, c)
}

private fun DrawScope.drawMore(w: Float, h: Float, c: Color) {
  for (i in -1..1) {
    drawCircle(c, radius = 0.075f * w, center = Offset((0.5f + i * 0.28f) * w, 0.5f * h))
  }
}

private fun DrawScope.drawTune(w: Float, h: Float, c: Color, s: Stroke) {
  // 三条滑杆：线在滑块处断开，滑块画实心圆。
  listOf(0.28f to 0.68f, 0.50f to 0.34f, 0.72f to 0.58f).forEach { (y, knobX) ->
    drawLine(
      c, Offset(0.10f * w, y * h), Offset((knobX - 0.10f) * w, y * h),
      s.width, StrokeCap.Round,
    )
    drawLine(
      c, Offset((knobX + 0.10f) * w, y * h), Offset(0.90f * w, y * h),
      s.width, StrokeCap.Round,
    )
    drawCircle(c, radius = 0.075f * w, center = Offset(knobX * w, y * h))
  }
}


private fun DrawScope.drawCopy(w: Float, h: Float, c: Color, s: Stroke) {
  // 后面那张纸（只画露出来的 L 形）+ 前面那个完整圆角框。
  val r = CornerRadius(0.10f * w, 0.10f * w)
  drawRoundRect(
    c,
    topLeft = Offset(0.30f * w, 0.14f * h),
    size = Size(0.56f * w, 0.56f * h),
    cornerRadius = r,
    style = s,
  )
  drawRoundRect(
    c,
    topLeft = Offset(0.14f * w, 0.30f * h),
    size = Size(0.56f * w, 0.56f * h),
    cornerRadius = r,
    style = s,
  )
}

private fun DrawScope.drawQuote(w: Float, h: Float, c: Color, s: Stroke) {
  // 引用 = 回复箭头：一个拐下去再向左的弯 + 箭头。比两个小逗号好认，14dp 下也立得住。
  val p =
    Path().apply {
      moveTo(0.84f * w, 0.74f * h)
      lineTo(0.84f * w, 0.46f * h)
      quadraticTo(0.84f * w, 0.26f * h, 0.64f * w, 0.26f * h)
      lineTo(0.26f * w, 0.26f * h)
    }
  drawPath(p, c, style = s)
  val head =
    Path().apply {
      moveTo(0.44f * w, 0.08f * h)
      lineTo(0.20f * w, 0.26f * h)
      lineTo(0.44f * w, 0.44f * h)
    }
  drawPath(head, c, style = s)
}

private fun DrawScope.drawEdit(w: Float, h: Float, c: Color, s: Stroke) {
  // 斜笔杆 + 笔尖。
  drawLine(c, Offset(0.28f * w, 0.72f * h), Offset(0.76f * w, 0.24f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.24f * w, 0.64f * h), Offset(0.32f * w, 0.80f * h), s.width, StrokeCap.Round)
  drawLine(c, Offset(0.20f * w, 0.80f * h), Offset(0.36f * w, 0.76f * h), s.width, StrokeCap.Round)
}

private fun DrawScope.drawCheck(w: Float, h: Float, c: Color, s: Stroke) {
  // 一个勾。压在"复制"那枚图标上时，两笔要重合得看起来像同一枚图标换了状态。
  val p =
    Path().apply {
      moveTo(0.22f * w, 0.54f * h)
      lineTo(0.42f * w, 0.74f * h)
      lineTo(0.80f * w, 0.28f * h)
    }
  drawPath(p, c, style = s)
}

private fun DrawScope.drawLink(w: Float, h: Float, c: Color, s: Stroke) {
  // 外链：一个缺右上角的方框 + 一支朝右上出去的箭头。
  val box =
    Path().apply {
      moveTo(0.58f * w, 0.20f * h)
      lineTo(0.24f * w, 0.20f * h)
      lineTo(0.24f * w, 0.78f * h)
      lineTo(0.80f * w, 0.78f * h)
      lineTo(0.80f * w, 0.46f * h)
    }
  drawPath(box, c, style = s)
  drawLine(c, Offset(0.50f * w, 0.50f * h), Offset(0.80f * w, 0.20f * h), s.width, StrokeCap.Round)
  val head =
    Path().apply {
      moveTo(0.60f * w, 0.15f * h)
      lineTo(0.86f * w, 0.14f * h)
      lineTo(0.85f * w, 0.40f * h)
    }
  drawPath(head, c, style = s)
}
