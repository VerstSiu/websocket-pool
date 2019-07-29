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
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Http connection
 *
 * @author verstsiu created at 2019-01-26 10:00
 */
class HttpConnection(
  private val api: String,
  private val options: Options? = null,
  private val buildRequest: () -> Request = { Request.Builder().url(api).build() },
  private val context: ExecutorContext = DefaultExecutorContext): BaseConnection(context) {

  private var stateListener: ConnectionListener? = null
  private var httpCall: Call? = null
  private var cacheResponse: String? = null

  private val client  by lazy { getClientInstance(options) }

  override val displayName: String = api

  override var isActive: Boolean = false
    private set

  override fun onRelease() {
    stateListener = null
    isActive = false
    httpCall?.checkAndCancel()
    httpCall = null
  }

  override fun prepare(listener: ConnectionListener) {
    stateListener = listener
    context.io { doPrepareRequest(listener) }
  }

  override fun send(message: Any) {
    context.io { doSendRequest() }
  }

  private fun doPrepareRequest(listener: ConnectionListener) {
    var httpCall: Call? = null

    try {
      val request = buildRequest()

      httpCall = client.newCall(request)
      this.httpCall = httpCall

      val response = httpCall.execute()
      val responseText = response.body()?.string()
      this.httpCall = null

      if (responseText != null) {
        cacheResponse = responseText
        isActive = true
        listener.onConnectionComplete()
      } else {
        isActive = false
        listener.onConnectionFailure(IOException("couldn't get response content"))
      }

    } catch (t: Throwable) {
      if (this.httpCall == httpCall) {
        isActive = false
        stateListener?.onConnectionFailure(t)
      }
    }
  }

  private fun doSendRequest() {
    val cacheResponse = this.cacheResponse
    this.cacheResponse = null

    if (cacheResponse != null) {
      dispatchReceivedMessage(cacheResponse)
      return
    }
    var httpCall: Call? = null

    try {
      val request = buildRequest()

      httpCall = client.newCall(request)
      this.httpCall = httpCall

      val response = httpCall.execute()
      val responseText = response.body()?.string()
      this.httpCall = null

      if (responseText != null) {
        dispatchReceivedMessage(responseText)
      } else {
        isActive = false
        stateListener?.onConnectionFailure(IOException("couldn't get response content"))
      }

    } catch (t: Throwable) {
      if (this.httpCall == httpCall) {
        isActive = false
        stateListener?.onConnectionFailure(t)
      }
    }
  }

  /**
   * Options
   */
  data class Options(
    val proxyHost: String? = null,
    val proxyPort: Int? = null
  )

  companion object {
    private val defaultOkHttpClient by lazy { OkHttpClient() }

    private fun getClientInstance(options: Options?): OkHttpClient {
      val proxyHost = options?.proxyHost
      val proxyPort = options?.proxyPort

      if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null) {
        return OkHttpClient.Builder()
          .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
          .build()
      }
      return defaultOkHttpClient
    }

    private fun Call.checkAndCancel() {
      if (this.isExecuted && !this.isCanceled) {
        this.cancel()
      }
    }
  }

}