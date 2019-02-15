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
package com.ijoic.data.source.pool.prepare.impl

import com.ijoic.data.source.context.ExecutorContext
import com.ijoic.data.source.util.checkAndCancel
import java.util.concurrent.Future

/**
 * Limit interval prepare manager
 *
 * @author verstsiu created at 2019-01-23 10:58
 */
internal class LimitIntervalPrepareManager(
  private val interval: Long,
  private val onPrepare: () -> Unit,
  private val context: ExecutorContext): BasePrepareManager() {

  private var lastPrepareTime = 0L

  private var prepareSize = 0
  private var waitSize = 0
  private var waitBusy = false
  private var waitTask: Future<*>? = null

  override val requestSize: Int
    get() = prepareSize + waitSize

  override fun onRequestConnections(appendSize: Int) {
    waitSize += appendSize

    if (!waitBusy) {
      val currTime = context.getCurrentTime()

      if (lastPrepareTime <= 0 || currTime - lastPrepareTime > interval) {
        println("$this do prepare: last prepare time - $lastPrepareTime, curr time - $currTime, interval - $interval")
        doConnectionPrepare()
      } else {
        waitBusy = true
        waitTask = context.scheduleDelay(interval - (currTime - lastPrepareTime), this::checkoutConnectionPrepare)
      }
    }
  }

  override fun onPrepareComplete() {
    if (waitBusy) {
      return
    }
    --prepareSize

    if (waitSize <= 0) {
      return
    }
    doConnectionPrepare()
  }

  override fun release() {
    lastPrepareTime = 0L
    prepareSize = 0
    waitSize = 0
    waitBusy = false
    waitTask?.checkAndCancel()
    waitTask = null
  }

  private fun doConnectionPrepare() {
    lastPrepareTime = context.getCurrentTime()
    ++prepareSize
    --waitSize

    onPrepare()

    if (waitSize > 0 && interval > 0) {
      waitBusy = true
      waitTask = context.scheduleDelay(interval, this::checkoutConnectionPrepare)
    }
  }

  private fun checkoutConnectionPrepare() {
    syncEdit(this::onCheckoutConnectionPrepare)
  }

  private fun onCheckoutConnectionPrepare() {
    waitBusy = false
    waitTask?.checkAndCancel()
    waitTask = null

    if (waitSize <= 0) {
      return
    }
    doConnectionPrepare()
  }
}