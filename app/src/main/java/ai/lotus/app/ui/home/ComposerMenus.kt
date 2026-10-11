package ai.lotus.app.ui.home

import ai.lotus.app.ui.glass.floatGlass
import ai.lotus.app.ui.glass.glassPanel
import ai.lotus.app.ui.glass.rowPress
import ai.lotus.app.ui.glass.glassRim
import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.model.ThinkingOption
import ai.lotus.app.ui.theme.LotusPalette
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 输入条上方弹出的玻璃菜单（思考强度 / 模型切换）。
 *
 * 为什么不用 `Popup`：这里就贴在输入条正上方、跟着输入条的抬升一起走，用布局里的浮层
 * 最省事，也不会另开一个窗口跟输入法抢焦点。外层再铺一层透明遮罩，点空白即关。
 */
@Composable
fun GlassMenuPanel(
  items: List<String>,
  selected: Int,
  modifier: Modifier = Modifier,
  width: Dp = 236.dp,
  onPick: (Int) -> Unit,
) {
  val shape = RoundedCornerShape(22.dp)
  Column(
    modifier =
      modifier
        .width(width)
        // 跟弹层/面板同一块玻璃（floatGlass）—— 用户口径：「两种不同风格的玻璃，全部统一」。
        .floatGlass(shape = shape, strength = 0.55f, elevation = 10.dp)
        .padding(vertical = 8.dp),
  ) {
    items.forEachIndexed { index, label ->
      // 面板里的行必须是**真按键**：按下浮出一层薄折射 + 轻微放大，抬手回弹（静止隐形）。
      val rowP = rowPress(RoundedCornerShape(14.dp))
      Row(
        // ⚠️ 必须是 fillMaxWidth：写成 fillMaxSize 会让每一行吃掉整屏高度，
        //    面板被撑成一整块玻璃、第二项开始全被顶到屏幕外（踩过）。
        modifier =
          Modifier.fillMaxWidth()
            .then(rowP.modifier)
            .clickable(
              interactionSource = rowP.interaction,
              indication = null,
            ) { onPick(index) }
            .height(46.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = label,
          color = LotusPalette.Ink,
          fontSize = 15.sp,
          modifier = Modifier.weight(1f),
        )
        // 选中态只放一枚小圆点：白底上整行高亮会脏。
        Box(
          modifier =
            Modifier.size(7.dp)
              .clip(CircleShape)
              .background(
                if (index == selected) LotusPalette.Ink else Color.Transparent
              ),
        )
      }
    }
  }
}

/**
 * 已选图片的缩略片（玻璃托着）。解码放到 IO，失败就退回一枚占位图标 ——
 * 绝不让一张读不出来的图把输入条顶崩。
 */
@Composable
fun AttachmentChip(uri: Uri, onRemove: () -> Unit, modifier: Modifier = Modifier) {
  val bitmap = rememberBitmap(uri)
  val shape = RoundedCornerShape(18.dp)
  Box(
    modifier =
      modifier
        .size(64.dp)
        .glassPanel(shape = shape, strength = 0.5f, rimAlpha = 0.5f, elevation = 8.dp)
        .padding(6.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = "已选图片",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)),
      )
    } else {
      LotusIcon(
        glyph = LotusGlyph.Add,
        tint = LotusPalette.InkMuted,
        modifier = Modifier.size(20.dp),
      )
    }

    // 右上角的"删掉"。**不垫玻璃片、不描边** —— 它已经长在输入条那块玻璃上了，再画一圈边
    // 就是用户点名要删的"同一块玻璃里分化出来的第二块玻璃"。静止只剩一枚叉，按下才浮出折射。
    val removeP = rowPress(CircleShape, scale = 1.15f)
    Box(
      modifier =
        Modifier.align(Alignment.TopEnd)
          .size(22.dp)
          .then(removeP.modifier)
          .clickable(
            interactionSource = removeP.interaction,
            indication = null,
          ) { onRemove() },
      contentAlignment = Alignment.Center,
    ) {
      LotusIcon(
        glyph = LotusGlyph.Close,
        tint = LotusPalette.Ink,
        modifier = Modifier.size(12.dp).semantics { contentDescription = "移除图片" },
      )
    }
  }
}

@Composable
private fun rememberBitmap(uri: Uri): ImageBitmap? {
  val context = LocalContext.current
  var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(uri) {
    bitmap =
      withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
              BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
          }
          .getOrNull()
      }
  }
  return bitmap
}

/** 模型面板里的一组：一个提供方 + 它下面的模型。 */
data class ModelGroup(val id: String, val name: String, val models: List<String>)

