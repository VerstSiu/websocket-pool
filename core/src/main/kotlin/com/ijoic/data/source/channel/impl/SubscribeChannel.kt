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
class SubscribeChannel<DATA, MSG>(
  private val pool: ConnectionPool,
  private val handler: MessageHandler,
  private val mapSubscribe: (Operation, DATA) -> MSG,
  private val mapSubscribeMerge: ((Operation, List<DATA>) -> MSG)? = null,
  private val mergeGroupSize: Int = -1): BaseChannel() {

  private val activeMessages = mutableListOf<DATA>()
  private var bindConnection: Connection? = null

  private val editLock = Object()

  private val connectionListener = object: ConnectionPool.ConnectionChangedListener {
    override fun onConnectionActive(connection: Connection) {
      synchronized(editLock) {
        bindConnection = connection
        connection.addMessageHandler(handler)
        sendSubscribe(connection, Operation.SUBSCRIBE, activeMessages)
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
   * Add [subscribe] data
   */
  fun add(subscribe: DATA, sendRepeat: Boolean = false) {
    val operation = Operation.SUBSCRIBE

    synchronized(editLock) {
      if (subscribe == null) {
        return
      }
      if (activeMessages.contains(subscribe)) {
        if (sendRepeat) {
          sendMessageWithExistConnection(mapSubscribe(operation, subscribe))
        }
        return
      }
      activeMessages.add(subscribe)

      if (sendMessageWithExistConnection(mapSubscribe(operation, subscribe))) {
        return
      }
      val connection = pool.getActiveConnections(1).firstOrNull()
      pool.addConnectionChangeListener(connectionListener)

      if (connection == null) {
        pool.requestConnections(1)
      } else {
        bindConnection = connection
        connection.addMessageHandler(handler)
        sendSubscribe(connection, Operation.SUBSCRIBE, activeMessages)
      }
    }
  }

  /**
   * Add [subscribe] data all
   */
  fun addAll(subscribe: List<DATA>, sendRepeat: Boolean = false) {
    val operation = Operation.SUBSCRIBE

    synchronized(editLock) {
      val addMessages = subscribe
        .toMutableList()
        .apply { removeAll(activeMessages) }

      if (addMessages.isEmpty()) {
        if (sendRepeat) {
          sendSubscribeWithExistConnection(operation, subscribe)
        }
        return
      }
      activeMessages.addAll(addMessages)

      if ((sendRepeat && sendSubscribeWithExistConnection(operation, subscribe))
        || (!sendRepeat && sendSubscribeWithExistConnection(operation, addMessages))) {
        return
      }
      val connection = pool.getActiveConnections(1).firstOrNull()
      pool.addConnectionChangeListener(connectionListener)

      if (connection == null) {
        pool.requestConnections(1)
      } else {
        bindConnection = connection
        connection.addMessageHandler(handler)
        sendSubscribe(connection, Operation.SUBSCRIBE, activeMessages)
      }
    }
  }

  /**
   * Remove [subscribe] data
   */
  fun remove(subscribe: DATA) {
    val operation = Operation.UNSUBSCRIBE

    synchronized(editLock) {
      if (!activeMessages.contains(subscribe)) {
        return
      }
      activeMessages.remove(subscribe)
      val oldConnection = bindConnection

      if (oldConnection != null && oldConnection.isActive) {
        val msg = mapSubscribe(operation, subscribe)

        if (msg != null) {
          oldConnection.send(msg, onError)
        }
      }
      if (activeMessages.isEmpty()) {
        pool.removeConnectionChangeListener(connectionListener)
        bindConnection?.removeMessageHandler(handler)
        bindConnection = null
      }
    }
  }

  /**
   * Remove [subscribe] data all
   */
  fun removeAll(subscribe: List<DATA>) {
    val operation = Operation.UNSUBSCRIBE

    synchronized(editLock) {
      val removeMessages = subscribe
        .toMutableList()
        .apply { retainAll(activeMessages) }

      if (removeMessages.isEmpty()) {
        return
      }
      activeMessages.removeAll(removeMessages)
      val oldConnection = bindConnection

      if (oldConnection != null && oldConnection.isActive) {
        sendSubscribe(oldConnection, operation, removeMessages)
      }
      if (activeMessages.isEmpty()) {
        pool.removeConnectionChangeListener(connectionListener)
        bindConnection?.removeMessageHandler(handler)
        bindConnection = null
      }
    }
  }

  private fun sendSubscribe(connection: Connection, operation: Operation, items: List<DATA>) {
    if (mapSubscribeMerge != null) {
      val mergeGroups = if (mergeGroupSize <= 1) {
        listOf(items)
      } else {
        items.chunked(mergeGroupSize)
      }
      val msgItems = mergeGroups.map { mapSubscribeMerge.invoke(operation, it) }

      msgItems.forEach {
        if (it != null) {
          connection.send(it, onError)
        }
      }
    } else {
      items.forEach {
        val msg = mapSubscribe(operation, it)

        if (msg != null) {
          connection.send(msg, onError)
        }
      }
    }
  }

  private fun sendSubscribeWithExistConnection(operation: Operation, items: List<DATA>): Boolean {
    val oldConnection = bindConnection

    if (oldConnection != null) {
      if (!oldConnection.isActive) {
        bindConnection = null
        oldConnection.removeMessageHandler(handler)
      } else {
        sendSubscribe(oldConnection, operation, items)
      }
    }
    return bindConnection != null
  }

  private fun sendMessageWithExistConnection(message: MSG): Boolean {
    val oldConnection = bindConnection

    if (oldConnection != null) {
      if (!oldConnection.isActive) {
        bindConnection = null
        oldConnection.removeMessageHandler(handler)
      } else if (message != null) {
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

  /**
   * Operation
   */
  enum class Operation {
    /**
     * Subscribe
     */
    SUBSCRIBE,

    /**
     * Unsubscribe
     */
    UNSUBSCRIBE
  }
}