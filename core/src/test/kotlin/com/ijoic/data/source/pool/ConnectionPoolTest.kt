package com.ijoic.data.source.pool

import com.ijoic.data.source.Connection
import com.ijoic.data.source.ConnectionListener
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito.*
import java.time.Duration

/**
 * Connection pool test
 */
class ConnectionPoolTest {
  @Test
  fun testRequestConnectionsSimple() {
    val connection = mock(Connection::class.java)
    val pool = ConnectionPool(
      PoolConfig(
        limitPrepareInterval = Duration.ofSeconds(10)
      )
    ) { connection }

    pool.requestConnections(1)
    verify(connection, times(1)).prepare(Matchers.any(ConnectionListener::class.java))

    pool.requestConnections(1)
    verify(connection, times(1)).prepare(Matchers.any(ConnectionListener::class.java))
  }
}