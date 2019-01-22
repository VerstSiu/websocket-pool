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
import okhttp3.*
import okio.ByteString
import org.apache.logging.log4j.LogManager

/**
 * WebSocket connection
 *
 * @author verstsiu created at 2019-01-22 11:33
 */
internal class Connection {

  private var prepareSocket: WebSocket? = null
  private var activeSocket: WebSocket? = null

  private val connectionId = ++childGeneration

  /**
   * Prepare connection with http [client] and [url]
   */
  fun prepare(client: OkHttpClient, url: String, onActive: () -> Unit, onInActive: () -> Unit) {
    logger.debug("[cid-$connectionId] connection begin, url: $url")
    val request = Request.Builder()
      .url(url)
      .build()

    client.newWebSocket(request, object: WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        logger.debug("[cid-$connectionId] connection complete")
        prepareSocket = null
        activeSocket = webSocket
        onActive()
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        logger.error("[cid-$connectionId] connection error", t)
        prepareSocket = null
        activeSocket = null
        onInActive()
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val bytesContent = bytes.toByteArray()

        for (handler in messageHandlers) {
          if (handler.dispatchMessage(bytesContent)) {
            break
          }
        }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        for (handler in messageHandlers) {
          if (handler.dispatchMessage(text)) {
            break
          }
        }
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (code == 1000) {
          logger.debug("[cid-$connectionId] connection closed: $reason")
        } else {
          logger.error("[cid-$connectionId] connection closed: $code - $reason")
        }
        prepareSocket = null
        activeSocket = null
        onInActive()
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        logger.debug("[cid-$connectionId] connection closing")
        webSocket.close(1000, "triggered by closing")
      }
    })
  }

  /* -- message handlers :begin -- */

  private var messageHandlers: Set<MessageHandler> = emptySet()

  /**
   * Add [handler]
   */
  fun addMessageHandler(handler: MessageHandler) {
    val oldHandlers = messageHandlers

    if (!oldHandlers.contains(handler)) {
      messageHandlers = oldHandlers
        .toMutableSet()
        .apply { add(handler) }
    }
  }

  /**
   * Remove [handler]
   */
  fun removeMessageHandler(handler: MessageHandler) {
    val oldHandlers = messageHandlers

    if (oldHandlers.contains(handler)) {
      messageHandlers = oldHandlers
        .toMutableSet()
        .apply { remove(handler) }
    }
  }

  /* -- message handlers :end -- */

  /* -- messages :begin -- */

  /**
   * Send [byteArray] contents
   */
  fun sendBytes(byteArray: ByteArray) {
    activeSocket?.send(ByteString.of(*byteArray))
  }

  /**
   * Send [text] contents
   */
  fun sendText(text: String) {
    activeSocket?.send(text)
  }

  /* -- messages :end -- */

  /**
   * Destroy connection
   */
  fun destroy() {
    val oldPrepareSocket = prepareSocket
    val oldActiveSocket = activeSocket
    prepareSocket = null
    activeSocket = null
    messageHandlers = emptySet()

    oldPrepareSocket?.cancel()
    oldActiveSocket?.close(1000, "client destroy connection")
  }

  companion object {
    private val logger = LogManager.getLogger(Connection::class.java)

    private var childGeneration = 0
  }
}