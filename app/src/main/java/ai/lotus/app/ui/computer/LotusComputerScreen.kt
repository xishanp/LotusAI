package ai.lotus.app.ui.computer

import ai.lotus.app.computer.ComputerCommandResult
import ai.lotus.app.data.AppPrefs
import ai.lotus.app.data.ProjectLinks
import ai.lotus.app.computer.ComputerPhase
import ai.lotus.app.computer.ComputerStatus
import ai.lotus.app.computer.LinuxHubBridge
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.glass.pressLiquid
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.settings.Caption
import ai.lotus.app.ui.settings.Hairline
import ai.lotus.app.ui.settings.SettingsGroup
import ai.lotus.app.ui.settings.SettingsRow
import ai.lotus.app.ui.settings.SettingsScaffold
import ai.lotus.app.ui.theme.LotusPalette
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import ai.lotus.app.ui.motion.LotusSpringPreset
import ai.lotus.app.ui.motion.lotusSpring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * “Lotus的电脑”只做两件事：展示外部 LinuxHub 的真实状态，并提供稳定的 CLI 桥接口。
 *
 * 状态和输出全部来自 LinuxHub 的 `127.0.0.1:38473`。没有 Token、服务未启动或接口失败时
 * 都会如实标出，不在 App 内偷偷创建容器，也不伪造命令结果。
 */
