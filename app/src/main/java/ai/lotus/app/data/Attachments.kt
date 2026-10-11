package ai.lotus.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/**
 * 聊天里发出去的图片。
 *
 * 用户口径：「现在我发图片，他能看了吗？因为我单独直接发一张图片，发不出去，是啥情况，
 * 狂按都没用」—— 之前 `+` 只是选图 + 摆一个缩略片，**图根本没进请求**，等于按了没反应。
 *
 * 现在这条路是通的：
 *  1. 选图 → [import]：缩到长边 ≤ [MaxDim]、存成 jpg 丢进 `filesDir/images/`（相册给的
 *     content:// 只是一次性授权，重启就失效，必须自己拷一份）。
 *  2. 发送 → [dataUrl]：读成 `data:image/jpeg;base64,…`，塞进请求的 `image_url`。
 *
 * ⚠️ 只发**本地文件**，不要发远端 URL；也别把图片写进 conversations.json（会把那个文件撑爆）。
 */
object Attachments {
  private const val DirName = "images"
  private const val MaxDim = 1280
  private const val JpegQuality = 85

  private fun dir(context: Context) = File(context.filesDir, DirName).apply { mkdirs() }

  /** 相册 → 私有目录。返回落盘后的绝对路径；失败 = null。 */
  fun import(context: Context, uri: Uri): String? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
          BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MaxDim || bounds.outHeight / (sample * 2) >= MaxDim) {
          sample *= 2
        }
        val decoded =
          context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
          } ?: return@runCatching null

        val scale = MaxDim.toFloat() / maxOf(decoded.width, decoded.height)
        val out =
          if (scale < 1f) {
            Bitmap.createScaledBitmap(
              decoded,
              (decoded.width * scale).toInt().coerceAtLeast(1),
              (decoded.height * scale).toInt().coerceAtLeast(1),
              true,
            )
          } else {
            decoded
          }
        val file = File(dir(context), "img_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, JpegQuality, it) }
        if (out !== decoded) out.recycle()
        decoded.recycle()
        file.absolutePath
      }
      .getOrNull()

  /** 本地图片路径 → `data:image/jpeg;base64,…`（请求里 image_url 要的就是它）。 */
  fun dataUrl(path: String): String? =
    runCatching {
        val bytes = File(path).readBytes()
        if (bytes.isEmpty()) null
        else "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
      }
      .getOrNull()
}

/**
 * 非图片附件：视频 / 文档。
 *
 * 用户口径：「那个加号那里，除了能弄图片之外，视频和文件也弄不了呀」。
 *
 * ⚠️ 这里**不搬文件**：用户随手选个几百 MB 的视频，拷一份进私有目录能把手机塞爆，
 * 而且我们的聊天接口本来也发不了视频。所以只拿**名字 + 大小**，纯文本文件才顺手把内容读出来
 * （那才是模型真能用的东西）。
 */
data class DocRef(val name: String, val bytes: Long, val text: String?)

private const val MaxTextBytes = 96 * 1024

/** 能当纯文本读进来的后缀。读不了的一律只带文件名。 */
private val TextExt =
  setOf(
    "txt", "md", "markdown", "json", "csv", "tsv", "log", "yml", "yaml", "xml", "html", "htm",
    "kt", "kts", "java", "py", "js", "ts", "tsx", "c", "cpp", "h", "hpp", "sh", "zsh", "gradle",
    "properties", "toml", "ini", "sql", "conf", "env",
  )

private fun displayName(context: Context, uri: Uri): String {
  var n = ""
  runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
      val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
      if (i >= 0 && c.moveToFirst()) n = c.getString(i).orEmpty()
    }
  }
  if (n.isBlank()) n = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
  return n.ifBlank { "附件" }
}

private fun byteSize(context: Context, uri: Uri): Long {
  var size = -1L
  runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
      val i = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
      if (i >= 0 && c.moveToFirst()) size = c.getLong(i)
    }
  }
  if (size >= 0) return size
  return runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L }
    .getOrDefault(-1L)
}

/** 描述一个选中的视频 / 文档。拿不到就返回 null。 */
fun describeDoc(context: Context, uri: Uri): DocRef? =
  runCatching {
      val name = displayName(context, uri)
      val size = byteSize(context, uri)
      val ext = name.substringAfterLast('.', "").lowercase()
      val body =
        if (ext in TextExt && size in 1..MaxTextBytes.toLong()) {
          context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
          }
        } else {
          null
        }
      DocRef(name, size, body)
    }
    .getOrNull()

/** 人类看得懂的大小。 */
fun humanBytes(bytes: Long): String =
  when {
    bytes < 0 -> "大小未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
  }
