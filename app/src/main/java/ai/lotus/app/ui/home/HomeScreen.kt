package ai.lotus.app.ui.home

import ai.lotus.app.R
import ai.lotus.app.data.DefaultAssistantName
import ai.lotus.app.ui.theme.LotusPalette
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 首页（空状态）。
 *
 * 版式：立绘 + 问候语整体落在画面中部略偏上，下方留白直接交给输入条。
 *
 * ⚠️ 曾经在这里排过 5 片弧形引导语（"写文案 / 创意灵感 / …"）。用户看过真机后明确要求
 * **全部删掉**（"根本做不好看"），所以这里不再有任何 chip / 引导语 / 手绘图标。
 * 想加回任何形态的引导语之前先问清楚 —— 这一块已经被否过两轮了。
 *
 * @param bottomInset 底部固定层（输入条 + 底栏）占掉的高度，由外层 [ai.lotus.app.ui.LotusApp] 传入。
 * @param onArtBounds 把立绘的布局矩形报出去（root 坐标）。启动页用它做"长大后的落点"，
 *   让启动页收尾和首页**逐像素对齐**，淡出时看不出换页。
 */
@Composable
fun HomeScreen(
  modifier: Modifier = Modifier,
  bottomInset: Dp,
  /**
   * 助手显示名。**这里必须跟着改名走** —— 用户口径：「我尝试给 AI 换名字，换了之后它
   * 每次介绍还是原来的那个」：那句"你好，我是……"原来是写死的，改名根本传不到这里。
   */
  assistantName: String = DefaultAssistantName,
  onOpenHistory: () -> Unit = {},
  onArtBounds: (Rect) -> Unit = {},
) {

  Column(modifier = modifier.fillMaxSize()) {
    // ⚠️ 这里**没有顶栏**了（用户口径：「最上方的顶栏去掉，液态玻璃就是为了放大空间代替顶栏」）。
    //    历史钮 / 更多钮现在由 LotusApp 的悬浮玻璃层画，压在本页内容之上，不占布局。
    //    这个 60dp 空白只是为了保住立绘的既有位置 —— 立绘的上下重心是用户逐轮调出来的，
    //    删掉这 60dp 它会整体上移 ~29dp，等于把调过的版式又打回去。
    Spacer(Modifier.height(60.dp))

    // 上下两段留白按 1 : 1.05 分 —— 立绘块落在中部略偏上（用户："人物往中间挪一点"）。
    // 用权重而不是写死 dp：任何屏高上都保持同样的视觉重心。
    Spacer(Modifier.weight(1f))

    // 立绘：白底 PNG，和纯白画布无缝相接。
    Image(
      painter = androidx.compose.ui.res.painterResource(R.drawable.lotus_mascot),
      contentDescription = null,
      modifier =
        Modifier.fillMaxWidth(0.74f)
          .align(Alignment.CenterHorizontally)
          .onGloballyPositioned { onArtBounds(it.boundsInRoot()) },
    )

    Spacer(Modifier.height(2.dp))

    Text(
      text = "你好，我是$assistantName",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
      color = LotusPalette.Ink,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "很高兴认识你，随时为你解答问题",
      style = MaterialTheme.typography.bodyMedium,
      color = LotusPalette.InkMuted,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    Spacer(Modifier.weight(1.05f))

    // 给底部固定层（输入条 + 底栏）留位。它们不在这个 Column 里，见 LotusApp。
    Spacer(Modifier.height(bottomInset))
  }
}
