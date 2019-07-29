package com.ijoic.data.source.pool

import com.ijoic.data.source.Connection
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.junit.Test
import java.time.Duration

/**
 * Connection pool test
 */
class ConnectionPoolTest {
  @Test
  fun testRequestConnectionsSimple() {
    val connection: Connection = mock()
    val pool = ConnectionPool(
      PoolConfig(
        limitPrepareInterval = Duration.ofSeconds(10)
      )
    ) { connection }

    pool.requestConnections(1)
    verify(connection, times(1)).prepare(any())

    pool.requestConnections(1)
    verify(connection, times(1)).prepare(any())
  }
}