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
package com.ijoic.websocket.pool.run

import com.ijoic.websocket.pool.channel.SimpleGroupChannel
import com.ijoic.websocket.pool.connect.pool.ConnectionPool
import com.ijoic.websocket.pool.handler.MessageHandler

fun main() {
  val pool = ConnectionPool("wss://echo.websocket.org")
  val handler = TestMessageHandler()
  val channel = SimpleGroupChannel(pool, handler)

  channel.add("Hello World!")

  try {
    System.`in`.read()

  } catch (t: Throwable) {
    t.printStackTrace()
  } finally {
    pool.destroy()
  }
}

private class TestMessageHandler: MessageHandler {
  override fun dispatchMessage(bytes: ByteArray): Boolean {
    println("receive bytes: ${bytes.size}")
    return true
  }

  override fun dispatchMessage(text: String): Boolean {
    println("receive message: $text")
    return true
  }
}