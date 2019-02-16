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
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

/**
 * Test executor context
 *
 * @author verstsiu created at 2019-02-16 16:55
 */
class TestExecutorContext: ExecutorContext {

  private val ioList = mutableListOf<() -> Unit>()
  private val scheduleList = mutableListOf<ScheduleItem>()

  private var lastCurrTime = 0L

  /**
   * Elapse curr time with [milliseconds]
   */
  fun elaspse(milliseconds: Long) {
    val currTime = lastCurrTime + milliseconds
    lastCurrTime += currTime

    popScheduleElapsed(currTime)
  }

  override fun getCurrentTime(): Long {
    return lastCurrTime
  }

  override fun scheduleDelay(delayMs: Long, runnable: () -> Unit): Future<*> {
    val task = FutureTask<Unit>(runnable)
    scheduleList.add(ScheduleItem(lastCurrTime + delayMs, 0L, runnable, task, repeat = false))
    return task
  }

  override fun scheduleAtFixRate(delayMs: Long, periodMs: Long, runnable: () -> Unit): Future<*> {
    val task = FutureTask<Unit>(runnable)
    scheduleList.add(ScheduleItem(lastCurrTime + delayMs, periodMs, runnable, task, repeat = true))
    return task
  }

  /**
   * Execute next schedule runnable
   */
  fun nextSchedule(): Boolean {
    if (scheduleList.isEmpty()) {
      return false
    }
    val scheduleItem = scheduleList.minBy { it.startMs }

    if (scheduleItem != null) {
      scheduleList.remove(scheduleItem)
      val task = scheduleItem.task

      if (task.isCancelled || task.isDone) {
        // do nothing
      } else {
        lastCurrTime = scheduleItem.startMs
        task.run()

        if (scheduleItem.repeat) {
          scheduleList.add(
            ScheduleItem(
              scheduleItem.startMs + scheduleItem.periodMs,
              scheduleItem.periodMs,
              scheduleItem.runnable,
              FutureTask(scheduleItem.runnable),
              repeat = true
            )
          )
        }
        return true
      }
    }
    return false
  }

  /**
   * Pop schedule elapsed with [currTime]
   */
  private fun popScheduleElapsed(currTime: Long) {
    scheduleList.sortBy { it.startMs }

    while (true) {
      if (scheduleList.isEmpty()) {
        return
      }
      val scheduleItem = scheduleList.first()

      if (scheduleItem.startMs > currTime) {
        break
      }
      scheduleList.remove(scheduleItem)
      val task = scheduleItem.task

      if (!task.isDone && !task.isCancelled) {
        task.run()

        if (scheduleItem.repeat) {
          scheduleList.add(
            ScheduleItem(
              scheduleItem.startMs + scheduleItem.periodMs,
              scheduleItem.periodMs,
              scheduleItem.runnable,
              FutureTask(scheduleItem.runnable),
              repeat = true
            )
          )
          scheduleList.sortBy { it.startMs }
        }
      }
    }
  }

  override fun io(runnable: () -> Unit) {
    ioList.add(runnable)
  }

  /**
   * Execute next io runnable
   */
  fun nextIO(): Boolean {
    if (!ioList.isEmpty()) {
      ioList.removeAt(0).invoke()
      return true
    }
    return false
  }

  /**
   * Schedule item
   */
  private data class ScheduleItem(
    val startMs: Long,
    val periodMs: Long,
    val runnable: () -> Unit,
    val task: FutureTask<Unit>,
    val repeat: Boolean
  )
}