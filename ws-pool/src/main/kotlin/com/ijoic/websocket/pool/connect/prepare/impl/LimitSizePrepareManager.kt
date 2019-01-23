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
package com.ijoic.websocket.pool.connect.prepare.impl

/**
 * Limit size prepare manager
 *
 * @author verstsiu created at 2019-01-23 11:50
 */
internal class LimitSizePrepareManager(
  private val size: Int,
  private val onPrepare: () -> Unit): BasePrepareManager() {

  private var prepareSize = 0
  private var waitSize = 0

  override val requestSize: Int
    get() = prepareSize + waitSize

  override fun onRequestConnections(appendSize: Int) {
    when {
      waitSize > 0 -> {
        waitSize += appendSize
      }
      this.size <= 0 -> {
        prepareSize += appendSize
        repeat(appendSize) { onPrepare() }
      }
      prepareSize < this.size -> {
        val appendPrepareSize = Math.min(appendSize, this.size - prepareSize)
        prepareSize += appendPrepareSize
        waitSize += appendSize - appendPrepareSize
        repeat(appendPrepareSize) { onPrepare() }
      }
      else -> {
        waitSize += appendSize
      }
    }
  }

  override fun onPrepareComplete() {
    if (waitSize <= 0) {
      --prepareSize
      return
    }
    --waitSize
    onPrepare()
  }

}