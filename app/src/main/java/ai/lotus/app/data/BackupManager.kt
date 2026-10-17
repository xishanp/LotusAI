package ai.lotus.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 加密备份的结果。文案直接可给界面显示。 */
sealed interface BackupResult {
  data class Ok(val files: Int, val bytes: Long) : BackupResult

  data class Err(val message: String) : BackupResult
}

@Serializable
private data class BackupManifest(
  val format: String = "lotus-ai-backup",
  val version: Int = 1,
  val createdAt: Long = 0L,
  val appVersion: String = "",
  val fileCount: Int = 0,
  val totalBytes: Long = 0L,
)

@Serializable
private data class PrefValue(
  val type: String,
  val text: String = "",
  val number: Double = 0.0,
  val strings: List<String> = emptyList(),
)

@Serializable
private data class PrefsBackup(
  val prefs: Map<String, Map<String, PrefValue>> = emptyMap(),
)

/**
 * 加密备份：`*.lotusbackup`。
 *
 * 磁盘格式：magic + version + iterations + salt + iv + AES-GCM(zip)。
 * 密码只存在用户输入里，不落盘、不进日志。AES-GCM 同时保证保密性与完整性；
 * 导入要么完整成功，要么回滚，绝不把半套数据留在本机。
 */
object BackupManager {
  private const val Magic = "LOTUSBAK"
  private const val FormatVersion = 1
  private const val Iterations = 210_000
  private const val SaltBytes = 16
  private const val IvBytes = 12
  private const val TagBits = 128
  private const val MaxArchiveBytes = 200L * 1024L * 1024L
  private const val MaxEntries = 20_000
  private val Aad = "LotusAI|backup|v1".toByteArray(Charsets.UTF_8)
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

  suspend fun export(context: Context, uri: Uri, password: String): BackupResult =
    withContext(Dispatchers.IO) {
      if (password.length < 8) return@withContext BackupResult.Err("密码至少 8 位")
      val stage = File(context.cacheDir, "lotus-backup-${UUID.randomUUID()}")
      val plain = File(stage, "plain.zip")
      stage.mkdirs()
      try {
        val manifest = writePlainArchive(context, plain)
        encryptFile(plain, context, uri, password)
        BackupResult.Ok(manifest.fileCount, manifest.totalBytes)
      } catch (t: Throwable) {
        BackupResult.Err(backupError(t, "导出失败"))
      } finally {
        stage.deleteRecursively()
      }
    }

  suspend fun import(context: Context, uri: Uri, password: String): BackupResult =
    withContext(Dispatchers.IO) {
      if (password.length < 8) return@withContext BackupResult.Err("密码至少 8 位")
      val stage = File(context.cacheDir, "lotus-restore-${UUID.randomUUID()}")
      val plain = File(stage, "plain.zip")
      val unpacked = File(stage, "unpacked")
      val stagedFiles = File(unpacked, "files")
      stage.mkdirs()
      try {
        decryptToFile(context, uri, password, plain)
        val manifest = unpackArchive(plain, unpacked)
        validateStaged(unpacked, stagedFiles, manifest)
        val prefs = readPrefsBackup(File(unpacked, "prefs.json"))
        installAtomically(context, stagedFiles, prefs)
        BackupResult.Ok(manifest.fileCount, manifest.totalBytes)
      } catch (t: Throwable) {
        val message =
          when {
            t is AEADBadTagException -> "密码错误，或备份文件已损坏"
            t.message?.contains("Tag mismatch", ignoreCase = true) == true ->
              "密码错误，或备份文件已损坏"
            else -> backupError(t, "导入失败")
          }
        BackupResult.Err(message)
      } finally {
        stage.deleteRecursively()
      }
    }

