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
package com.ijoic.data.source.pool

import com.ijoic.data.source.Connection
import com.ijoic.data.source.ConnectionListener
import com.ijoic.data.source.context.ExecutorContext
import com.ijoic.data.source.context.impl.DefaultExecutorContext
import com.ijoic.data.source.pool.prepare.PrepareManager
import com.ijoic.data.source.pool.prepare.impl.LimitIntervalPrepareManager
import com.ijoic.data.source.pool.prepare.impl.LimitSizePrepareManager
import com.ijoic.data.source.util.checkAndCancel
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.logging.log4j.LogManager
import java.util.concurrent.Future

/**
 * WebSocket connection pool
 *
 * @author verstiu created at 2019-01-22 11:34
 */
class ConnectionPool(
  config: PoolConfig? = null,
  private val context: ExecutorContext = DefaultExecutorContext,
  createConnection: () -> Connection) {

  private val activeConnections = mutableListOf<Connection>()
  private val prepareConnections = mutableListOf<Connection>()
  private val connectionFactory = GenericObjectPool(ConnectionFactory(createConnection))

  private val config = PoolConfig.verify(config)

  private val prepareManager = this.config.toPrepareManager()
  private val metrics = ConnectionMetrics()

  private var retryCount = 0
  private var retryBusy = false
  private var retryTask: Future<*>? = null

  private var activeId = 0
  private val editLock = Object()

  /**
   * Returns active connections with [size]
   */
  internal fun getActiveConnections(size: Int): List<Connection> {
    if (size <= 0) {
      return emptyList()
    }
    val connections = activeConnections.toList()

    return if (connections.size > size) {
      connections.subList(0, size)
    } else {
      connections
    }
  }

  /**
   * Request connections with [size]
   */
  internal fun requestConnections(size: Int) {
    syncEdit { onRequestConnections(size) }
  }

  private fun onRequestConnections(size: Int) {
    if (retryBusy) {
      val oldRequestSize = metrics.requestSize + 1

      if (size > oldRequestSize) {
        metrics.requestSize = size - 1
      }
    } else {
      prepareManager.requestConnections(size)
      metrics.requestSize = prepareManager.requestSize
    }
  }

  private fun prepareConnection() {
    syncEdit(this::onPrepareConnection)
  }

  private fun onPrepareConnection() {
    val connection = connectionFactory.borrowObject()
    val prepareActiveId = this.activeId

    connection.prepare(object : ConnectionListener {
      override fun onConnectionComplete() {
        if (prepareActiveId != activeId) {
          return
        }
        syncEdit { onChildConnectionActive(connection) }
      }

      override fun onConnectionFailure(error: Throwable?) {
        if (prepareActiveId != activeId) {
          return
        }
        syncEdit { onChildConnectionInactive(connection) }
      }

      override fun onConnectionClosed(message: String?, error: Throwable?) {
        if (prepareActiveId != activeId) {
          return
        }
        syncEdit { onChildConnectionInactive(connection) }
      }
    })
    prepareConnections.add(connection)
  }

  private fun onChildConnectionActive(connection: Connection) {
    if (prepareConnections.remove(connection)) {
      --metrics.requestSize
      ++metrics.activeSize
      prepareManager.notifyPrepareComplete()
    }
    activeConnections.add(connection)

    if (retryBusy) {
      retryBusy = false
      retryTask?.checkAndCancel()
      retryTask = null
      retryCount = 0
      prepareManager.requestConnections(metrics.requestSize)
    }
    notifyConnectionActive(connection)
  }

  private fun onChildConnectionInactive(connection: Connection) {
    connection.release()

    if (activeConnections.remove(connection)) {
      --metrics.activeSize
      ++metrics.requestSize
      connectionFactory.returnObject(connection)
    }
    if (prepareConnections.remove(connection)) {
      prepareManager.notifyPrepareComplete()
      connectionFactory.returnObject(connection)
    }

    when {
      retryBusy -> {
        ++retryCount
        scheduleRetryConnection()
      }
      !activeConnections.isEmpty() -> {
        prepareManager.appendConnections(1)
      }
      prepareManager.requestSize <= 0 && metrics.requestSize > 0 -> {
        retryBusy = true
        scheduleRetryConnection()
      }
    }
    notifyConnectionInactive(connection)
  }

  private fun scheduleRetryConnection() {
    val retryIntervals = config.retryIntervals
    val delayMs = retryIntervals[retryCount % retryIntervals.size].toMillis()

    if (delayMs <= 0) {
      onRetryConnection()
    } else {
      logger.trace("schedule retry $retryCount after $delayMs ms")
      retryTask = context.scheduleDelay(delayMs) {
        syncEdit { onRetryConnection() }
      }
    }
  }

  private fun onRetryConnection() {
    logger.debug("retry connection $retryCount")
    prepareManager.requestConnections(1)
  }

  private fun syncEdit(func: () -> Unit) {
    synchronized(editLock, func)
  }

  /**
   * Release pooled connections
   */
  fun release() {
    syncEdit {
      val oldConnections = mutableListOf<Connection>().apply {
        addAll(activeConnections)
        addAll(prepareConnections)
      }
      oldConnections.forEach { it.release() }

      prepareConnections.clear()
      activeConnections.clear()
      prepareManager.release()
      metrics.reset()
      retryCount = 0
      retryBusy = false
      retryTask?.checkAndCancel()
      retryTask = null
      ++activeId
    }
  }

  /* -- connection listeners :begin -- */

  private val connectionListeners = mutableSetOf<ConnectionChangedListener>()

  /**
   * Add connection change [listener]
   */
  internal fun addConnectionChangeListener(listener: ConnectionChangedListener) {
    connectionListeners.add(listener)
  }

  /**
   * Remove connection change [listener]
   */
  internal fun removeConnectionChangeListener(listener: ConnectionChangedListener) {
    connectionListeners.remove(listener)
  }

  private fun notifyConnectionActive(connection: Connection) {
    connectionListeners.forEach { it.onConnectionActive(connection) }
  }

  private fun notifyConnectionInactive(connection: Connection) {
    connectionListeners.forEach { it.onConnectionActive(connection) }
  }

  /**
   * Connection changed listener
   */
  internal interface ConnectionChangedListener {
    /**
     * [connection] active
     */
    fun onConnectionActive(connection: Connection) {}

    /**
     * [connection] inactive
     */
    fun onConnectionInactive(connection: Connection) {}
  }

  /* -- connection listeners :end -- */

  private fun PoolConfig.toPrepareManager(): PrepareManager {
    val intervalMs = this.limitPrepareInterval
      ?.toMillis()
      ?.takeIf { it > 0 }

    return if (intervalMs != null) {
      LimitIntervalPrepareManager(
        intervalMs,
        this@ConnectionPool::prepareConnection,
        context
      )
    } else {
      LimitSizePrepareManager(
        this.limitPrepareSize,
        this@ConnectionPool::prepareConnection
      )
    }
  }

  /**
   * Connection metrics
   */
  private data class ConnectionMetrics(
    var activeSize: Int = 0,
    var requestSize: Int = 0) {

    fun reset() {
      activeSize = 0
      requestSize = 0
    }
  }

  companion object {
    private val logger = LogManager.getLogger(ConnectionPool::class.java)
  }
}