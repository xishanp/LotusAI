package ai.lotus.app.data

/** 应用里出现的仓库链接只从这里取，避免原项目与签名分发仓库互相串错。 */
object ProjectLinks {
  /** Lotus AI 的签名分发与 Release 仓库。 */
  const val LotusDistributionRepo = "xishanp/LotusAI"
  const val LotusDistributionUrl = "https://github.com/xishanp/LotusAI"

  /** LinuxHub 的原项目仓库。 */
  const val LinuxHubSourceRepo = "xishanp/Linuxhub"
  const val LinuxHubSourceUrl = "https://github.com/xishanp/Linuxhub"

  const val AuthorGitHub = "xishanp"
  const val AuthorGitHubUrl = "https://github.com/xishanp"
}
