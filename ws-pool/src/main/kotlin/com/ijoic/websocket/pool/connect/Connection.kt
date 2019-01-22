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

import com.ijoic.websocket.pool.handler.MessageHandler

/**
 * WebSocket connection
 *
 * @author verstsiu created at 2019-01-22 11:33
 */
internal interface Connection {

  /**
   * Prepare connection with [url], [onActive] callback and [onInactive] callback
   */
  fun prepare(url: String, onActive: () -> Unit, onInactive: () -> Unit)

  /**
   * Add [handler]
   */
  fun addMessageHandler(handler: MessageHandler)

  /**
   * Remove [handler]
   */
  fun removeMessageHandler(handler: MessageHandler)

  /**
   * Send [byteArray] contents
   */
  fun sendBytes(byteArray: ByteArray)

  /**
   * Send [text] contents
   */
  fun sendText(text: String)

  /**
   * Destroy connection
   */
  fun destroy()

}