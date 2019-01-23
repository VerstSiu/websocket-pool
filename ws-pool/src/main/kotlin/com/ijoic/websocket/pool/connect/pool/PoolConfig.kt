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
package com.ijoic.websocket.pool.connect.pool

import java.time.Duration

/**
 * Pool config
 *
 * @author verstsiu created at 2019-01-22 20:49
 */
data class PoolConfig(
  val limitPrepareSize: Int = 0,
  val limitPrepareInterval: Duration? = null,
  val retryIntervals: List<Duration> = emptyList()) {

  companion object {

    private val defaultRetryIntervals = listOf<Duration>(
      Duration.ZERO,
      Duration.ofSeconds(1),
      Duration.ofSeconds(2),
      Duration.ofSeconds(4),
      Duration.ofSeconds(8),
      Duration.ofSeconds(16),
      Duration.ofSeconds(32),
      Duration.ofSeconds(64),
      Duration.ofSeconds(128),
      Duration.ofSeconds(128)
    )

    /**
     * Returns verified [src] pool config
     */
    internal fun verify(src: PoolConfig?): PoolConfig {
      val limitPrepareSize = src?.limitPrepareSize ?: 0
      val limitPrepareInterval = src?.limitPrepareInterval
        ?.takeIf { !it.isZero && !it.isNegative }

      val retryIntervals = src?.retryIntervals
        ?.filter { !it.isNegative }
        ?.takeIf { !it.isEmpty() } ?: defaultRetryIntervals

      return PoolConfig(
        limitPrepareSize,
        limitPrepareInterval,
        retryIntervals
      )
    }
  }
}