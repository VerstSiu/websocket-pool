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
package com.ijoic.data.source.pool.prepare

/**
 * Prepare manager
 *
 * @author verstsiu created at 2019-01-23 10:56
 */
internal interface PrepareManager {
  /**
   * Request connection size
   */
  val requestSize: Int

  /**
   * Request connections with [size]
   */
  fun requestConnections(size: Int)

  /**
   * Append connections with [size]
   */
  fun appendConnections(size: Int)

  /**
   * Notify prepare complete
   */
  fun notifyPrepareComplete()

  /**
   * Release manager
   */
  fun release()
}