package ai.lotus.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolLoopTest {
  @Test
  fun keepsExecutingBeyondFormerRoundAndCallCaps() = runBlocking {
    val batchSize = 10
    val batches = 6 // 60 calls, beyond the former 8-round / 24-call limits.
    var executedCalls = 0
    var batchIndex = 0

    driveToolLoop((0 until batchSize).toList()) { calls ->
      assertEquals(batchSize, calls.size)
      executedCalls += calls.size
      batchIndex += 1
      if (batchIndex < batches) (0 until batchSize).toList() else emptyList()
    }

    assertEquals(60, executedCalls)
    assertEquals(batches, batchIndex)
  }
}
