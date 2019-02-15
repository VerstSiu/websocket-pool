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

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.SimpleMessage

private val connectionLogger = LogManager.getLogger(Connection::class.java)

/**
 * Send [message] with [errorHandler]
 *
 * @author verstsiu created at 2019-01-24 16:19
 */
fun Connection.send(message: Any, errorHandler: (Throwable) -> Unit) {
  connectionLogger.debug { SimpleMessage("[$displayName] send message: $message") }

  try {
    send(message)
  } catch (t: Throwable) {
    errorHandler.invoke(t)
  }
}