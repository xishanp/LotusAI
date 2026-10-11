package ai.lotus.app.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 用户自己的聊天头像。
 *
 * 用户口径：「对话聊天那还得加一个上传用户头像」。
 *
 * 从相册选一张，**拷进私有目录**（`filesDir/user_avatar.png`）—— 不存 content:// 那种临时
 * 授权，重启 App 后就失效了。空 = 没设过，聊天页回落到那枚玻璃圆片 + 人像图标。
 */
object AvatarStore {
  private const val FileName = "user_avatar.png"

  private fun file(context: Context) = File(context.filesDir, FileName)

  /** 已设过的头像路径；没设过 = null。 */
  fun path(context: Context): String? = file(context).takeIf { it.isFile }?.absolutePath

  /** 把相册里选的图拷进私有目录（覆盖旧的）。返回是否成功。 */
  fun save(context: Context, uri: Uri): Boolean =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
          file(context).outputStream().use { output -> input.copyTo(output) }
        }
        file(context).isFile
      }
      .getOrElse { false }

  fun clear(context: Context) {
    file(context).delete()
  }
}
