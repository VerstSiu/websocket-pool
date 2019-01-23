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
package com.ijoic.websocket.pool.connect.pool

import com.ijoic.websocket.pool.connect.Connection
import com.ijoic.websocket.pool.connect.ConnectionImpl
import com.ijoic.websocket.pool.connect.prepare.PrepareManager
import com.ijoic.websocket.pool.connect.prepare.impl.LimitIntervalPrepareManager
import com.ijoic.websocket.pool.connect.prepare.impl.LimitSizePrepareManager
import com.ijoic.websocket.pool.util.AppExecutors
import java.util.concurrent.TimeUnit

/**
 * WebSocket connection pool
 *
 * @author verstiu created at 2019-01-22 11:34
 */
class ConnectionPool(
  private val url: String,
  config: PoolConfig? = null,
  createConnection: (() -> Connection)? = null,
  scheduleDelay: ((Long, () -> Unit) -> Unit)? = null) {

  private val activeConnections = mutableListOf<Connection>()
  private val prepareConnections = mutableListOf<Connection>()

  private val config = PoolConfig.verify(config)
  private val createConnection = createConnection ?: { ConnectionImpl() }
  private val scheduleDelay = scheduleDelay ?: { delayMs, r ->
    AppExecutors.scheduler.schedule(r, delayMs, TimeUnit.MILLISECONDS)
  }

  private val prepareManager = this.config.toPrepareManager()

  /**
   * Request connections with [size]
   */
  internal fun requestConnections(size: Int) {
    prepareManager.requestConnections(size)
  }

  private fun prepareConnection() {
    val connection = createConnection()

    connection.prepare(
      url,
      onActive = {
        if (prepareConnections.remove(connection)) {
          prepareManager.notifyPrepareComplete()
        }
        activeConnections.add(connection)
        notifyConnectionActive(connection)
      },
      onInactive = {
        if (activeConnections.remove(connection)) {
          prepareManager.notifyPrepareComplete()
        }
        prepareConnections.add(connection)
        notifyConnectionInactive(connection)
      }
    )
    prepareConnections.add(connection)
  }

  /**
   * Returns min load active connection or null
   */
  internal fun getMinLoadActiveConnection(): Connection? {
    // TODO
    return activeConnections.firstOrNull()
  }

  /**
   * Destroy pooled connections
   */
  fun destroy() {
    prepareConnections.forEach { it.destroy() }
    prepareConnections.clear()
    activeConnections.forEach { it.destroy() }
    activeConnections.clear()
    prepareManager.destroy()
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
        { System.currentTimeMillis() },
        scheduleDelay
      )
    } else {
      LimitSizePrepareManager(
        this.limitPrepareSize,
        this@ConnectionPool::prepareConnection
      )
    }
  }

}