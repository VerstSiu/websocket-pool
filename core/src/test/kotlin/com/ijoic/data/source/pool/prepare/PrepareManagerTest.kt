package com.ijoic.data.source.pool.prepare

import com.ijoic.data.source.context.impl.DefaultExecutorContext
import com.ijoic.data.source.pool.prepare.impl.LimitIntervalPrepareManager
import com.ijoic.data.source.pool.prepare.impl.LimitSizePrepareManager
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.junit.Test

/**
 * Prepare manager test
 */
class PrepareManagerTest {
  @Test
  fun testLimitSizeSimple() {
    val onPrepare: () -> Unit = mock()
    val manager: PrepareManager = LimitSizePrepareManager(10, onPrepare)

    manager.requestConnections(1)
    assert(manager.requestSize == 1)
    verify(onPrepare, times(1)).invoke()

    manager.requestConnections(1)
    assert(manager.requestSize == 1)
    verify(onPrepare, times(1)).invoke()

    manager.requestConnections(2)
    assert(manager.requestSize == 2)
    verify(onPrepare, times(2)).invoke()

    manager.requestConnections(1)
    assert(manager.requestSize == 2)
    verify(onPrepare, times(2)).invoke()
  }

  @Test
  fun testLimitIntervalSimple() {
    val onPrepare: () -> Unit = mock()
    val manager: PrepareManager = LimitIntervalPrepareManager(2000, onPrepare, DefaultExecutorContext)

    manager.requestConnections(1)
    assert(manager.requestSize == 1)
    verify(onPrepare, times(1)).invoke()

    manager.requestConnections(1)
    assert(manager.requestSize == 1)
    verify(onPrepare, times(1)).invoke()

    manager.requestConnections(2)
    assert(manager.requestSize == 2)
    verify(onPrepare, times(1)).invoke()

    manager.requestConnections(1)
    assert(manager.requestSize == 2)
    verify(onPrepare, times(1)).invoke()
  }
}