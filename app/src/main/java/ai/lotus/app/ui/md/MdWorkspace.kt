package ai.lotus.app.ui.md

import ai.lotus.app.R
import ai.lotus.app.data.Prompts
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.glass.pressLiquid
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.settings.Caption
import ai.lotus.app.ui.settings.GlassTextArea
import ai.lotus.app.ui.settings.Hairline
import ai.lotus.app.ui.settings.SettingsGroup
import ai.lotus.app.ui.settings.SettingsScaffold
import ai.lotus.app.ui.theme.LotusPalette
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MD 工作区 —— 底栏中间那格**文件夹**。
 *
 * 用户口径：「memory 也就是记忆，应该包含在 MD 中的某个文件里面，或者就在一个目录下」。
 * 所以记忆和人设不分家，全在这里以一棵真实的 `.md` 目录树摆出来：
 * 点文件夹进去、点文件直接改，改完落盘、下一轮请求就读得到（见 [Prompts.systemBlocks]）。
 *
 * ```
 * md/
 * ├── IDENTITY.md / SOUL.md / AGENTS.md / USER.md / MEMORY.md   ← 会进请求的 5 份
 * └── memory/  facts/ decisions/ lessons/ projects/ references/ tasks/ temporary/
 * ```
 *
 * 这里**只有文件读写**，没有向量库、没有额外模型调用 —— 每一分 token 花在哪都写在文件里，看得见。
 */
@Composable
fun MdWorkspace(bottomInset: Dp) {
  // 目录栈（相对 md/）。空 = 在根上。
  var trail by remember { mutableStateOf(listOf<String>()) }
  var openFile by remember { mutableStateOf<String?>(null) }
  var revision by remember { mutableStateOf(0) }

  // ⚠️ **系统返回键必须先在文件页里退一层**：开着文件就关文件、在子目录就回上一级，
  //    只有已经在根目录时才把返回让给外层（否则会直接退出到桌面）。
  //    用户口径：「在文件页中点开一个文件 查看返回的时候直接退出桌面了 应该返回到它的上级」。
  BackHandler(enabled = openFile != null || trail.isNotEmpty()) {
    if (openFile != null) {
      revision++
      openFile = null
    } else {
      trail = trail.dropLast(1)
    }
  }

  val opened = openFile
  if (opened != null) {
    MdEditor(
      rel = opened,
      bottomInset = bottomInset,
      onBack = {
        revision++
        openFile = null
      },
    )
    return
  }

  val relDir = if (trail.isEmpty()) Prompts.Root else "${Prompts.Root}/${trail.joinToString("/")}"
  val context = LocalContext.current
  val entries = remember(relDir, revision) { Prompts.listDir(context, relDir) }

  MdBrowser(
    title = trail.lastOrNull().orEmpty(),
    atRoot = trail.isEmpty(),
    entries = entries,
    bottomInset = bottomInset,
    onUp = { trail = trail.dropLast(1) },
    onOpenDir = { trail = trail + it },
    onOpenFile = { openFile = it },
  )
}

@Composable
private fun MdBrowser(
  title: String,
  atRoot: Boolean,
  entries: List<Prompts.MdEntry>,
  bottomInset: Dp,
  onUp: () -> Unit,
  onOpenDir: (String) -> Unit,
  onOpenFile: (String) -> Unit,
) {
  val body: @Composable () -> Unit = {
    Caption("文件")
    SettingsGroup(enter = 0) {
      if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
          Text("空目录", style = MaterialTheme.typography.bodyMedium, color = LotusPalette.InkFaint)
        }
      }
      entries.forEachIndexed { index, entry ->
        if (index > 0) Hairline()
        MdRow(
          name = entry.name,
          meta = if (entry.isDir) "文件夹" else "${humanSize(entry.bytes)} · ${shortDate(entry.modified)}",
          isDir = entry.isDir,
          onClick = { if (entry.isDir) onOpenDir(entry.name) else onOpenFile(entry.rel) },
        )
      }
    }
  }

  if (atRoot) {
    // 根目录没有"上一层"，所以不写大标题（用户口径：「最上面也别写个 MD，好丑啊」）——
    // 液态玻璃本来就是拿来代替顶栏、把空间让给内容的。
    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(bottom = bottomInset + 24.dp)
          .padding(horizontal = 18.dp)
          .padding(top = 6.dp)
    ) {
      // 顶上放立绘（她正拿着文件在看），**不写大标题** —— 用户口径：
      // 「最上面也别写个 MD，好丑啊，你生成一张我们这个绘图，他拿着文件看的样子」。
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
          painter = painterResource(R.drawable.lotus_md),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.height(150.dp),
        )
      }
      Spacer(Modifier.height(6.dp))
      body()
    }
  } else {
    SettingsScaffold(title = title, bottomInset = bottomInset, onBack = onUp) { body() }
  }
}

/** 文件/文件夹行：图标 + 名字 + 底下那行 meta（对齐度参照用户给的文件管理器截图）。 */
@Composable
private fun MdRow(name: String, meta: String, isDir: Boolean, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
      label = "mdRowDim",
    )
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .pressLiquid(active = press, shape = RoundedCornerShape(16.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // ⚠️ 这里原来给图标托了一枚小玻璃片。用户 2026-10-11 定：同一块玻璃内部**不许再出现第二块**
    //   （「跟这文件区那里也有很多……不要描边了，直接隐藏起来」）。整行本来就坐在列表那块大玻璃上，
    //    再垫一层只会在白底上多出一圈脏边。图标裸画即可，行的按压反馈一点没少。
    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
      LotusIcon(
        glyph = if (isDir) LotusGlyph.Folder else LotusGlyph.Doc,
        tint = LotusPalette.Ink.copy(alpha = 0.82f),
        modifier = Modifier.size(18.dp),
      )
    }
    Spacer(Modifier.width(13.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        color = LotusPalette.Ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = LotusPalette.InkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun MdEditor(rel: String, bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  var text by remember(rel) { mutableStateOf(Prompts.read(context, rel)) }

  SettingsScaffold(title = rel.substringAfterLast('/'), bottomInset = bottomInset, onBack = onBack) {
    GlassTextArea(
      label = rel,
      value = text,
      placeholder = "写点什么…",
      minLines = 16,
      actionLabel = if (text.isBlank()) "插入模板" else "清空",
      onAction = {
        text = templateFor(rel)
        Prompts.write(context, rel, text)
      },
      onChange = {
        text = it
        Prompts.write(context, rel, it)
      },
    )
  }
}

/** "插入模板"那一枚：只对 5 份前线文件给内容，档案柜里新建的文件从空白开始。 */
private fun templateFor(rel: String): String =
  when (rel) {
    Prompts.Identity -> Prompts.IdentityTemplate
    Prompts.Soul -> Prompts.SoulTemplate
    Prompts.Agents -> Prompts.AgentsTemplate
    Prompts.User -> Prompts.UserTemplate
    Prompts.Memory -> Prompts.MemoryTemplate
    else -> ""
  }

private fun humanSize(bytes: Long): String =
  when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f kB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
  }

private fun shortDate(millis: Long): String =
  SimpleDateFormat("yyyy/M/d", Locale.US).format(Date(millis))
