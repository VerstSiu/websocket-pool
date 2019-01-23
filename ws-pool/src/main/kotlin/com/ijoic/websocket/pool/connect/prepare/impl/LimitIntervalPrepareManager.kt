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
 * Limit interval prepare manager
 *
 * @author verstsiu created at 2019-01-23 10:58
 */
internal class LimitIntervalPrepareManager(
  private val interval: Long,
  private val onPrepare: () -> Unit,
  private val getCurrTime: () -> Long,
  private val scheduleDelay: (Long, () -> Unit) -> Unit): BasePrepareManager() {

  private var lastPrepareTime = 0L

  private var prepareBusy = false
  private var waitBusy = false
  private var waitSize = 0

  override fun onRequestConnections(size: Int) {
    if (prepareBusy) {
      val oldRequestSize = waitSize + 1

      if (oldRequestSize >= size) {
        return
      }
      waitSize += size - oldRequestSize

    } else {
      if (waitSize < size) {
        waitSize = size
      }
      if (!waitBusy) {
        val currTime = getCurrTime()

        if (lastPrepareTime <= 0 || currTime - lastPrepareTime > interval) {
          doConnectionPrepare()
        } else {
          waitBusy = true
          scheduleDelay(interval - (currTime - lastPrepareTime), this::checkoutConnectionPrepare)
        }
      }
    }
  }

  override fun onPrepareComplete() {
    if (waitBusy) {
      return
    }

    if (waitSize <= 0) {
      prepareBusy = false
      return
    }
    doConnectionPrepare()
  }

  private fun doConnectionPrepare() {
    prepareBusy = true
    lastPrepareTime = getCurrTime()
    --waitSize

    onPrepare()

    if (waitSize > 0 && interval > 0) {
      waitBusy = true
      scheduleDelay(interval, this::checkoutConnectionPrepare)
    }
  }

  private fun checkoutConnectionPrepare() {
    syncEdit(this::onCheckoutConnectionPrepare)
  }

  private fun onCheckoutConnectionPrepare() {
    waitBusy = false

    if (prepareBusy || waitSize <= 0) {
      return
    }
    doConnectionPrepare()
  }
}