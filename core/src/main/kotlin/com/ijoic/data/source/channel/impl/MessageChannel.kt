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
 * Message channel
 *
 * @author verstsiu created at 2019-01-24 15:26
 */
class MessageChannel<T>(
  private val pool: ConnectionPool,
  private val handler: MessageHandler): BaseChannel() {

  private val cachedMessages = mutableListOf<T>()
  private var bindConnection: Connection? = null

  private val editLock = Object()

  private val connectionListener = object: ConnectionPool.ConnectionChangedListener {
    override fun onConnectionActive(connection: Connection) {
      synchronized(editLock) {
        bindConnection = connection
        connection.addMessageHandler(handler)
        val msgItems = cachedMessages.toList()
        cachedMessages.clear()

        if (!msgItems.isEmpty()) {
          msgItems.forEach {
            if (it != null) {
              connection.send(it, onError)
            }
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
   * Send [message]
   */
  fun send(message: T) {
    synchronized(editLock) {
      if (message == null || sendMessageWithExistConnection(message)) {
        return
      }
      val connection = pool.getActiveConnections(1).firstOrNull()

      if (connection == null) {
        cachedMessages.add(message)
        pool.addConnectionChangeListener(connectionListener)
        pool.requestConnections(1)
      } else {
        bindConnection = connection
        connection.addMessageHandler(handler)
        connection.send(message, onError)
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
      cachedMessages.clear()
      pool.removeConnectionChangeListener(connectionListener)
      bindConnection?.removeMessageHandler(handler)
      bindConnection = null
    }
  }
}