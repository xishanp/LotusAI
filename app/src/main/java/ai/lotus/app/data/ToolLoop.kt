package ai.lotus.app.data

/**
 * 驱动一次用户请求内的多步工具循环。
 *
 * 这里刻意不接收“最大轮数/最大调用数”：模型请求多少批、每批多少项都执行，直到下一批为空。
 * 死循环的刹车交给上游停止返回工具、请求失败，以及协程取消，不靠客户端静默截断任务。
 */
internal suspend fun <T> driveToolLoop(
  initialCalls: List<T>,
  executeBatch: suspend (List<T>) -> List<T>,
) {
  var calls = initialCalls
  while (calls.isNotEmpty()) {
    calls = executeBatch(calls)
  }
}
