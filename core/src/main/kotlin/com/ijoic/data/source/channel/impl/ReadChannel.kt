package com.ijoic.data.source.channel.impl

import com.ijoic.data.source.Connection
import com.ijoic.data.source.handler.MessageHandler
import com.ijoic.data.source.pool.ConnectionPool

/**
 * Read channel
 *
 * @author verstsiu created at 2019-07-05 21:31
 */
class ReadChannel(
  private val pool: ConnectionPool,
  private val handler: MessageHandler): BaseChannel() {

  private var bindConnection: Connection? = null
  private val editLock = Object()

  private val connectionListener by lazy {
    object: ConnectionPool.ConnectionChangedListener {
      override fun onConnectionActive(connection: Connection) {
        synchronized(editLock) {
          bindConnection = connection
          connection.addMessageHandler(handler)
        }
      }

      override fun onConnectionInactive(connection: Connection) {
        synchronized(editLock) {
          if (connection == bindConnection) {
            bindConnection = null
            connection.removeMessageHandler(handler)
          }
        }
      }
    }
  }

  init {
    prepareChannel()
  }

  private fun prepareChannel() {
    synchronized(editLock) {
      val connection = pool.getActiveConnections(1).firstOrNull()

      if (connection == null) {
        pool.addConnectionChangeListener(connectionListener)
        pool.requestConnections(1)
      } else {
        bindConnection = connection
        connection.addMessageHandler(handler)
      }
    }
  }

  override fun release() {
    synchronized(editLock) {
      pool.removeConnectionChangeListener(connectionListener)
      bindConnection?.removeMessageHandler(handler)
      bindConnection = null
    }
  }
}