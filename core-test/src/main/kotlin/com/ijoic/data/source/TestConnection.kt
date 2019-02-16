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
package com.ijoic.data.source

import com.ijoic.data.source.handler.MessageHandler
import org.mockito.Mockito.mock

/**
 * Test connection
 *
 * @author verstsiu created at 2019-02-16 18:06
 */
class TestConnection : Connection {

  /**
   * Mock connection
   */
  val mockConnection: Connection = mock(Connection::class.java)

  override val displayName: String = "test-connection"

  override var isActive: Boolean = false
    private set

  override fun prepare(listener: ConnectionListener?) {
    currListener = listener
    mockConnection.prepare(listener)
  }

  override fun release() {
    handlerItems = emptyList()
  }

  override fun send(message: Any) {
    mockConnection.send(message)
  }

  /* -- notify :begin -- */

  private var currListener: ConnectionListener? = null

  /**
   * Notify connection complete
   */
  fun notifyConnectionComplete() {
    currListener?.onConnectionComplete()
  }

  /**
   * Notify connection failure
   */
  fun notifyConnectionFailure(error: Throwable?) {
    currListener?.onConnectionFailure(error)
  }

  /**
   * Notify connection closed
   */
  fun notifyConnectionClosed(message: String?, error: Throwable?) {
    currListener?.onConnectionClosed(message, error)
  }

  /* -- notify :end -- */

  /* -- message handler :begin -- */

  private var handlerItems: List<MessageHandler> = emptyList()

  override fun addMessageHandler(handler: MessageHandler) {
    val oldHandlerItems = handlerItems

    if (!oldHandlerItems.contains(handler)) {
      handlerItems = oldHandlerItems
        .toMutableList()
        .apply { add(handler) }
    }
  }

  override fun removeMessageHandler(handler: MessageHandler) {
    val oldHandlerItems = handlerItems

    if (oldHandlerItems.contains(handler)) {
      handlerItems = oldHandlerItems
        .toMutableList()
        .apply { remove(handler) }
    }
  }

  /* -- message handler :end -- */
}