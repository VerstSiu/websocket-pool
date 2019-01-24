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
package sample.ijoic.websocket.pool

import com.ijoic.data.source.channel.impl.MessageChannel
import com.ijoic.data.source.context.impl.DefaultExecutorContext
import com.ijoic.data.source.handler.MessageHandler
import com.ijoic.data.source.pool.ConnectionPool
import com.ijoic.data.source.websocket.okhttp.WebSocketConnection
import com.ijoic.data.source.websocket.okhttp.options.WebSocketOptions

/**
 * Test sample execute entrance
 */
fun main() {
  val pool = ConnectionPool(
    createConnection = {
      WebSocketConnection(
        WebSocketOptions("wss://echo.websocket.org"),
        DefaultExecutorContext
      )
    },
    context = DefaultExecutorContext
  )
  val handler = TestMessageHandler()
  val channel = MessageChannel<String>(pool, handler)

  channel.send("Hello World!")

  try {
    System.`in`.read()

  } catch (t: Throwable) {
    t.printStackTrace()
  } finally {
    pool.release()
  }
}

private class TestMessageHandler: MessageHandler {
  override fun dispatchMessage(message: Any): Boolean {
    println("receive message: $message")
    return true
  }
}