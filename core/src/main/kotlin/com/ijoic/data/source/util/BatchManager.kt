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
package com.ijoic.data.source.util

import com.ijoic.data.source.context.ExecutorContext
import com.ijoic.data.source.context.impl.DefaultExecutorContext
import java.time.Duration
import java.util.concurrent.Future

/**
 * Batch manager
 *
 * @author verstsiu created at 2019-04-22 15:14
 */
internal class BatchManager<T>(
  interval: Duration,
  private val context: ExecutorContext = DefaultExecutorContext,
  private val onDispatch: (List<T>) -> Unit) {

  private val intervalMs = interval.toMillis()
  private var waitTask: Future<*>? = null

  /**
   * Accept [data]
   */
  fun accept(data: T) {
    if (!addItem(data)) {
      return
    }
    scheduleNextExecuteTask()
  }

  /**
   * Accept al [data] items
   */
  fun acceptAll(data: Collection<T>) {
    if (!addItems(data)) {
      return
    }
    scheduleNextExecuteTask()
  }

  private fun scheduleNextExecuteTask() {
    val oldTask = waitTask

    if (oldTask == null || oldTask.isCancelled || oldTask.isDone) {
      waitTask = context.scheduleDelay(intervalMs) {
        val items = popupAllItems()
        onDispatch(items)
      }
    }
  }

  /**
   * Release manager resources
   */
  fun release() {
    waitTask?.checkAndCancel()
    waitTask = null
    items.clear()
  }

  /* -- items manage :begin  -- */

  private val items = mutableListOf<T>()
  private val editLock = Object()

  private fun addItem(data: T): Boolean {
    synchronized(editLock) {
      if (!items.contains(data)) {
        items.add(data)
        return true
      }
      return false
    }
  }

  private fun addItems(data: Collection<T>): Boolean {
    synchronized(editLock) {
      var addSize = 0

      data.forEach {
        if (!items.contains(it)) {
          ++addSize
          items.add(it)
        }
      }
      return addSize > 0
    }
  }

  private fun removeItem(data: T): Boolean {
    synchronized(editLock) {
      if (items.contains(data)) {
        items.remove(data)
        return true
      }
      return false
    }
  }

  private fun popupAllItems(): List<T> {
    synchronized(editLock) {
      val popupItems = items.toList()
      items.clear()
      return popupItems
    }
  }

  /* -- items manage :end  -- */
}