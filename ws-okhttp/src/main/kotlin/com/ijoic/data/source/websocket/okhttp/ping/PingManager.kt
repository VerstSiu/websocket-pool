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
package com.ijoic.data.source.websocket.okhttp.ping

import com.ijoic.data.source.ConnectionListener
import com.ijoic.data.source.context.ExecutorContext
import com.ijoic.data.source.util.checkAndCancel
import com.ijoic.data.source.websocket.okhttp.options.WebSocketOptions
import java.time.Duration
import java.util.concurrent.Future

/**
 * Ping manager
 *
 * @author verstsiu created at 2019-01-23 20:40
 */
internal class PingManager(
  private val options: WebSocketOptions,
  private val context: ExecutorContext,
  private val onSendPingMessage: (String) -> Unit,
  private val onCloseConnection: () -> Unit): ConnectionListener {

  private val pingIntervalMs = options.pingInterval?.toVerifiedMsOrNull()
  private val pingLazyMs = options.pingAfterNoMessageReceived?.toVerifiedMsOrNull()

  private val receiveTimeoutMs = options.messageReceivedTimeout?.toVerifiedMsOrNull()
  private val pongTimeoutMs = options.pongReceivedTimeout?.toVerifiedMsOrNull()

  private var isConnectionActive = false
  private var lastReceivedTime = 0L

  private var pingTask: Future<*>? = null
  private var receiveTimeoutTask: Future<*>? = null
  private var pongTimeoutTask: Future<*>? = null
  private var lastPing = 0L

  override fun onConnectionComplete() {
    isConnectionActive = true
    lastReceivedTime = context.getCurrentTime()

    if (pingIntervalMs != null) {
      schedulePingInterval(0, pingIntervalMs)
    } else {
      schedulePingLazy()
    }
    scheduleCheckReceiveTimeout()
  }

  override fun onConnectionFailure(error: Throwable?) {
    resetReceivedStatus()
  }

  override fun onConnectionClosed(message: String?, error: Throwable?) {
    resetReceivedStatus()
  }

  /**
   * Receive message
   */
  fun onReceivedMessage(isPongMessage: Boolean = false) {
    if (!isConnectionActive) {
      return
    }
    val currTime = context.getCurrentTime()
    lastReceivedTime = currTime

    if (isPongMessage) {
      lastPing = 0L
      pongTimeoutTask?.checkAndCancel()
      pongTimeoutTask = null
    }
    if (pingIntervalMs == null) {
      schedulePingLazy()
    }
    scheduleCheckReceiveTimeout()
  }

  /**
   * Check pong [message]
   */
  fun checkPongMessage(message: String): Boolean {
    return options.pongMessage == message || options.isPongMessage?.invoke(message) == true
  }

  /**
   * Release manager
   */
  fun release() {
    resetReceivedStatus()
  }

  /* -- check ping :begin -- */

  private fun schedulePingInterval(delayMs: Long, periodMs: Long) {
    pingTask = context.scheduleAtFixRate(
      delayMs,
      periodMs,
      this::onPingInterval
    )
  }

  private fun onPingInterval() {
    if (!isConnectionActive) {
      return
    }
    generateAndSendPingMessage()
  }

  private fun schedulePingLazy() {
    if (pingLazyMs != null) {
      pingTask?.checkAndCancel()
      pingTask = context.scheduleDelay(
        pingLazyMs,
        this::onPingLazy
      )
    }
  }

  private fun onPingLazy() {
    if (!isConnectionActive) {
      return
    }
    val lazyMs = this.pingLazyMs ?: return
    val currTime = context.getCurrentTime()

    if (currTime - lastReceivedTime > lazyMs) {
      generateAndSendPingMessage()
    }
  }

  private fun generateAndSendPingMessage() {
    val message = options.pingMessage ?: options.genPingMessage?.invoke()

    if (message != null) {
      onSendPingMessage(message)
      scheduleCheckPongTimeout()

      if (lastPing == 0L) {
        lastPing = context.getCurrentTime()
      }
    }
  }

  /* -- check ping :end -- */

  /* -- check timeout :begin -- */

  private fun scheduleCheckReceiveTimeout() {
    if (receiveTimeoutMs != null) {
      receiveTimeoutTask?.checkAndCancel()
      receiveTimeoutTask = context.scheduleDelay(
        receiveTimeoutMs,
        this::onCheckReceiveTimeout
      )
    }
  }

  private fun onCheckReceiveTimeout() {
    if (!isConnectionActive) {
      return
    }
    val currTime = context.getCurrentTime()
    val checkMs = receiveTimeoutMs ?: return

    if (lastReceivedTime > 0 && currTime - lastReceivedTime >= checkMs) {
      resetReceivedStatus()
      onCloseConnection()
    }
  }

  private fun scheduleCheckPongTimeout() {
    if (pongTimeoutMs == null) {
      return
    }
    val lastPing = this.lastPing

    if (lastPing <= 0) {
      pongTimeoutTask?.checkAndCancel()
      pongTimeoutTask = context.scheduleDelay(
        pongTimeoutMs,
        this::onCheckPongTimeout
      )
    } else {
      val currTime = context.getCurrentTime()
      val leftTimeoutMs = pongTimeoutMs - (currTime - lastPing)

      if (leftTimeoutMs <= 0) {
        resetReceivedStatus()
        onCloseConnection()
      } else {
        pongTimeoutTask?.checkAndCancel()
        pongTimeoutTask = context.scheduleDelay(
          leftTimeoutMs,
          this::onCheckPongTimeout
        )
      }
    }
  }

  private fun onCheckPongTimeout() {
    if (!isConnectionActive) {
      return
    }
    val checkMs = pongTimeoutMs ?: return
    val currTime = context.getCurrentTime()
    val lastPing = this.lastPing

    if (lastPing > 0 && currTime - lastPing >= checkMs) {
      resetReceivedStatus()
      onCloseConnection()
    }
  }

  /* -- check timeout :end -- */

  private fun resetReceivedStatus() {
    isConnectionActive = false
    lastReceivedTime = 0L

    pingTask?.checkAndCancel()
    pingTask = null

    receiveTimeoutTask?.checkAndCancel()
    receiveTimeoutTask = null

    pongTimeoutTask?.checkAndCancel()
    pongTimeoutTask = null

    lastPing = 0L
  }

  private fun Duration.toVerifiedMsOrNull(): Long? {
    return this
      .takeIf { !it.isZero && !it.isNegative }
      ?.toMillis()
  }

}