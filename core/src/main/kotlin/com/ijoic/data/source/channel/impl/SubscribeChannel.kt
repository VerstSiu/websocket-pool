/*
 *
 *  Copyright(c) 2019 VerstSiu
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.ijoic.data.source.channel.impl

import com.ijoic.data.source.Connection
import com.ijoic.data.source.handler.MessageHandler
import com.ijoic.data.source.pool.ConnectionPool
import com.ijoic.data.source.send

/**
 * Subscribe channel
 *
 * @author verstsiu created at 2019-01-24 18:42
 */
class SubscribeChannel<T>(
  private val pool: ConnectionPool,
  private val handler: MessageHandler): BaseChannel() {

  private val activeMessages = mutableListOf<T>()
  private var bindConnection: Connection? = null

  private val editLock = Object()

  private val connectionListener = object: ConnectionPool.ConnectionChangedListener {
    override fun onConnectionActive(connection: Connection) {
      synchronized(editLock) {
        bindConnection = connection
        connection.addMessageHandler(handler)

        activeMessages.forEach {
          if (it != null) {
            connection.send(it, onError)
          }
        }
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

  /**
   * Add [subscribe] message
   */
  fun add(subscribe: T) {
    synchronized(editLock) {
      if (subscribe == null || activeMessages.contains(subscribe)) {
        return
      }
      activeMessages.add(subscribe)

      if (sendMessageWithExistConnection(subscribe)) {
        return
      }
      val connection = pool.getActiveConnections(1).firstOrNull()

      if (connection == null) {
        pool.addConnectionChangeListener(connectionListener)
        pool.requestConnections(1)
      } else {
        bindConnection = connection
        connection.addMessageHandler(handler)

        activeMessages.forEach {
          if (it != null) {
            connection.send(it, onError)
          }
        }
      }
    }
  }

  /**
   * Remove [subscribe] message with [unsubscribe]
   */
  fun remove(subscribe: T, unsubscribe: T) {
    synchronized(editLock) {
      if (!activeMessages.contains(subscribe)) {
        return
      }
      activeMessages.remove(subscribe)
      val oldConnection = bindConnection

      if (oldConnection != null && oldConnection.isActive && unsubscribe != null) {
        oldConnection.send(unsubscribe)
      }
      if (activeMessages.isEmpty()) {
        pool.removeConnectionChangeListener(connectionListener)
        bindConnection?.removeMessageHandler(handler)
        bindConnection = null
      }
    }
  }

  private fun sendMessageWithExistConnection(message: Any): Boolean {
    val oldConnection = bindConnection

    if (oldConnection != null) {
      if (!oldConnection.isActive) {
        bindConnection = null
        oldConnection.removeMessageHandler(handler)
      } else {
        oldConnection.send(message, onError)
      }
    }
    return bindConnection != null
  }

  override fun release() {
    synchronized(editLock) {
      activeMessages.clear()
      pool.removeConnectionChangeListener(connectionListener)
      bindConnection?.removeMessageHandler(handler)
      bindConnection = null
    }
  }
}