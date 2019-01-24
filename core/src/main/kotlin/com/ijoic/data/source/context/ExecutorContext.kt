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
package com.ijoic.data.source.context

import java.util.concurrent.Future

/**
 * Executor Context
 *
 * @author verstsiu created at 2019-01-23 21:08
 */
interface ExecutorContext {
  /**
   * Returns current time in milliseconds
   */
  fun getCurrentTime(): Long

  /**
   * Schedule delay with [delayMs] and [runnable]
   */
  fun scheduleDelay(delayMs: Long, runnable: () -> Unit): Future<*>

  /**
   * Schedule at fix rate with [delayMs], [periodMs] and [runnable]
   */
  fun scheduleAtFixRate(delayMs: Long, periodMs: Long, runnable: () -> Unit): Future<*>

  /**
   * Execute [runnable] at different thread immediately
   */
  fun io(runnable: () -> Unit)
}