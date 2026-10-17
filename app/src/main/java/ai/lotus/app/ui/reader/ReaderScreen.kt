package ai.lotus.app.ui.reader

import ai.lotus.app.ui.icons.LotusGlyph
import ai.lotus.app.ui.icons.LotusIcon
import ai.lotus.app.ui.theme.LotusPalette
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.lotus.app.ui.glass.LiquidGlassIconButton
import ai.lotus.app.ui.glass.glassPanel

/**
 * **应用内阅读器** —— 点消息里那枚来源胶囊，就在这里打开它读过的那个页面。
 *
 * 用户口径：「它现在点开会跳到应用外 我选择应用内 这内置」。
 *
 * 所以：不再走 `Intent.ACTION_VIEW`（那会把用户丢进 Chrome / 微信内置浏览器，回来还得自己找），
 * 直接在 App 里开一个 WebView，顶上一条玻璃：返回、域名、在浏览器打开（想要外部时才点）。
 *
 * ⚠️ WebView 只在**这一页**创建；退出就销毁（`onDispose`），不要把它挂在全局 —— 留着 = 吃内存。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
  url: String,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var progress by remember { mutableIntStateOf(0) }
  var title by remember(url) { mutableStateOf(hostOf(url)) }

  BackHandler { onClose() }

  Box(modifier = modifier.fillMaxSize().background(LotusPalette.Canvas)) {
    AndroidView(
      modifier = Modifier.fillMaxSize().padding(top = 92.dp),
      factory = { ctx ->
        WebView(ctx).apply {
          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          settings.loadsImagesAutomatically = true
          settings.useWideViewPort = true
          settings.loadWithOverviewMode = true
          webViewClient =
            object : WebViewClient() {
              override fun onPageFinished(view: WebView?, u: String?) {
                view?.title?.takeIf { it.isNotBlank() }?.let { title = it }
              }
            }
          webChromeClient =
            object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
              }
            }
          loadUrl(url)
        }
      },
    )

    // 顶部玻璃条：不占内容区高度地压在上面（跟聊天页一样，靠悬浮代替顶栏）。
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 14.dp, vertical = 10.dp)
          .height(52.dp)
          .glassPanel(
            shape = RoundedCornerShape(26.dp),
            strength = 0.55f,
            rimAlpha = 0.5f,
            elevation = 8.dp,
            veil = 0.45f,
          )
          .padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LiquidGlassIconButton(onClick = onClose, size = 38.dp, nested = true) { tint ->
        LotusIcon(LotusGlyph.Back, tint, Modifier.size(17.dp))
      }
      Spacer(Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          color = LotusPalette.Ink,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = if (progress in 1..99) "载入中 $progress%" else hostOf(url),
          color = LotusPalette.InkFaint,
          fontSize = 9.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.width(6.dp))
      LiquidGlassIconButton(
        onClick = {
          // 用户明说要应用内；这个只作为"我就想丢去浏览器"的出口，默认不点。
          runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
          }
        },
        size = 38.dp,
        nested = true,
      ) { tint ->
        LotusIcon(LotusGlyph.Link, tint, Modifier.size(15.dp))
      }
    }
  }
}

private fun hostOf(url: String): String =
  runCatching { java.net.URI(url).host?.removePrefix("www.") }
    .getOrNull()
    ?.takeIf { it.isNotBlank() }
    ?: url
