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
package com.ijoic.data.source.websocket.okhttp

import com.ijoic.data.source.ConnectionListener
import com.ijoic.data.source.context.ExecutorContext
import com.ijoic.data.source.context.impl.DefaultExecutorContext
import com.ijoic.data.source.impl.BaseConnection
import com.ijoic.data.source.websocket.okhttp.options.WebSocketOptions
import com.ijoic.data.source.websocket.okhttp.ping.PingManager
import okhttp3.*
import okio.ByteString
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * WebSocket connection
 *
 * @author verstsiu created at 2019-01-23 18:55
 */
class WebSocketConnection(
  private val options: WebSocketOptions,
  context: ExecutorContext = DefaultExecutorContext): BaseConnection(context) {

  constructor(url: String, context: ExecutorContext = DefaultExecutorContext): this(
    WebSocketOptions(url),
    context
  )

  private var prepareSocket: WebSocket? = null
  private var activeSocket: WebSocket? = null

  private val pingManager = PingManager(
    options,
    context,
    onSendPingMessage = { send(it) },
    onCloseConnection = { activeSocket?.close(1000, "message received timeout") }
  )

  private var activeId = 0

  override val displayName: String = options.url

  override var isActive: Boolean = false
    private set

  private var refActiveListener: WeakReference<ConnectionListener>? = null

  override fun prepare(listener: ConnectionListener?) {
    listener ?: return
    if (isActive || prepareSocket != null || activeSocket != null) {
      return
    }
    refActiveListener = WeakReference(listener)

    val prepareActiveId = this.activeId
    val request = Request.Builder()
      .url(options.url)
      .build()

    prepareSocket = getClientInstance(options).newWebSocket(request, object: WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        if (prepareActiveId != activeId) {
          return
        }
        prepareSocket = null
        activeSocket = webSocket
        isActive = true
        pingManager.onConnectionComplete()
        listener.onConnectionComplete()
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (prepareActiveId != activeId) {
          return
        }
        isActive = false
        pingManager.onConnectionFailure(t)
        listener.onConnectionFailure(t)
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, "server closed connection")
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (prepareActiveId != activeId) {
          return
        }
        isActive = false
        pingManager.onConnectionClosed(reason, null)
        listener.onConnectionClosed(reason, null)
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        if (prepareActiveId != activeId) {
          return
        }
        val isPongMessage = pingManager.checkPongMessage(text)
        pingManager.onReceivedMessage(isPongMessage)

        if (!isPongMessage) {
          dispatchReceivedMessage(text)
        }
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        if (prepareActiveId != activeId) {
          return
        }
        val msgContent = bytes.toByteArray()
        val decodeText = options.decodeBytes?.invoke(msgContent)

        if (decodeText != null) {
          onMessage(webSocket, decodeText)
          return
        }
        pingManager.onReceivedMessage(false)

        dispatchReceivedMessage(msgContent)
      }
    })
  }

  override fun send(message: Any?) {
    message ?: return
    val socket = activeSocket

    if (!isActive || socket == null) {
      throw IllegalStateException("connection not active")
    }
    when(message) {
      is ByteArray -> socket.send(ByteString.of(*message))
      is String -> socket.send(message)
      else -> throw IllegalArgumentException("invalid argument: $message")
    }
  }

  override fun restartPrepare() {
    val listener = refActiveListener?.get()

    if (!isActive || listener == null) {
      return
    }
    onRelease()
    listener.onConnectionClosed("connection refresh", null)
  }

  override fun onRelease() {
    isActive = false
    ++activeId
    prepareSocket?.close(1000, "client release")
    prepareSocket = null
    activeSocket?.close(1000, "client release")
    activeSocket = null
    pingManager.release()
  }

  companion object {
    private val defaultOkHttpClient by lazy { OkHttpClient() }

    private fun getClientInstance(options: WebSocketOptions): OkHttpClient {
      val proxyHost = options.proxyHost
      val proxyPort = options.proxyPort

      if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null) {
        return OkHttpClient.Builder()
          .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
          .build()
      }
      return defaultOkHttpClient
    }
  }
}