  private fun writePlainArchive(context: Context, target: File): BackupManifest {
    val allFiles = context.filesDir.walkTopDown().filter { it.isFile }.toList()
    val total = allFiles.sumOf { it.length() }
    if (total > MaxArchiveBytes) error("数据超过 200 MB，暂时不支持这么大的备份")
    if (allFiles.size > MaxEntries) error("文件数量超过限制")
    val manifest =
      BackupManifest(
        createdAt = System.currentTimeMillis(),
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
        fileCount = allFiles.size,
        totalBytes = total,
      )

    ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
      zip.putNextEntry(ZipEntry("manifest.json"))
      zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
      zip.closeEntry()

      allFiles.forEach { file ->
        val rel = file.relativeTo(context.filesDir).invariantSeparatorsPath
        zip.putNextEntry(ZipEntry("files/$rel"))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
      }

      zip.putNextEntry(ZipEntry("prefs.json"))
      zip.write(json.encodeToString(readPrefs(context)).toByteArray(Charsets.UTF_8))
      zip.closeEntry()
    }
    return manifest
  }

  private fun readPrefs(context: Context): PrefsBackup {
    val names = listOf("app_prefs", "selection")
    val out = linkedMapOf<String, Map<String, PrefValue>>()
    names.forEach { name ->
      val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
      out[name] = prefs.all.mapValues { (_, value) -> value.toBackupPref() }
    }
    return PrefsBackup(out)
  }

  private fun Any?.toBackupPref(): PrefValue =
    when (this) {
      is Boolean -> PrefValue("boolean", text = this.toString())
      is Int -> PrefValue("int", number = this.toDouble())
      is Long -> PrefValue("long", number = this.toDouble())
      is Float -> PrefValue("float", number = this.toDouble())
      is String -> PrefValue("string", text = this)
      is Set<*> -> PrefValue("string_set", strings = this.mapNotNull { it?.toString() }.sorted())
      null -> PrefValue("null")
      else -> PrefValue("string", text = this.toString())
    }

  private fun readPrefsBackup(file: File): PrefsBackup {
    if (!file.isFile) return PrefsBackup()
    return json.decodeFromString<PrefsBackup>(file.readText())
  }

  private fun encryptFile(plain: File, context: Context, uri: Uri, password: String) {
    val salt = ByteArray(SaltBytes).also { SecureRandom().nextBytes(it) }
    val iv = ByteArray(IvBytes).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TagBits, iv))
    cipher.updateAAD(Aad)

    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选文件")
    DataOutputStream(BufferedOutputStream(output)).use { out ->
      out.write(Magic.toByteArray(Charsets.US_ASCII))
      out.writeInt(FormatVersion)
      out.writeInt(Iterations)
      out.writeInt(salt.size)
      out.writeInt(iv.size)
      out.write(salt)
      out.write(iv)
      FileInputStream(plain).use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
          val n = input.read(buf)
          if (n < 0) break
          if (n > 0) out.write(cipher.update(buf, 0, n))
        }
      }
      out.write(cipher.doFinal())
    }
  }

  private fun decryptToFile(context: Context, uri: Uri, password: String, target: File) {
    val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
    DataInputStream(BufferedInputStream(input)).use { src ->
      val magic = ByteArray(Magic.length)
      src.readFully(magic)
      if (String(magic, Charsets.US_ASCII) != Magic) error("不是 Lotus 备份文件")
      val version = src.readInt()
      if (version != FormatVersion) error("备份版本 $version 暂不支持")
      val iterations = src.readInt()
      if (iterations !in 10_000..1_000_000) error("备份参数异常")
      val salt = ByteArray(src.readInt().also { if (it !in 8..64) error("盐长度异常") })
      val iv = ByteArray(src.readInt().also { if (it !in 12..32) error("IV 长度异常") })
      src.readFully(salt)
      src.readFully(iv)

      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(
        Cipher.DECRYPT_MODE,
        deriveKey(password, salt, iterations),
        GCMParameterSpec(TagBits, iv),
      )
      cipher.updateAAD(Aad)

      FileOutputStream(target).use { out ->
        val buf = ByteArray(64 * 1024)
        while (true) {
          val n = src.read(buf)
          if (n < 0) break
          if (target.length() > MaxArchiveBytes) error("备份解压后超过 200 MB")
          if (n > 0) out.write(cipher.update(buf, 0, n))
        }
        out.write(cipher.doFinal())
      }
    }
  }

  private fun deriveKey(password: String, salt: ByteArray, iterations: Int = Iterations): SecretKeySpec {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
    return try {
      val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
      SecretKeySpec(raw, "AES")
    } finally {
      spec.clearPassword()
    }
  }

  private fun unpackArchive(zipFile: File, targetDir: File): BackupManifest {
    targetDir.mkdirs()
    val root = targetDir.canonicalFile
    var total = 0L
    var entries = 0
    var manifest: BackupManifest? = null
    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entries++
        if (entries > MaxEntries) error("备份文件数量异常")
        val name = entry.name.replace('\\', '/')
        if (name.startsWith('/') || name.split('/').any { it == ".." }) error("备份路径不安全")
        val out = File(targetDir, name)
        val canonical = out.canonicalFile
        if (!canonical.path.startsWith(root.path + File.separator)) error("备份路径不安全")
        if (!entry.isDirectory) {
          canonical.parentFile?.mkdirs()
          FileOutputStream(canonical).use { output ->
            val buf = ByteArray(64 * 1024)
            while (true) {
              val n = zip.read(buf)
              if (n < 0) break
              total += n
              if (total > MaxArchiveBytes) error("备份解压后超过 200 MB")
              if (n > 0) output.write(buf, 0, n)
            }
          }
        }
        zip.closeEntry()
      }
    }
    val manifestFile = File(targetDir, "manifest.json")
    if (manifestFile.isFile) {
      manifest = json.decodeFromString<BackupManifest>(manifestFile.readText())
    }
    return manifest ?: error("备份缺少 manifest")
  }

  private fun validateStaged(unpacked: File, stagedFiles: File, manifest: BackupManifest) {
    if (manifest.format != "lotus-ai-backup" || manifest.version != FormatVersion) {
      error("不是兼容的 Lotus 备份")
    }
    if (!stagedFiles.isDirectory) error("备份里没有应用数据")
    val requiredAny = listOf("providers.json", "conversations.json", "capabilities.json")
    if (requiredAny.none { File(stagedFiles, it).isFile }) error("备份数据不完整")
    // 先解析一次核心 JSON，坏包不进入安装阶段。
    File(stagedFiles, "providers.json").takeIf { it.isFile }?.let {
      runCatching { json.parseToJsonElement(it.readText()) }.getOrElse { error("模型配置损坏") }
    }
    File(stagedFiles, "conversations.json").takeIf { it.isFile }?.let {
      runCatching { json.parseToJsonElement(it.readText()) }.getOrElse { error("会话数据损坏") }
    }
    File(stagedFiles, "capabilities.json").takeIf { it.isFile }?.let {
      runCatching { json.parseToJsonElement(it.readText()) }.getOrElse { error("能力配置损坏") }
    }
  }

  private fun installAtomically(
    context: Context,
    stagedFiles: File,
    importedPrefs: PrefsBackup,
  ) {
    val live = context.filesDir
    val rollback = File(context.cacheDir, "lotus-rollback-${UUID.randomUUID()}")
    rollback.mkdirs()
    val moved = mutableListOf<Pair<File, File>>()
    val currentPrefs = snapshotPrefs(context)
    var installStarted = false
    var restoredAll = true
    try {
      live.listFiles()?.forEach { child ->
        val dest = File(rollback, child.name)
        if (!child.renameTo(dest)) error("无法准备回滚：${child.name}")
        moved += child to dest
      }
      installStarted = true
      stagedFiles.listFiles()?.forEach { child -> copyRecursively(child, File(live, child.name)) }
      applyPrefs(context, importedPrefs)
    } catch (t: Throwable) {
      if (installStarted) live.listFiles()?.forEach { it.deleteRecursively() }
      moved.forEach { (original, saved) ->
        if (!saved.renameTo(original)) restoredAll = false
      }
      if (runCatching { restorePrefs(context, currentPrefs) }.isFailure) restoredAll = false
      throw t
    } finally {
      // 回滚全部成功才删快照；否则把原文件留在 cache 里，避免二次破坏。
      if (restoredAll) rollback.deleteRecursively()
    }
  }

  private fun copyRecursively(src: File, dst: File) {
    if (src.isDirectory) {
      dst.mkdirs()
      src.listFiles()?.forEach { copyRecursively(it, File(dst, it.name)) }
    } else {
      dst.parentFile?.mkdirs()
      FileInputStream(src).use { input -> FileOutputStream(dst).use { output -> input.copyTo(output) } }
      dst.setLastModified(src.lastModified())
    }
  }

  private fun snapshotPrefs(context: Context): Map<String, Map<String, Any?>> =
    listOf("app_prefs", "selection").associateWith { name ->
      context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap()
    }

  private fun applyPrefs(context: Context, backup: PrefsBackup) {
    backup.prefs.forEach { (name, values) ->
      val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
      val preserveOnboarding =
        if (name == "app_prefs") prefs.getBoolean("onboardingCompleted", true) else null
      val editor = prefs.edit().clear()
      values.forEach { (key, value) -> value.putInto(editor, key) }
      if (name == "app_prefs" && "onboardingCompleted" !in values) {
        editor.putBoolean("onboardingCompleted", preserveOnboarding ?: true)
      }
      if (!editor.commit()) error("保存设置失败")
    }
  }

  private fun restorePrefs(context: Context, snapshot: Map<String, Map<String, Any?>>) {
    snapshot.forEach { (name, all) ->
      val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
      all.forEach { (key, value) -> value.toBackupPref().putInto(editor, key) }
      editor.commit()
    }
  }

  private fun PrefValue.putInto(editor: SharedPreferences.Editor, key: String) {
    when (type) {
      "boolean" -> editor.putBoolean(key, text.toBoolean())
      "int" -> editor.putInt(key, number.toInt())
      "long" -> editor.putLong(key, number.toLong())
      "float" -> editor.putFloat(key, number.toFloat())
      "string" -> editor.putString(key, text)
      "string_set" -> editor.putStringSet(key, strings.toSet())
      else -> editor.remove(key)
    }
  }

  private fun backupError(t: Throwable, prefix: String): String =
    "$prefix：${(t.message ?: t::class.java.simpleName).take(140)}"
}