/**
 * 输入条上方那**一块**面板：上半选模型，下半选思考等级。
 *
 * 用户口径：「把闪电标和这个合并吧，这个也能直观体现出思考等级」+「上方模型选择，下方思考等级」。
 * 所以这两件事不再各弹一个面板 —— 一个入口、一块玻璃、上下两段。
 *
 *  - 模型段：两级（供应商可伸缩 → 它的模型）。供应商多的时候面板不会撑爆。
 *  - 思考段：只列**当前模型**支持的档位；[hint] 用来写清"这几档是怎么来的"
 *    （上游没有公布能力表，所以不能装作知道）。
 */
@Composable
fun GlassComposerPanel(
  groups: List<ModelGroup>,
  selectedProviderId: String?,
  selectedModelId: String,
  thinking: List<ThinkingOption>,
  thinkingIndex: Int,
  modifier: Modifier = Modifier,
  width: Dp = 276.dp,
  hint: String? = null,
  onPickModel: (providerId: String, modelId: String) -> Unit,
  onPickThinking: (Int) -> Unit,
) {
  val shape = RoundedCornerShape(24.dp)
  var expanded by remember(groups) {
    mutableStateOf(
      groups.firstOrNull { it.id == selectedProviderId }?.id ?: groups.firstOrNull()?.id
    )
  }

  Column(
    modifier =
      modifier
        .width(width)
        .heightIn(max = 470.dp)
        .floatGlass(shape = shape, strength = 0.55f, elevation = 12.dp)
        // 吃掉面板空白处的点击，别让它穿到底下的遮罩上把面板关掉。
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        .padding(vertical = 8.dp),
  ) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      PanelCaption("模型")

      groups.forEach { group ->
        val open = expanded == group.id
        val mine = group.id == selectedProviderId

        val groupP = rowPress(RoundedCornerShape(14.dp))
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .then(groupP.modifier)
              .clickable(
                interactionSource = groupP.interaction,
                indication = null,
              ) { expanded = if (open) null else group.id }
              .height(46.dp)
              .padding(horizontal = 18.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = group.name,
            color = LotusPalette.Ink,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = if (mine) "使用中 · ${group.models.size}" else "${group.models.size} 个模型",
            color = LotusPalette.InkFaint,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 6.dp),
          )
          val rotation by
            animateFloatAsState(
              targetValue = if (open) 90f else 0f,
              animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
              label = "groupChevron",
            )
          LotusIcon(
            glyph = LotusGlyph.Chevron,
            tint = LotusPalette.InkFaint,
            modifier = Modifier.size(13.dp).graphicsLayer { rotationZ = rotation },
          )
        }

        AnimatedVisibility(
          visible = open,
          enter = expandVertically(tween(180)) + fadeIn(tween(150)),
          exit = shrinkVertically(tween(160)) + fadeOut(tween(110)),
        ) {
          Column {
            group.models.forEach { id ->
              val picked = mine && id == selectedModelId
              val modelP = rowPress(RoundedCornerShape(12.dp))
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .then(modelP.modifier)
                    .clickable(
                      interactionSource = modelP.interaction,
                      indication = null,
                    ) { onPickModel(group.id, id) }
                    .height(42.dp)
                    .padding(start = 30.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = id,
                  color = if (picked) LotusPalette.Ink else LotusPalette.Ink.copy(alpha = 0.78f),
                  fontSize = 14.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Box(
                  modifier =
                    Modifier.size(7.dp)
                      .clip(CircleShape)
                      .background(if (picked) LotusPalette.Ink else Color.Transparent)
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(8.dp))
      Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(0.7.dp).background(LotusPalette.Hairline))
      Spacer(Modifier.height(8.dp))

      PanelCaption("思考")
      thinking.forEachIndexed { index, option ->
        val picked = index == thinkingIndex
        val tierP = rowPress(RoundedCornerShape(12.dp))
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .then(tierP.modifier)
              .clickable(
                interactionSource = tierP.interaction,
                indication = null,
              ) { onPickThinking(index) }
              .height(42.dp)
              .padding(horizontal = 18.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = option.label,
            color = if (picked) LotusPalette.Ink else LotusPalette.Ink.copy(alpha = 0.78f),
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
          )
          Box(
            modifier =
              Modifier.size(7.dp)
                .clip(CircleShape)
                .background(if (picked) LotusPalette.Ink else Color.Transparent)
          )
        }
      }

      hint?.let {
        Text(
          text = it,
          color = LotusPalette.InkFaint,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun PanelCaption(text: String) {
  Text(
    text = text,
    color = LotusPalette.InkMuted,
    fontSize = 12.sp,
    modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 2.dp),
  )
}
