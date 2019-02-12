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
package com.ijoic.data.source.websocket.okhttp.options

import java.time.Duration

/**
 * WebSocket options
 *
 * @author verstsiu created at 2019-01-23 20:27
 */
data class WebSocketOptions(
  val url: String,
  val proxyHost: String? = null,
  val proxyPort: Int? = null,
  val pingMessage: String? = null,
  val pongMessage: String? = null,
  val genPingMessage: (() -> String)? = null,
  val isPongMessage: ((String) -> Boolean)? = null,
  val pingInterval: Duration? = null,
  val pingAfterNoMessageReceived: Duration? = null,
  val messageReceivedTimeout: Duration? = null,
  val pongReceivedTimeout: Duration? = null,
  val decodeBytes: ((ByteArray) -> String?)? = null
)