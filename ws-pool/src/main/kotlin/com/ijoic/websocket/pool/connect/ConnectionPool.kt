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
package com.ijoic.websocket.pool.connect

/**
 * WebSocket connection pool
 *
 * @author verstiu created at 2019-01-22 11:34
 */
internal class ConnectionPool(
  private val url: String,
  private val createConnection: () -> Connection = { ConnectionImpl() }) {

  private val activeConnections = mutableListOf<Connection>()
  private val prepareConnections = mutableListOf<Connection>()

  /**
   * Request connections with [size]
   */
  fun requestConnections(size: Int) {
    val prepareSize = size - prepareConnections.size

    if (prepareSize <= 0) {
      return
    }
    repeat(prepareSize) {
      prepareConnection()
    }
  }

  private fun prepareConnection() {
    val connection = createConnection()

    connection.prepare(
      url,
      onActive = {
        prepareConnections.remove(connection)
        activeConnections.add(connection)
        notifyConnectionActive(connection)
      },
      onInactive = {
        activeConnections.remove(connection)
        prepareConnections.add(connection)
        notifyConnectionInactive(connection)
      }
    )
    prepareConnections.add(connection)
  }

  /**
   * Returns min load active connection or null
   */
  fun getMinLoadActiveConnection(): Connection? {
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
  }

  /* -- connection listeners :begin -- */

  private val connectionListeners = mutableSetOf<ConnectionChangedListener>()

  /**
   * Add connection change [listener]
   */
  fun addConnectionChangeListener(listener: ConnectionChangedListener) {
    connectionListeners.add(listener)
  }

  /**
   * Remove connection change [listener]
   */
  fun removeConnectionChangeListener(listener: ConnectionChangedListener) {
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
  interface ConnectionChangedListener {
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

}