@Composable
fun LotusComputerScreen(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var status by remember { mutableStateOf(ComputerStatus()) }
  var checking by remember { mutableStateOf(false) }
  var command by remember { mutableStateOf("") }
  var result by remember { mutableStateOf<ComputerCommandResult?>(null) }
  var running by remember { mutableStateOf(false) }
  var token by remember { mutableStateOf(AppPrefs.linuxHubToken(context)) }
  var showToken by remember { mutableStateOf(false) }
  var tokenHint by remember { mutableStateOf("") }

  suspend fun refresh() {
    checking = true
    status = LinuxHubBridge.status(context)
    checking = false
  }

  LaunchedEffect(Unit) { refresh() }

  SettingsScaffold("Lotus的电脑", bottomInset, onBack) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier.size(42.dp)
            .glassPanel(CircleShape, strength = 0.5f, rimAlpha = 0.62f, elevation = 5.dp),
        contentAlignment = Alignment.Center,
      ) {
        LotusIcon(LotusGlyph.Computer, LotusPalette.Ink, Modifier.size(20.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column {
        Text("LinuxHub 外部工作区", style = MaterialTheme.typography.titleMedium, color = LotusPalette.Ink)
        Text(
          "外部容器与无头桌面，由 LinuxHub 本机服务提供",
          style = MaterialTheme.typography.labelMedium,
          color = LotusPalette.InkMuted,
        )
      }
    }

    Spacer(Modifier.height(18.dp))
    Caption("本机 Agent Token")
    SettingsGroup(enter = 0) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
          "从 LinuxHub → 设置 → Lotus的电脑 复制 Token，再粘贴到这里。Token 只保存在本机私有配置中。",
          style = MaterialTheme.typography.bodySmall,
          color = LotusPalette.InkMuted,
          lineHeight = 18.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          BasicTextField(
            value = token,
            onValueChange = {
              token = it
              tokenHint = ""
              LinuxHubBridge.invalidate()
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle =
              TextStyle(
                color = LotusPalette.Ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
              ),
            cursorBrush = SolidColor(LotusPalette.Ink),
            visualTransformation =
              if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            decorationBox = { inner ->
              if (token.isBlank()) {
                Text(
                  "粘贴 Bearer Token",
                  color = LotusPalette.InkFaint,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 12.sp,
                  lineHeight = 18.sp,
                )
              }
              inner()
            },
          )
          Spacer(Modifier.width(8.dp))
          TerminalButton(
            text = if (showToken) "隐藏" else "显示",
            enabled = token.isNotBlank(),
            onClick = { showToken = !showToken },
          )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TerminalButton(
            text = "粘贴",
            enabled = true,
            onClick = {
              val clipboard = context.getSystemService(ClipboardManager::class.java)
              val pasted =
                clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                  ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
              if (pasted.isNotEmpty()) {
                token = pasted
                tokenHint = "已从剪贴板填入，点「保存并检测」"
                LinuxHubBridge.invalidate()
              } else {
                tokenHint = "剪贴板里没有文本"
              }
            },
          )
          TerminalButton(
            text = if (checking) "检测中…" else "保存并检测",
            enabled = !checking,
            onClick = {
              AppPrefs.setLinuxHubToken(context, token)
              token = AppPrefs.linuxHubToken(context)
              tokenHint =
                if (token.isBlank()) "Token 已清空"
                else "Token 已保存，正在验证 LinuxHub"
              scope.launch { refresh() }
            },
          )
          if (token.isNotBlank()) {
            TerminalButton(
              text = "清空",
              enabled = !checking,
              onClick = {
                AppPrefs.setLinuxHubToken(context, "")
                token = ""
                tokenHint = "Token 已清空"
                LinuxHubBridge.invalidate()
                scope.launch { refresh() }
              },
            )
          }
        }
        if (tokenHint.isNotBlank()) {
          Spacer(Modifier.height(8.dp))
          Text(
            text = tokenHint,
            style = MaterialTheme.typography.bodySmall,
            color = LotusPalette.InkMuted,
          )
        }
      }
    }

    Spacer(Modifier.height(18.dp))

    Box(
      modifier =
        Modifier.fillMaxWidth()
          .glassPanel(
            shape = RoundedCornerShape(26.dp),
            strength = 0.52f,
            rimAlpha = 0.72f,
            veil = 0.9f,
            elevation = 9.dp,
          )
          .padding(20.dp),
    ) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            Modifier.size(8.dp)
              .clip(CircleShape)
              .background(
                when (status.phase) {
                  ComputerPhase.Ready -> LotusPalette.Ink.copy(alpha = 0.72f)
                  ComputerPhase.Running -> LotusPalette.Ink.copy(alpha = 0.54f)
                  ComputerPhase.Error -> LotusPalette.Ink.copy(alpha = 0.36f)
                  ComputerPhase.Unavailable -> LotusPalette.InkFaint
                }
              )
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (checking) "正在检测接口…" else phaseLabel(status.phase),
            color = LotusPalette.Ink.copy(alpha = 0.76f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
          )
        }
        Spacer(Modifier.height(10.dp))
        Text(
          text = status.message,
          color = LotusPalette.Ink,
          fontSize = 15.sp,
          lineHeight = 22.sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
          text = "连接、工具目录与执行结果均来自本机 LinuxHub。",
          color = LotusPalette.InkMuted,
          fontSize = 11.5.sp,
          lineHeight = 17.sp,
        )
      }
    }

    Spacer(Modifier.height(18.dp))
    Caption("连接")
    SettingsGroup(enter = 1) {
      SettingsRow(
        title = "LinuxHub",
        value = status.host,
        icon = LotusGlyph.Computer,
        chevron = false,
        onClick = {},
      )
      Hairline()
      SettingsRow(
        title = "检测接口",
        value = if (checking) "检测中…" else "重新检测",
        icon = LotusGlyph.Refresh,
        onClick = { if (!checking) scope.launch { refresh() } },
      )
    }

    Spacer(Modifier.height(16.dp))
    Caption("能力")
    SettingsGroup(enter = 2) {
      status.capabilities.forEachIndexed { index, capability ->
        if (index > 0) Hairline()
        SettingsRow(
          title = capability,
          value = if (status.phase == ComputerPhase.Ready) "可用" else "连接后启用",
          icon = when (capability) {
            "终端" -> LotusGlyph.Chat
            "无头桌面" -> LotusGlyph.Computer
            "截图" -> LotusGlyph.Doc
            "鼠标键盘" -> LotusGlyph.Tune
            "应用启动" -> LotusGlyph.Sparkle
            "Shizuku" -> LotusGlyph.Bolt
            "系统信息" -> LotusGlyph.Info
            else -> LotusGlyph.Globe
          },
          chevron = false,
          onClick = {},
        )
      }
      if (status.capabilities.isEmpty()) {
        SettingsRow(
          title = "尚未读取工具目录",
          value = "连接后显示",
          icon = LotusGlyph.Info,
          chevron = false,
          onClick = {},
        )
      }
    }

    Spacer(Modifier.height(16.dp))
    Caption("终端")
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .glassPanel(
            shape = RoundedCornerShape(22.dp),
            strength = 0.54f,
            rimAlpha = 0.76f,
            veil = 0.88f,
            elevation = 7.dp,
          )
          .padding(14.dp),
    ) {
      Column {
        Row(verticalAlignment = Alignment.Top) {
          Text(
            "$",
            color = LotusPalette.InkMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
          )
          Spacer(Modifier.width(8.dp))
          BasicTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier.weight(1f),
            singleLine = false,
            minLines = 2,
            maxLines = 6,
            textStyle =
              TextStyle(
                color = LotusPalette.Ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
              ),
            cursorBrush = SolidColor(LotusPalette.Ink),
            decorationBox = { inner ->
              if (command.isBlank()) {
                Text(
                  if (status.phase == ComputerPhase.Ready) "输入要在 LinuxHub 容器执行的命令" else "先配置 Token 并检测连接",
                  color = LotusPalette.InkMuted,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 13.sp,
                  lineHeight = 19.sp,
                )
              }
              inner()
            },
          )
        }
        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TerminalButton(
            text = if (running) "运行中…" else "运行",
            enabled = !running && command.isNotBlank() && status.phase == ComputerPhase.Ready,
            onClick = {
              scope.launch {
                running = true
                result = LinuxHubBridge.execute(context, command)
                running = false
              }
            },
          )
        }
        result?.let {
          Spacer(Modifier.height(12.dp))
          Hairline()
          Spacer(Modifier.height(10.dp))
          Text(
            text = it.displayText(),
            color = if (it.ok) LotusPalette.Ink else LotusPalette.InkMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
          )
          Text(
            text = buildString {
              append("exit=").append(it.exitCode ?: "—")
              append(" · ").append(it.durationMs).append("ms")
            },
            color = LotusPalette.InkFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp),
          )
        }
      }
    }

    Spacer(Modifier.height(18.dp))
    Caption("说明")
    Text(
      "所有网络请求只发往 127.0.0.1:38473。未启动 LinuxHub 或 Token 无效时，命令不会执行，也不会显示成功。",
      style = MaterialTheme.typography.bodySmall,
      color = LotusPalette.InkMuted,
      lineHeight = 18.sp,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
    )
    SettingsGroup(enter = 3) {
      SettingsRow(
        title = "LinuxHub 原项目",
        value = ProjectLinks.DoroUbuntuRepo,
        icon = LotusGlyph.Globe,
        onClick = {
          runCatching {
            context.startActivity(
              Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(ProjectLinks.DoroUbuntuUrl)
              }
            )
          }
        },
      )
    }
  }
}

@Composable
private fun TerminalButton(text: String, enabled: Boolean, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = lotusSpring(LotusSpringPreset.Snappy),
      label = "computerTerminalPress",
    )
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .pressLiquid(active = press, shape = RoundedCornerShape(50), scale = 1.045f)
        .clickable(
          enabled = enabled,
          interactionSource = interaction,
          indication = null,
          onClick = onClick,
        )
        .padding(horizontal = 16.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (enabled) LotusPalette.Ink else LotusPalette.InkFaint,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
    )
  }
}

private fun phaseLabel(phase: ComputerPhase): String =
  when (phase) {
    ComputerPhase.Unavailable -> "未连接"
    ComputerPhase.Ready -> "LinuxHub 已连接"
    ComputerPhase.Running -> "命令运行中"
    ComputerPhase.Error -> "连接异常"
  }
