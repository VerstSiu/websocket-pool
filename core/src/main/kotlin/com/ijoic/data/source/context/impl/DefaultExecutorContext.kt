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
package com.ijoic.data.source.context.impl

import com.ijoic.data.source.context.ExecutorContext
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Default executor context
 *
 * @author verstsiu created at 2019-01-23 21:14
 */
object DefaultExecutorContext: ExecutorContext {

  private val scheduler by lazy { Executors.newScheduledThreadPool(10) }
  private val ioExecutor by lazy { Executors.newCachedThreadPool() }

  override fun getCurrentTime(): Long {
    return System.currentTimeMillis()
  }

  override fun scheduleDelay(delayMs: Long, runnable: () -> Unit): Future<*> {
    return scheduler.schedule(
      runnable,
      delayMs,
      TimeUnit.MILLISECONDS
    )
  }

  override fun scheduleAtFixRate(delayMs: Long, periodMs: Long, runnable: () -> Unit): Future<*> {
    return scheduler.scheduleAtFixedRate(
      runnable,
      delayMs,
      periodMs,
      TimeUnit.MILLISECONDS
    )
  }

  override fun io(runnable: () -> Unit) {
    ioExecutor.submit(runnable)
  }

}