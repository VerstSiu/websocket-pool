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

import com.ijoic.websocket.pool.connect.prepare.PrepareManager

/**
 * Base prepare manager
 *
 * @author verstsiu created at 2019-01-23 12:30
 */
abstract class BasePrepareManager: PrepareManager {

  private val editLock = Object()
  private var prepareEnabled = true

  override fun requestConnections(size: Int) {
    if (size <= 0) {
      return
    }
    syncEdit {
      val oldRequestSize = requestSize
      val appendSize = size - oldRequestSize

      if (appendSize > 0) {
        onRequestConnections(appendSize)
      }
    }
  }

  override fun appendConnections(size: Int) {
    if (size <= 0) {
      return
    }
    syncEdit {
      onRequestConnections(size)
    }
  }

  override fun notifyPrepareComplete() {
    syncEdit(this::onPrepareComplete)
  }

  override fun destroy() {
    prepareEnabled = false
  }

  /**
   * Request connections with positive [appendSize]
   */
  protected abstract fun onRequestConnections(appendSize: Int)

  /**
   * Prepare complete
   */
  protected abstract fun onPrepareComplete()

  /**
   * Synchronize edit with [func]
   */
  protected fun syncEdit(func: () -> Unit) {
    if (!prepareEnabled) {
      return
    }
    synchronized(editLock, func)
  }
}