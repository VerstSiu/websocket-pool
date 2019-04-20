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
package com.ijoic.data.source.http.okhttp

import com.ijoic.data.source.ConnectionListener
import com.ijoic.data.source.context.ExecutorContext
import com.ijoic.data.source.context.impl.DefaultExecutorContext
import com.ijoic.data.source.impl.BaseConnection
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Http connection
 *
 * @author verstsiu created at 2019-01-26 10:00
 */
class HttpConnection(
  private val api: String,
  private val buildUrl: (String, Any) -> String = { baseUrl, _ -> baseUrl },
  private val buildRequest: (String, Any) -> Request = { url, _ -> Request.Builder().url(url).build() },
  private val context: ExecutorContext = DefaultExecutorContext): BaseConnection(context) {

  private var stateListener: ConnectionListener? = null
  private var failMessage: Any? = null
  private var httpCall: Call? = null

  override val displayName: String = api

  override var isActive: Boolean = false
    private set

  override fun onRelease() {
    stateListener = null
    failMessage = null
    isActive = false
    httpCall?.checkAndCancel()
    httpCall = null
  }

  override fun prepare(listener: ConnectionListener?) {
    listener ?: return
    stateListener = listener
    val oldFailMessage = failMessage

    if (oldFailMessage != null) {
      send(oldFailMessage)
    } else {
      isActive = true
      listener.onConnectionComplete()
    }
  }

  override fun send(message: Any?) {
    message ?: return
    failMessage = null
    context.io { doSendMessage(message) }
  }

  private fun doSendMessage(message: Any) {
    var httpCall: Call? = null

    try {
      val request = buildRequest(buildUrl(api, message), message)

      httpCall = client.newCall(request)
      this.httpCall = httpCall

      val response = httpCall.execute()
      val responseText = response.body()?.string()
      this.httpCall = null

      if (responseText != null) {
        dispatchReceivedMessage(responseText)
      }

    } catch (t: Throwable) {
      if (this.httpCall == httpCall) {
        isActive = false
        failMessage = message
        stateListener?.onConnectionFailure(t)
      }
    }
  }

  companion object {
    private val client by lazy { OkHttpClient() }

    private fun Call.checkAndCancel() {
      if (this.isExecuted && !this.isCanceled) {
        this.cancel()
      }
    }
  }
}