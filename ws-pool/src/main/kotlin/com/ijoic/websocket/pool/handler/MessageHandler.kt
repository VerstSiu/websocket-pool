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
package com.ijoic.websocket.pool.handler

/**
 * Message handler
 *
 * @author verstsiu created at 2019-01-22 16:09
 */
interface MessageHandler {
  /**
   * Dispatch [bytes] message
   */
  fun dispatchMessage(bytes: ByteArray): Boolean = false

  /**
   * Dispatch [text] message
   */
  fun dispatchMessage(text: String): Boolean = false
}