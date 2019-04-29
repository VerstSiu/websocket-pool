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
import com.ijoic.data.source.context.ExecutorContext
import com.ijoic.data.source.context.impl.DefaultExecutorContext
import com.ijoic.data.source.handler.MessageHandler
import com.ijoic.data.source.pool.ConnectionPool
import com.ijoic.data.source.send
import com.ijoic.data.source.util.BatchManager
import java.time.Duration

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
  private val mergeGroupSize: Int = -1,
  mergeDuration: Duration = Duration.ofMillis(20),
  context: ExecutorContext = DefaultExecutorContext): BaseChannel() {

  private val activeMessages = mutableListOf<DATA>()
  private var bindConnection: Connection? = null

  private val editLock = Object()
  private val msgBatchManager = BatchManager(mergeDuration, context, onDispatch = this::dispatchSubscribe)

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
        msgBatchManager.release()
      }
    }
  }

  /**
   * Add [subscribe] data
   */
  fun add(subscribe: DATA, sendRepeat: Boolean = false) {
    val connection = checkAndRequestConnection()

    if (connection == null) {
      activeMessages.add(subscribe)
    } else {
      msgBatchManager.accept(SubscribeInfo(Operation.SUBSCRIBE, subscribe, sendRepeat))
    }
  }

  /**
   * Add [subscribe] data all
   */
  fun addAll(subscribe: List<DATA>, sendRepeat: Boolean = false) {
    val connection = checkAndRequestConnection()

    if (connection == null) {
      activeMessages.addAll(subscribe)
    } else {
      msgBatchManager.acceptAll(subscribe.map { SubscribeInfo(Operation.SUBSCRIBE, it, sendRepeat) })
    }
  }

  /**
   * Remove [subscribe] data
   */
  fun remove(subscribe: DATA) {
    val connection = checkAndRequestConnection()

    if (connection == null) {
      activeMessages.remove(subscribe)
    } else {
      msgBatchManager.accept(SubscribeInfo(Operation.UNSUBSCRIBE, subscribe, false))
    }
  }

  /**
   * Remove [subscribe] data all
   */
  fun removeAll(subscribe: List<DATA>) {
    val connection = checkAndRequestConnection()

    if (connection == null) {
      activeMessages.removeAll(subscribe)
    } else {
      msgBatchManager.acceptAll(subscribe.map { SubscribeInfo(Operation.UNSUBSCRIBE, it, false) })
    }
  }

  private fun dispatchSubscribe(items: List<SubscribeInfo<DATA>>) {
    val subscribeItems = mutableSetOf<DATA>()
    val unsubscribeItems = mutableSetOf<DATA>()

    synchronized(editLock) {
      items.forEach {
        val data = it.item

        if (data != null) {
          when (it.operation) {
            Operation.SUBSCRIBE -> {
              if (!activeMessages.contains(data)) {
                activeMessages.add(data)
                subscribeItems.add(data)
                unsubscribeItems.remove(data)
              }
            }
            Operation.UNSUBSCRIBE -> {
              if (activeMessages.contains(data)) {
                activeMessages.remove(data)
                subscribeItems.remove(data)
                unsubscribeItems.add(data)
              }
            }
          }
        }
      }

      val sendRepeatItems = items
        .filter { it.operation == Operation.SUBSCRIBE && it.sendRepeat }
        .map { it.item }
        .toMutableSet()
        .apply { removeAll(subscribeItems) }

      // subscribe/unsubscribe items
      if (sendSubscribeWithExistConnection(Operation.UNSUBSCRIBE, unsubscribeItems.toList())) {
        sendSubscribeWithExistConnection(Operation.SUBSCRIBE, sendRepeatItems.toList())
        sendSubscribeWithExistConnection(Operation.SUBSCRIBE, subscribeItems.toList())
        return
      }
      val connection = checkAndRequestConnection()

      if (connection != null) {
        bindConnection = connection
        connection.addMessageHandler(handler)
        sendSubscribe(connection, Operation.SUBSCRIBE, activeMessages)
      }
    }
  }

  private fun checkAndRequestConnection(): Connection? {
    val oldConnection = bindConnection

    if (oldConnection != null) {
      return oldConnection
    }
    val connection = pool.getActiveConnections(1).firstOrNull()
    pool.addConnectionChangeListener(connectionListener)

    if (connection == null) {
      pool.requestConnections(1)
    }
    return connection
  }

  private fun sendSubscribe(connection: Connection, operation: Operation, items: List<DATA>) {
    if (mapSubscribeMerge != null) {
      val mergeGroups = if (mergeGroupSize <= 1) {
        listOf(items)
      } else {
        items.chunked(mergeGroupSize)
      }
      val msgItems = mergeGroups
        .filter { !it.isEmpty() }
        .map { mapSubscribeMerge.invoke(operation, it) }

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
      return true
    }
    return false
  }

  override fun release() {
    synchronized(editLock) {
      activeMessages.clear()
      pool.removeConnectionChangeListener(connectionListener)
      bindConnection?.removeMessageHandler(handler)
      bindConnection = null
      msgBatchManager.release()
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

  /**
   * Subscribe info
   */
  private data class SubscribeInfo<T>(
    val operation: Operation,
    val item: T,
    val sendRepeat: Boolean
  )
}