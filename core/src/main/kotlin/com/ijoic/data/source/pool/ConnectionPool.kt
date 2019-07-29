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
  private var connectionId = 0
  private val editLock = Object()

  /**
   * Active connection size
   */
  val activeConnectionSize: Int
    get() = activeConnections.size

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
  fun requestConnections(size: Int) {
    syncEdit { onRequestConnections(size) }
  }

  private fun onRequestConnections(size: Int) {
    logger.trace("$this request connections: thread - ${Thread.currentThread()}, size - $size")

    if (retryBusy) {
      val oldRequestSize = metrics.requestSize + 1

      if (size > oldRequestSize) {
        metrics.requestSize = size - 1
      }
    } else {
      logger.trace("$this manager - $prepareManager, old request size - ${prepareManager.requestSize}")
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
    val connectionId = this.connectionId++
    val taskId = "${connection.displayName} - $connectionId"
    logger.trace("[$taskId] prepare connection")

    if (!activePrepared) {
      activePrepared = true
      stateListeners.forEach { it.onConnectionActivePrepare() }
    }
    stateListeners.forEach { it.onConnectionBegin(connectionId) }
    connection.prepare(object : ConnectionListener {
      override fun onConnectionComplete() {
        logger.trace("[$taskId] connection complete")

        if (prepareActiveId != activeId) {
          return
        }
        if (activeConnectionSize <= 0) {
          stateListeners.forEach { it.onConnectionActive() }
        }
        stateListeners.forEach { it.onConnectionSucceed(connectionId) }
        syncEdit { onChildConnectionActive(connection) }
      }

      override fun onConnectionFailure(error: Throwable?) {
        logger.error("[$taskId] connection failed", error)

        if (prepareActiveId != activeId) {
          logger.debug("active id not matched")
          return
        }
        if (activeConnectionSize == 1) {
          logger.debug("notify connection inactive")
          stateListeners.forEach { it.onConnectionInactive() }
        }
        logger.debug("notify connection failed")
        stateListeners.forEach { it.onConnectionFailed(connectionId, error) }
        syncEdit { onChildConnectionInactive(connection) }
      }

      override fun onConnectionClosed(message: String?, error: Throwable?) {
        logger.trace("[$taskId] connection closed - $message", error)

        if (prepareActiveId != activeId) {
          return
        }
        if (activeConnectionSize == 1) {
          stateListeners.forEach { it.onConnectionInactive() }
        }
        stateListeners.forEach { it.onConnectionFailed(connectionId, error) }
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
    logger.debug("release connection")
    connection.release()
    var isReturnRequired = false

    if (activeConnections.remove(connection)) {
      --metrics.activeSize
      ++metrics.requestSize
      isReturnRequired = true
    }
    if (prepareConnections.remove(connection)) {
      prepareManager.notifyPrepareComplete()
      isReturnRequired = true
    }

    if (isReturnRequired) {
      connectionFactory.returnObject(connection)
    }

    when {
      retryBusy -> {
        ++retryCount
        logger.debug("schedule retry connection")
        scheduleRetryConnection()
      }
      !activeConnections.isEmpty() -> {
        prepareManager.appendConnections(1)
      }
      prepareManager.requestSize <= 0 && metrics.requestSize > 0 -> {
        retryBusy = true
        logger.debug("schedule retry connection")
        scheduleRetryConnection()
      }
    }
    logger.debug("notify connection inactive")
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
    connectionListeners.forEach { it.onConnectionInactive(connection) }
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

  /* -- connection state listener :begin -- */

  private var stateListeners: List<ConnectionStateListener> = emptyList()
  private var stateListenersLock = Object()

  private var activePrepared = false

  /**
   * Add state [listener]
   */
  fun addStateListener(listener: ConnectionStateListener) {
    synchronized(stateListenersLock) {
      if (!stateListeners.contains(listener)) {
        stateListeners = stateListeners + listener
      }
      if (activePrepared && activeConnectionSize <= 0) {
        listener.onConnectionActivePrepare()
      }
    }
  }

  /**
   * Remove state [listener]
   */
  fun removeStateListener(listener: ConnectionStateListener) {
    synchronized(stateListenersLock) {
      if (stateListeners.contains(listener)) {
        stateListeners = stateListeners - listener
      }
    }
  }

  /**
   * Connection state listener
   */
  interface ConnectionStateListener {
    /**
     * Connection begin with [connectionId]
     */
    fun onConnectionBegin(connectionId: Int) {}

    /**
     * Connection succeed with [connectionId]
     */
    fun onConnectionSucceed(connectionId: Int) {}

    /**
     * Connection failed with [connectionId] and [error]
     */
    fun onConnectionFailed(connectionId: Int, error: Throwable? = null) {}

    /**
     * Connection active prepare
     */
    fun onConnectionActivePrepare() {}

    /**
     * Connection active
     */
    fun onConnectionActive() {}

    /**
     * Connection inactive
     */
    fun onConnectionInactive() {}
  }

  /* -- connection state listener :end -- */

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