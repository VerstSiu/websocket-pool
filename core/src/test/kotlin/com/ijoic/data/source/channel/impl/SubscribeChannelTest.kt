package com.ijoic.data.source.channel.impl

import com.ijoic.data.source.TestConnection
import com.ijoic.data.source.context.impl.TestExecutorContext
import com.ijoic.data.source.handler.MessageHandler
import com.ijoic.data.source.pool.ConnectionPool
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito

/**
 * Subscribe channel test
 */
class SubscribeChannelTest {
  @Test
  fun testSimple() {
    val context = TestExecutorContext()
    val connection = TestConnection()
    val pool = ConnectionPool { connection }
    val handler = object: MessageHandler() {
      override fun dispatchMessage(receiveTime: Long, message: Any): Boolean {
        return true
      }
    }

    val channel = SubscribeChannel<String, String>(pool, handler, { _, msg -> msg }, { _, msgItems -> msgItems.joinToString("") }, context = context)
    val mockConnection = connection.mockConnection

    channel.add("A")

    context.elaspse(200L)
    connection.notifyConnectionComplete()
    Mockito.verify(mockConnection, Mockito.times(1)).send(Matchers.eq("A"))

    channel.add("B")
    channel.add("C")
    channel.add("D")

    context.elaspse(200L)
    Mockito.verify(mockConnection, Mockito.times(1)).send(Matchers.eq("BCD"))
  }
}