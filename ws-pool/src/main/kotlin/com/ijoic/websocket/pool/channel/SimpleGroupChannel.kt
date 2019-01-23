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
package com.ijoic.websocket.pool.channel

import com.ijoic.websocket.pool.connect.Connection
import com.ijoic.websocket.pool.connect.pool.ConnectionPool
import com.ijoic.websocket.pool.handler.MessageHandler

/**
 * Simple group channel
 *
 * @author verstsiu created at 2019-01-22 11:37
 */
class SimpleGroupChannel(
  private val pool: ConnectionPool,
  private val handler: MessageHandler
) : GroupChannel {

  private val cachedMessages = mutableListOf<String>()

  private val connectionListener = object: ConnectionPool.ConnectionChangedListener {
    override fun onConnectionActive(connection: Connection) {
      pool.removeConnectionChangeListener(this)
      connection.addMessageHandler(handler)
      val msgItems = cachedMessages.toMutableList()
      cachedMessages.clear()

      if (!msgItems.isEmpty()) {
        msgItems.forEach { connection.sendText(it) }
      }
    }
  }

  override fun add(message: String) {
    val connection = pool.getMinLoadActiveConnection()

    if (connection == null) {
      cachedMessages.add(message)
      pool.addConnectionChangeListener(connectionListener)
      pool.requestConnections(1)
    } else {
      connection.addMessageHandler(handler)
      connection.sendText(message)
    }
  }

  override fun remove(message: String) {
    cachedMessages.remove(message)
  }

}