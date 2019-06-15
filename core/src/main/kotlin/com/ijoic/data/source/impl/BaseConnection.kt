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
package com.ijoic.data.source.impl

import com.ijoic.data.source.Connection
import com.ijoic.data.source.context.ExecutorContext
import com.ijoic.data.source.handler.MessageHandler
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.SimpleMessage
import java.util.concurrent.Executors

/**
 * Base connection
 *
 * @author verstsiu created at 2019-01-24 15:59
 */
abstract class BaseConnection(private val context: ExecutorContext): Connection {

  private var handlerItems: List<MessageHandler> = emptyList()
  private val executors = Executors.newFixedThreadPool(1)

  override fun addMessageHandler(handler: MessageHandler) {
    val oldHandlerItems = handlerItems

    if (!oldHandlerItems.contains(handler)) {
      handler.host = this
      handlerItems = oldHandlerItems
        .toMutableList()
        .apply { add(handler) }
    }
  }

  override fun removeMessageHandler(handler: MessageHandler) {
    val oldHandlerItems = handlerItems

    if (oldHandlerItems.contains(handler)) {
      handler.host = null
      handlerItems = oldHandlerItems
        .toMutableList()
        .apply { remove(handler) }
    }
  }

  /**
   * Dispatch received [message]
   */
  protected fun dispatchReceivedMessage(message: Any) {
    val receiveTime = context.getCurrentTime()

    executors.run {
      val oldHandlerItems = handlerItems

      if (oldHandlerItems.isEmpty()) {
        connectionLogger.debug {
          SimpleMessage("no handler found to dispatch message: $message")
        }
      } else {
        var msgDispatched = false

        for (handler in oldHandlerItems) {
          if (handler.dispatchMessage(receiveTime, message)) {
            msgDispatched = true
            break
          }
        }
        if (!msgDispatched) {
          connectionLogger.debug {
            SimpleMessage("message dispatch failed with all handlers (${oldHandlerItems.size}): $message")
          }
        }
      }
    }
  }

  override fun release() {
    handlerItems = emptyList()
    onRelease()
  }

  protected abstract fun onRelease()

  companion object {
    private val connectionLogger = LogManager.getLogger(Connection::class.java)
  }
}