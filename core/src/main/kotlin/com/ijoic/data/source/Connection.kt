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
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

/**
 * Connection
 *
 * @author verstsiu created at 2019-01-23 18:48
 */
interface Connection {
  /**
   * Display name
   */
  val displayName: String

  /**
   * Active status
   */
  val isActive: Boolean

  /**
   * Prepare connection with [listener]
   *
   * Note: Accept nullable [listener] for mokito test
   */
  fun prepare(listener: ConnectionListener?)

  /**
   * Release connection
   */
  fun release()

  /**
   * Send [message]
   *
   * Throws [IllegalStateException] while connection is not active
   * Throws [IllegalArgumentException] while message type unrecognized
   */
  @Throws(
    IllegalStateException::class,
    IllegalArgumentException::class
  )
  fun send(message: Any)

  /**
   * Add message [handler]
   */
  fun addMessageHandler(handler: MessageHandler)

  /**
   * Remove message [handler]
   */
  fun removeMessageHandler(handler: MessageHandler)
}